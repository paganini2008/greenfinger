#!/usr/bin/env bash
#
# Greenfinger, in containers.
#
#     ./run-docker.sh                start what run.conf describes
#     ./run-docker.sh status         what is running
#     ./run-docker.sh stop           stop and remove them ('down' is the same thing)
#     ./run-docker.sh logs 2         follow node 2
#     ./run-docker.sh build          rebuild the images and stop
#     ./run-docker.sh help           this
#
# How many nodes, on which ports, with or without the front end container, how much memory: all
# of it in run.conf beside this script, which run-local.sh reads too. There are no options -- the
# shape of a run belongs in a file you can read back later rather than in a line somebody typed
# once. Anything already in the environment still wins, so `GF_NODES=3 ./run-docker.sh` is a
# one-off.
#
# A database or a search server this machine is already running is reached from inside a container
# as host.docker.internal, and the GF_* variables that name one are passed through:
#
#     GF_PROFILE=prod \
#     GF_DB_URL='jdbc:mysql://host.docker.internal:3306/greenfinger' GF_DB_USERNAME=... \
#     GF_INDEX_PROVIDER=elasticsearch GF_ES_URIS=http://host.docker.internal:9200 \
#     GF_VECTOR_STORE=qdrant GF_QDRANT_URL=http://host.docker.internal:6333 \
#     ./run-docker.sh -n 3
#
# Same shape as run-local.sh and for the same reason: a container is a node, not "the server".
# They find each other by name on a user-defined network, which is what makes the peer list a list
# of names rather than of addresses nobody knows in advance.
#
# Each node gets its own volume. A node keeps a complete copy of everything -- database, pages,
# dedup stores -- and replication keeps the copies the same; a container replaced without its
# volume comes back empty and has to be told the whole crawl again.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# An empty value in a settings file means "not set", and has to be made to mean it. Spring reads
# ${GF_CLUSTER_PORT:22000} as "22000 unless the variable exists" -- and an exported empty string
# exists, so the default never applies and the bind fails on an empty int. Dropping the empties
# here is what lets run.conf ship a key with no value beside its explanation.
drop_empty_gf() {
  local name
  for name in $(compgen -v | grep -E '^GF_' || true); do
    [[ -z "${!name}" ]] && unset "${name}"
  done
  return 0
}

# Settings, in one place and in one order.
#
#   run.conf   the run: how many nodes, which cluster, where the data and the logs go
#   .env       the machine: passwords, api keys, the addresses of databases and stores
#   the shell  whatever the caller already exported
#
# Later beats earlier, so .env can override run.conf and a one-off on the command line beats both:
#
#     GF_NODES=3 ./run-local.sh
#
# `set -a` because the names in these files are the same GF_* the yaml in config/ reads -- they
# have to be exported before the jvm starts, or ${GF_DB_URL} in application.yml resolves to
# nothing. The caller's values are captured with `export -p`, whose output is made to be read
# back, rather than an associative array: that is bash 4 and macOS ships 3.2.
load_settings() {
  local preset
  # `declare -x` rather than `export` is what `export -p` prints, and `declare` inside a function
  # makes a *local* -- so evaluating it here would set a variable that vanishes on return, and the
  # caller's one-off would be silently ignored. Rewritten to `export`, which is global wherever it
  # is run.
  preset="$(export -p | grep -E ' GF_[A-Za-z0-9_]+=' | sed 's/^declare -x /export /' || true)"
  set -a
  if [[ -f "${SCRIPT_DIR}/run.conf" ]]; then
    # shellcheck disable=SC1091
    source "${SCRIPT_DIR}/run.conf"
  fi
  if [[ -f "${ENV_FILE:-${SCRIPT_DIR}/.env}" ]]; then
    # shellcheck disable=SC1090
    source "${ENV_FILE:-${SCRIPT_DIR}/.env}"
  fi
  set +a
  eval "${preset}"
  drop_empty_gf
}
load_settings

SUBNET="${GF_SUBNET:-172.28.0.0/16}"
IP_PREFIX="${GF_IP_PREFIX:-172.28.0.}"
IP_START="${GF_IP_START:-10}"
PREFIX="${GF_CONTAINER_PREFIX:-greenfinger}"
# The image definitions, put here by packaging. The build context is deploy/ itself, because that
# is where the jar and the front end build are.
DOCKER_DIR="${SCRIPT_DIR}/docker"
# Its own cluster name and its own port, so a set of containers left running neither merges with
# nodes started by run-local.sh nor takes the port they elect on. See the note in run-local.sh.
CLUSTER_NAME="${GF_CLUSTER_NAME:-greenfinger-docker}"
CLUSTER_PORT="${GF_CLUSTER_PORT:-22020}"
NODES="${GF_NODES:-3}"
BASE_PORT="${GF_BASE_PORT:-50080}"
# empty means a named volume per node, which survives the container being replaced
DATA_ROOT="${GF_DATA_ROOT:-}"
COMMAND="start"
LOG_NODE=1
# The front end is its own container: a small Node server that serves the Angular build and
# forwards the api to the nodes. The nodes can each serve the app themselves -- that is what makes
# one process a complete installation -- but then a browser is talking to one node rather than to
# the cluster. This gives it one address. GF_WEB=0 in run.conf leaves it out.
# The docker network the nodes share. Its own network rather than the default bridge, because
# the nodes are given fixed addresses out of GF_SUBNET and announce those to each other.
NETWORK="${GF_NETWORK:-greenfinger}"

# Per container, and the same default as the other three launchers. A container with no limit can
# take the whole machine, and three of them on a laptop is how the middle one gets killed.
MEMORY="${GF_MEMORY:-1g}"

# The node image this builds and runs. Named here rather than only where it is used, because
# every use of it is under `set -u` and an unset name is not a default -- it is the script
# stopping on its first line of real work.
IMAGE="${GF_IMAGE:-greenfinger:local}"

WEB="${GF_WEB:-1}"
WEB_PORT="${GF_WEB_PORT:-9700}"
WEB_IMAGE="${GF_WEB_IMAGE:-greenfinger-web:local}"
WEB_NAME="${PREFIX}-web"

while [[ $# -gt 0 ]]; do
  case "$1" in
    # down is stop: docker compose's word for it, and the one people reach for
    start|stop|down|status|build) COMMAND="$1"; shift ;;
    logs) COMMAND="logs"; LOG_NODE="${2:-1}"; shift 2 || shift ;;
    help) sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "This script takes a verb and nothing else: start, stop (down), status, logs," >&2
      echo "build, help." >&2
      echo "Everything else is in run.conf beside it." >&2
      exit 1
      ;;
  esac
done

# docker reads .dockerignore only at the root of the build context, and the resources plugin that
# puts this directory together will not ship a name beginning with a dot. Packaging carries it as
# `dockerignore` and this puts it where docker will look. Without it a build uploads the data
# store and the logs before doing anything.
if [[ -f "${SCRIPT_DIR}/dockerignore" ]]; then
  cp "${SCRIPT_DIR}/dockerignore" "${SCRIPT_DIR}/.dockerignore"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is not on the path." >&2
  exit 1
fi

names() { docker ps -a --filter "name=^${PREFIX}-" --format '{{.Names}}' | sort; }

case "${COMMAND}" in
  status)
    docker ps -a --filter "name=^${PREFIX}-" \
      --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
    exit 0
    ;;
  logs)
    docker logs -f "${PREFIX}-${LOG_NODE}"
    exit 0
    ;;
  stop|down)
    # not mapfile: it is bash 4 and macOS ships 3.2, where this whole branch was a "command
    # not found" and nothing was ever stopped
    running=()
    while IFS= read -r line; do
      [[ -n "${line}" ]] && running+=("${line}")
    done < <(names)
    if [[ "${#running[@]}" -eq 0 ]]; then
      echo "No greenfinger containers."
      exit 0
    fi
    docker rm -f ${running[@]+"${running[@]}"} >/dev/null
    echo "Removed ${#running[@]} container(s). Their volumes are kept; remove them with:"
    echo "  docker volume rm \$(docker volume ls -q --filter name=^${PREFIX}-)"
    exit 0
    ;;
esac

if ! compgen -G "${SCRIPT_DIR}/lib/greenfinger-api-*.jar" >/dev/null; then
  echo "Cannot find lib/greenfinger-api-<version>.jar beside this script." >&2
  echo "Build it with 'mvn -pl greenfinger-api -am package'." >&2
  exit 1
fi

echo "Building ${IMAGE} ..."
docker build -q -f "${DOCKER_DIR}/Dockerfile" -t "${IMAGE}" "${SCRIPT_DIR}" >/dev/null
[[ "${COMMAND}" == "build" ]] && { echo "Built ${IMAGE}."; exit 0; }

# Created with the subnet the addresses below come from. An existing network with a different
# subnet is left alone and reported, because removing somebody's network is not this script's
# business -- pass GF_NETWORK to use another name instead.
if docker network inspect "${NETWORK}" >/dev/null 2>&1; then
  existing="$(docker network inspect -f '{{range .IPAM.Config}}{{.Subnet}}{{end}}' "${NETWORK}" 2>/dev/null || true)"
  if [[ -n "${existing}" && "${existing}" != "${SUBNET}" ]]; then
    echo "Network ${NETWORK} exists with subnet ${existing}, not ${SUBNET}." >&2
    echo "Remove it with 'docker network rm ${NETWORK}', or set GF_NETWORK to another name." >&2
    exit 1
  fi
else
  docker network create --subnet "${SUBNET}" "${NETWORK}" >/dev/null
fi

# Every node is told the whole list, itself included. Discovery only needs one door to open, but
# listing all of them means no node depends on a particular other one being up first.
# Addresses, assigned before anything starts: every node is told the whole list including itself,
# which is what lets them be started in any order.
node_ip() { printf '%s%d' "${IP_PREFIX}" "$((IP_START + $1 - 1))"; }

peers=""
for ((i = 1; i <= NODES; i++)); do
  peers="${peers}${peers:+,}$(node_ip "${i}")"
done

# Anything already set in this shell that names a store or a database is handed to every node.
# The alternative is editing config/application.yml for a run, which is how a test setting ends
# up committed. Note that "localhost" inside a container is the container: a database or a search
# server running on this machine is host.docker.internal from in there.
PASS_THROUGH=(
  GF_DB_URL GF_DB_USERNAME GF_DB_PASSWORD GF_DB_DRIVER GF_DB_DIALECT
  GF_OUTPUT_TYPES
  GF_FILE_TARGET GF_MINIO_ENDPOINT GF_MINIO_ACCESS_KEY GF_MINIO_SECRET_KEY GF_MINIO_BUCKET
  GF_INDEX_PROVIDER GF_INDEX_PREFIX GF_ES_URIS GF_ES_USERNAME GF_ES_PASSWORD GF_ES_ANALYZER
  GF_VECTOR_STORE GF_VECTOR_ES_URIS GF_VECTOR_ES_USERNAME GF_VECTOR_ES_PASSWORD
  GF_QDRANT_URL GF_QDRANT_API_KEY GF_WEAVIATE_URL
  GF_EMBEDDING_PROVIDER GF_EMBEDDING_PRELOAD GF_EMBEDDING_OFFLINE GF_MODEL_DIR
  GF_WORK_THREADS GF_MAX_FETCH_SIZE GF_LOG_LEVEL
  GF_COMPLETION_CHECK_INTERVAL GF_IDLE_TIMEOUT GF_MAX_CONSECUTIVE_FAILURES GF_API_BASE_URL
  GF_CORS_ORIGINS GF_USERS GF_TOKEN_SECRET GF_TOKEN_VALIDITY
)

# The browser loads the app from the front end container and its requests are proxied to a node
# carrying that origin. A node that has not been told about it answers 403 Invalid CORS request,
# which looks in the browser exactly like a wrong password. Told here rather than left to the
# configuration default, because the port is an argument to this script.
# A signed token is only accepted by a node that signs the same way, so several nodes without a
# shared secret means signing in again on every other request. One node is fine either way.
if [[ "${NODES}" -gt 1 && -z "${GF_TOKEN_SECRET:-}" ]]; then
  echo "  note: GF_TOKEN_SECRET is not set, so each node signs tokens with its own." >&2
  echo "  Behind the front end container a browser will be asked to sign in repeatedly." >&2
  echo "  Set it in .env: GF_TOKEN_SECRET=\$(openssl rand -base64 48)" >&2
fi

if [[ "${WEB}" == "1" && -z "${GF_CORS_ORIGINS:-}" ]]; then
  export GF_CORS_ORIGINS="http://localhost:${WEB_PORT},http://127.0.0.1:${WEB_PORT}"
fi
env_args=()
for name in "${PASS_THROUGH[@]}"; do
  if [[ -n "${!name:-}" ]]; then
    env_args+=(-e "${name}=${!name}")
  fi
done
if [[ ${#env_args[@]} -gt 0 ]]; then
  echo "Passing through: $(printf '%s ' "${PASS_THROUGH[@]}" | tr ' ' '\n' | while read -r n; do [[ -n "${!n:-}" ]] && printf '%s ' "$n"; done)"
fi

echo "Starting ${NODES} node(s) on network ${NETWORK}."
for ((i = 1; i <= NODES; i++)); do
  port=$((BASE_PORT + i - 1))
  name="${PREFIX}-${i}"
  docker rm -f "${name}" >/dev/null 2>&1 || true
  if [[ -n "${DATA_ROOT}" ]]; then
    mkdir -p "${DATA_ROOT}/${name}"
    mount="${DATA_ROOT}/${name}:/app/data"
  else
    mount="${name}-data:/app/data"
  fi
  docker run -d \
    --name "${name}" \
    --network "${NETWORK}" \
    --ip "$(node_ip "${i}")" \
    --memory "${MEMORY}" \
    -p "${port}:50080" \
    -v "${mount}" \
    -v "${SCRIPT_DIR}/config:/app/config:ro" \
    -e GF_CLUSTER_NAME="${CLUSTER_NAME}" \
    -e GF_CLUSTER_PORT="${CLUSTER_PORT}" \
    -e GF_CLUSTER_HOSTS="${peers}" \
    -e GF_CLUSTER_ADVERTISE_HOST="$(node_ip "${i}")" \
    -e GF_PROFILE="${GF_PROFILE:-dev}" \
    ${env_args[@]+"${env_args[@]}"} \
    "${IMAGE}" >/dev/null
  echo "  ${name}  http://localhost:${port}  cluster $(node_ip "${i}")"
done

# ---- the front end ---------------------------------------------------------------------------
if [[ "${WEB}" == "1" ]]; then
  # index.html rather than the directory, for the same reason run-local.sh checks for it: a
  # half-finished build leaves a directory behind, and a container serving one is a blank page.
  if [[ ! -f "${DOCKER_DIR}/static/index.html" ]]; then
    if [[ -d "${DOCKER_DIR}/static" ]]; then
      echo "  docker/static has no index.html: the build did not finish. No front end container." >&2
    else
      echo "  no docker/static directory, so no front end container." >&2
    fi
    echo "  Build it with 'npm run build:deploy' in frontend/greenfinger-ui, or set GF_WEB=0." >&2
  else
    # The nodes just started, by the addresses they were given. The container spreads requests
    # across them; a signed token is checked by whichever one gets it, so any of them will do.
    upstreams=""
    for ((i = 1; i <= NODES; i++)); do
      upstreams="${upstreams}${upstreams:+,}$(node_ip "${i}"):50080"
    done

    # The api address the browser uses. Empty is right here -- the page and the api arrive on the
    # same origin, because this container forwards /v2 itself -- and GF_API_BASE_URL overrides it
    # for a deployment that puts the api somewhere else.
    printf 'window.__GF__ = { apiBaseUrl: "%s" };\n' "${GF_API_BASE_URL:-}" \
        > "${DOCKER_DIR}/static/env.js"

    docker rm -f "${WEB_NAME}" >/dev/null 2>&1 || true
    docker build -q -f "${DOCKER_DIR}/Dockerfile.web" -t "${WEB_IMAGE}" "${SCRIPT_DIR}" >/dev/null
    docker run -d \
      --name "${WEB_NAME}" \
      --network "${NETWORK}" \
      -e "GF_UPSTREAMS=${upstreams}" \
      -p "${WEB_PORT}:80" \
      "${WEB_IMAGE}" >/dev/null
    echo "  ${WEB_NAME}    http://localhost:${WEB_PORT}  (the app, in front of all ${NODES})"
  fi
fi

echo
echo "Open the app:      http://localhost:${WEB_PORT}"
echo "Watch them join:   ./run-docker.sh logs 1"
echo "Stop them:         ./run-docker.sh stop"
