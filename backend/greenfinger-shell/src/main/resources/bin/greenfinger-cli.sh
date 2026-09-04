#!/usr/bin/env bash
#
# Greenfinger, single machine launcher -- one command, run once.
#
# Nothing has to be installed: the metadata goes into an H2 file and the pages onto disk.
#
# This is the one-shot face: it runs the command on the line and exits. For a session that stays
# open -- one jvm, many commands, the greenfinger:> prompt -- run ./greenfinger-face.sh instead.
# The two are the same program and read the same data; they differ only in how long they live.
#
# Quick start -- define a catalog by answering questions, then run it:
#
#     ./greenfinger-face.sh                    # the prompt
#     greenfinger:> catalog-save               # one question per setting
#     greenfinger:> catalog-crawl --id=<id>    # the id it printed
#
# One line at a time instead of the prompt:
#
#     ./greenfinger-cli.sh catalog-list
#     ./greenfinger-cli.sh catalog-crawl --id=<id>
#     ./greenfinger-cli.sh update --id=<id> --refresh=true
#     ./greenfinger-cli.sh delete --id=<id> --keep-latest=3
#     ./greenfinger-cli.sh options             # every catalog setting and its default
#     ./greenfinger-cli.sh help                # every command
#
# Every option is long form, and the catalog is always addressed by its id.
#
# Which cluster this node joins and where it writes are not options: they are in run.conf beside
# this script -- GF_CLUSTER_NAME, GF_CLUSTER_HOSTS, GF_DATA_STORE, GF_WORKER_ROOT -- and the same
# file is read by greenfinger-face.sh, run-local.sh and run-docker.sh. They
# describe the installation rather than the command, and a run that has to be reproduced tomorrow
# is the normal case here. A one-off is still a one-off:
#
#     GF_DATA_STORE=/var/gf ./greenfinger-cli.sh catalog-list
#
# More than one process on this machine:
#
#     ./greenfinger-cli.sh catalog-crawl --id=<id> --node=3
#
# `--node` is read here, before the jvm starts, because starting processes is not something a
# command inside one can do. It is left on the line as well: the crawl command declares it, so
# passing it through costs nothing and a script that greps its own invocation still sees it.
#
# The first process is node 1 and keeps the data directory it has always used, so nothing you
# crawled before disappears when you ask for more nodes. The other n-1 have no prompt: they join
# the same cluster, take their share of the urls, and are stopped when the first one exits. Each
# gets its own directory, because every node keeps a complete copy and replication is what keeps
# the copies the same -- two nodes on one H2 file is not a shared cache, it is a refused
# connection.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "${SCRIPT_DIR}/.." && pwd)"

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

# The node's own settings -- which cluster to join, where its data lives -- come from run.conf
# beside this script, not from the command line. They describe the installation rather than the
# command being run: the same answer every time you type anything, and a different answer is a
# different node. run-local.sh and run-docker.sh read the same file.
#
#   GF_CLUSTER_NAME    nodes agreeing on this name find each other
#   GF_CLUSTER_PORT    and elect on this port; both halves matter
#   GF_CLUSTER_HOSTS   the machines to knock on. Only needed to reach another machine; the
#                      default finds other processes on this one
#   GF_DATA_STORE      the one directory everything this node writes lives under. Empty means
#                      data/ beside this script, so it never depends on where you happened to cd
#   GF_WORKER_ROOT     where extra local nodes keep theirs, when --node asks for more than one
#
# What the caller already exported is put back afterwards, so `GF_DATA_STORE=/tmp/x ./greenfinger-cli.sh
# catalog-list` is still a one-off. `export -p` rather than an associative array: bash 4, and
# macOS ships 3.2.
if [[ -f "${SCRIPT_DIR}/run.conf" ]]; then
  # `declare -x` rather than `export` is what `export -p` prints, and `declare` inside a function
  # makes a *local* -- so evaluating it here would set a variable that vanishes on return, and the
  # caller's one-off would be silently ignored. Rewritten to `export`, which is global wherever it
  # is run.
  preset="$(export -p | grep -E ' GF_[A-Za-z0-9_]+=' | sed 's/^declare -x /export /' || true)"
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/run.conf"
  set +a
  eval "${preset}"
  drop_empty_gf
fi

DATA_STORE="${GF_DATA_STORE:-}"
CLUSTER_NAME="${GF_CLUSTER_NAME:-}"
CLUSTER_HOSTS="${GF_CLUSTER_HOSTS:-}"
WORKER_ROOT="${GF_WORKER_ROOT:-}"
# One directory for every launcher's log; the file name inside it says which process wrote it.
LOG_DIR="${GF_LOG_DIR:-${SCRIPT_DIR}/logs}"
export GF_LOG_DIR="${LOG_DIR}"

# --as-worker is the one thing still read off the line, and it is not a setting: the prompt passes
# it when it forks a node, so that node knows to take a worker directory and open no prompt of its
# own. Nobody types it.
AS_WORKER=0
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --as-worker) AS_WORKER=1; shift ;;
    --data-store|--data-store=*|--cluster|--cluster=*|--cluster-hosts|--cluster-hosts=*|--worker-dir|--worker-dir=*)
      echo "'${1%%=*}' is not an option any more: it is configuration, and it lives in" >&2
      echo "run.conf beside this script -- ${SCRIPT_DIR}/run.conf" >&2
      echo "Set GF_DATA_STORE, GF_CLUSTER_NAME, GF_CLUSTER_HOSTS or GF_WORKER_ROOT there." >&2
      exit 1
      ;;
    *) ARGS+=("$1"); shift ;;
  esac
done
set -- ${ARGS[@]+"${ARGS[@]}"}

# --node is a crawl option, so it can appear anywhere after the command word. Read rather than
# consumed: the command declares it too, and leaving it in place keeps the line the user typed
# and the line the jvm receives the same thing.
NODES=1
previous=""
for arg in "$@"; do
  case "${arg}" in
    --node=*) NODES="${arg#*=}" ;;
    *) [[ "${previous}" == "--node" ]] && NODES="${arg}" ;;
  esac
  previous="${arg}"
done
if ! [[ "${NODES}" =~ ^[0-9]+$ ]] || [[ "${NODES}" -lt 1 ]]; then
  echo "--node takes a whole number of nodes, at least 1. Got '${NODES}'." >&2
  exit 1
fi

# The jar carries its version in its name -- greenfinger-shell-2.0.0.jar -- so what a node is
# running can be read off the file. Newest wins when several are left in lib/, which is what makes
# an upgrade a copy rather than a copy and a delete.
find_jar() {
  local pattern="$1"; shift
  local newest=""
  for dir in "$@"; do
    for candidate in "${dir}"/${pattern}; do
      if [[ -f "${candidate}" ]]; then
        if [[ -z "${newest}" || "${candidate}" -nt "${newest}" ]]; then newest="${candidate}"; fi
      fi
    done
  done
  printf '%s' "${newest}"
}

JAR="${GREENFINGER_JAR:-}"
if [[ -z "${JAR}" ]]; then
  # bin/ first: that is where packaging puts it, so the jar sits under the launcher rather
  # than beside the configuration and the data
  JAR="$(find_jar 'greenfinger-shell-*.jar' \
      "${SCRIPT_DIR}/lib" "${APP_HOME}/lib" "${APP_HOME}" \
      "${APP_HOME}/target" "${SCRIPT_DIR}")"
  if [[ -z "${JAR}" ]]; then
    # what packaging produced before the version reached the file name, and before the module
    # was called greenfinger-shell
    JAR="$(find_jar 'greenfinger-cli-*.jar' \
        "${SCRIPT_DIR}/lib" "${APP_HOME}/lib" "${APP_HOME}" \
        "${APP_HOME}/target" "${SCRIPT_DIR}")"
  fi
fi

if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "Cannot find greenfinger-shell-<version>.jar." >&2
  echo "Build it with 'mvn -pl greenfinger-shell -am package', or set GREENFINGER_JAR." >&2
  exit 1
fi

# One configuration directory, beside the jar. Everything tunable lives there; the defaults it
# overrides come from the application's own @ConfigurationProperties classes.
CONFIG_DIR="${GREENFINGER_CONFIG:-}"
if [[ -z "${CONFIG_DIR}" ]]; then
  for candidate in "${SCRIPT_DIR}/config" "${APP_HOME}/config"; do
    if [[ -d "${candidate}" ]]; then CONFIG_DIR="${candidate}"; break; fi
  done
fi

# Secrets come from a .env file that is never committed, so an api key stays out of the
# configuration directory and out of the repository. Searched beside the launcher, then in the
# project root above it, then in the current directory.
ENV_FILE="${GREENFINGER_ENV:-}"
if [[ -z "${ENV_FILE}" ]]; then
  for candidate in "${SCRIPT_DIR}/.env" "${APP_HOME}/.env" "${APP_HOME}/backend/.env" \
                   "${APP_HOME}/../.env" "./.env"; do
    if [[ -f "${candidate}" ]]; then ENV_FILE="${candidate}"; break; fi
  done
fi
if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
  preset="$(export -p | grep -E ' GF_[A-Za-z0-9_]+=' || true)"
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
  eval "${preset}"
  drop_empty_gf
fi

# GF_DATA_STORE is one directory and everything goes under it, split in two:
#
#   system/  what the crawler needs while it is crawling -- the frontier, the two dedup stores,
#            and the H2 or SQLite file when no database server was configured. Advanced setting.
#            Losing it costs a resume, never a search.
#   user/    what was crawled -- assets/ (the pages and images), index/ and vector/. This is what
#            a search reads, so it is the directory worth backing up, copying and keeping.
#
# The default is data/ beside this script rather than ./data, so where you happened to cd to
# before typing the command does not decide where a crawl is written. Set GF_DATA_STORE in
# run.conf to put it somewhere else.
DATA_STORE="${DATA_STORE:-${SCRIPT_DIR}/data}"
export GF_DATA_STORE="${DATA_STORE}"
export GF_SYSTEM_DATA_DIR="${GF_SYSTEM_DATA_DIR:-${DATA_STORE}/system}"
export GF_USER_DATA_DIR="${GF_USER_DATA_DIR:-${DATA_STORE}/user}"
# Only system/ is created here. It is always local and always needed: the frontier and the dedup
# filters are RocksDB whatever else is configured. user/ is left to whoever writes into it, so a
# node whose assets go to MinIO and whose search is Elasticsearch and Qdrant ends up with no
# user/ directory at all rather than three empty ones.
mkdir -p "${GF_SYSTEM_DATA_DIR}"
case "${GF_DB_URL:-}" in
  # a database somebody configured on purpose is left alone; only the zero-install H2 file moves
  "") export GF_DB_URL="jdbc:h2:file:${GF_SYSTEM_DATA_DIR}/greenfinger;AUTO_SERVER=TRUE" ;;
  *) ;;
esac

# Nodes that agree on this name find each other and share the work. "default" so that two
# terminals on one machine are one cluster without anybody having to say so, and so that giving
# it a name is how you keep two crawls apart rather than how you join them up.
export GF_CLUSTER_NAME="${CLUSTER_NAME:-default}"

# Who to knock on. The configuration default is 127.0.0.1, which finds the other processes on this
# machine and nothing else -- right for the usual case and useless across machines.
#
# Naming hosts also unpins the advertised address. It is fixed at 127.0.0.1 in the configuration
# so that several processes on one machine do not tell each other to use an interface none of them
# can reach; the moment you name another machine that default becomes the bug, because the address
# this node hands out is one the other machine cannot dial. Set it explicitly to override.
if [[ -n "${CLUSTER_HOSTS}" ]]; then
  export GF_CLUSTER_HOSTS="${CLUSTER_HOSTS}"
  if [[ -z "${GF_CLUSTER_ADVERTISE_HOST:-}" ]]; then
    # empty means "work it out", which is what spreader does when it is not told
    export GF_CLUSTER_ADVERTISE_HOST=""
  fi
fi

# Under the data store, beside system/ and user/ rather than inside either: a worker directory
# nested in one of those would show up in everything that walks node 1's files, and one outside
# the data store is a fourth top level directory in deploy/ for something that is plainly data.
if [[ -z "${WORKER_ROOT}" ]]; then
  WORKER_ROOT="${DATA_STORE}/workers"
fi

JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
fi

if ! "${JAVA_BIN}" -version 2>&1 | grep -qE '"(1[7-9]|[2-9][0-9])'; then
  echo "Greenfinger needs Java 17 or later. Found:" >&2
  "${JAVA_BIN}" -version >&2
  exit 1
fi

# A crawl is IO bound, so most of the heap goes to the page currently being parsed.
# How much heap. One setting for all four launchers -- run.conf's GF_MEMORY -- because "how much
# memory does it get" is the same question whether the jvm is started here, by run-local.sh or in
# a container, and having to remember three different names for it is how one of them gets missed.
#
# JAVA_OPTS still wins outright, for the run that needs a flag this does not offer.
MEMORY="${GF_MEMORY:-1g}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx${MEMORY} -Dfile.encoding=UTF-8}"

# Every node keeps a complete copy -- its own database file, its own pages, its own dedup stores
# -- and replication is what keeps the copies the same. Anything in the environment that would
# send them all to one place has to be replaced here rather than inherited.
worker_env() {
  local dir="$1"
  mkdir -p "${dir}/system"
  GF_DATA_STORE="${dir}"
  GF_SYSTEM_DATA_DIR="${dir}/system"
  GF_USER_DATA_DIR="${dir}/user"
  # the same cluster, or they would be n crawlers rather than one crawl
  GF_CLUSTER_NAME="${GF_CLUSTER_NAME}"
  case "${GF_DB_URL:-}" in
    # a server every node can dial at once: shared on purpose, so it is left alone
    jdbc:mysql:*|jdbc:postgresql:*|jdbc:sqlserver:*|jdbc:oracle:*|jdbc:h2:tcp:*|jdbc:h2:ssl:*) ;;
    # unset, or a file: each node needs its own, since two openers of one H2 file is a refusal
    # and two of one SQLite file is a lock fight
    *) GF_DB_URL="jdbc:h2:file:${dir}/system/greenfinger" ;;
  esac
  export GF_DATA_STORE GF_SYSTEM_DATA_DIR GF_USER_DATA_DIR GF_DB_URL GF_CLUSTER_NAME
}

# ---- one worker node, in the foreground ------------------------------------------------------
#
# Not for typing. The prompt uses it: `catalog-crawl --node=3` inside a session forks two of these
# and they join the cluster the session is already in. Kept here rather than written again in Java
# so that a worker started by the prompt and a worker started by this script are the same process
# with the same environment -- there is one place that decides what a worker's data directory, its
# database and its cluster are, and it is worker_env below.
#
#     GREENFINGER_WORKER_DIR=<dir> ./greenfinger-cli.sh --as-worker
#
# It replaces this shell, so whoever started it can stop it with a signal.
if [[ "${AS_WORKER}" == "1" ]]; then
  if [[ -z "${GREENFINGER_WORKER_DIR:-}" ]]; then
    echo "--as-worker needs GREENFINGER_WORKER_DIR." >&2
    exit 1
  fi
  worker_env "${GREENFINGER_WORKER_DIR}"
  exec "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR}" \
      --logging.file.name="${GREENFINGER_LOG:-${LOG_DIR}/worker.log}" \
      ${CONFIG_DIR:+--spring.config.additional-location="file:${CONFIG_DIR}/"} \
      --spring.shell.interactive.enabled=false \
      --greenfinger.shell.worker=true
fi

# The prompt forks its own extra nodes when somebody types --node=3 inside a session, and it does
# it by running this script with --as-worker. Handed the path rather than left to guess it: a jar
# run directly has no launcher, and should say so rather than fork whatever is beside it.
JAVA_OPTS="${JAVA_OPTS} -Dgreenfinger.launcher=${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
export GREENFINGER_WORKER_ROOT="${WORKER_ROOT}"

SPRING_ARGS=()
# Keep the log beside the launcher rather than in whatever directory you happen to be in;
# a relative path would leave a logs/ folder in every working directory.
SPRING_ARGS+=(--logging.file.name="${GREENFINGER_LOG:-${LOG_DIR}/greenfinger.log}")
if [[ -d "${CONFIG_DIR}" ]]; then
  SPRING_ARGS+=(--spring.config.additional-location="file:${CONFIG_DIR}/")
fi
# This launcher is the one-shot face: a command on the line, run once, and the process is gone.
# The prompt is greenfinger-face.sh, which is this script with GREENFINGER_FACE=1 set -- one
# implementation, two entry points, so the jar, the configuration, the data store and the extra
# nodes are found the same way whichever one you typed.
#
# `face` on the line still works, and means the same thing.
if [[ "${1:-}" == "face" ]]; then
  shift
  GREENFINGER_FACE=1
fi

if [[ "${GREENFINGER_FACE:-0}" == "1" ]]; then
  # The prompt reads its own lines, so a command word left on the launcher line would be run once
  # and the session would end on it -- the opposite of what asking for the prompt meant. Say so
  # and open the prompt anyway, which is what was asked for.
  if [[ $# -gt 0 ]]; then
    echo "The prompt takes its commands from the prompt. Type '$*' after greenfinger:>," >&2
    echo "or run it once with: ./greenfinger-cli.sh $*" >&2
    echo >&2
    set --
  fi
else
  # Nothing to run and no prompt to open: say what there is rather than starting a jvm that
  # would sit there waiting for input this face never reads.
  if [[ $# -eq 0 ]]; then
    set -- help
  fi
  SPRING_ARGS+=(--spring.shell.interactive.enabled=false)
fi

# One process is the ordinary case and nothing about it changes: no worker is started, no data
# directory is imposed, and the jvm is replaced rather than wrapped.
if [[ "${NODES}" -eq 1 ]]; then
  exec "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR}" "${SPRING_ARGS[@]}" "$@"
fi

# ---- more than one node on this machine ------------------------------------------------------

WORKER_PIDS=()

stop_workers() {
  # asked to stop, not killed: a worker winds its crawl down at the next check, so pages already
  # fetched still reach the outputs and the frontier stays consistent for a resume
  for pid in "${WORKER_PIDS[@]:-}"; do
    [[ -n "${pid}" ]] && kill "${pid}" 2>/dev/null || true
  done
  for pid in "${WORKER_PIDS[@]:-}"; do
    [[ -n "${pid}" ]] && wait "${pid}" 2>/dev/null || true
  done
}
trap stop_workers EXIT INT TERM

mkdir -p "${LOG_DIR}"
echo "Starting ${NODES} node(s): this prompt and $((NODES - 1)) worker(s)."

for ((i = 2; i <= NODES; i++)); do
  worker_dir="${WORKER_ROOT}/cli-${i}"

  # somebody else's directory, or this one's from a run that is still going. The lock holds a
  # pid and is checked rather than trusted: a process killed with -9 leaves one behind
  lock="${worker_dir}/.node.lock"
  if [[ -f "${lock}" ]] && kill -0 "$(cat "${lock}" 2>/dev/null)" 2>/dev/null; then
    echo "A node is already running on ${worker_dir} (pid $(cat "${lock}"))." >&2
    echo "Stop it, or start these somewhere else with --worker-dir." >&2
    exit 1
  fi

  ( worker_env "${worker_dir}"
    exec "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR}" \
        --logging.file.name="${LOG_DIR}/cli-worker-${i}.log" \
        ${CONFIG_DIR:+--spring.config.additional-location="file:${CONFIG_DIR}/"} \
        --spring.shell.interactive.enabled=false \
        --greenfinger.shell.worker=true
  ) > "${LOG_DIR}/cli-worker-${i}.out" 2>&1 &

  WORKER_PIDS+=("$!")
  mkdir -p "${worker_dir}"
  echo "$!" > "${lock}"
  echo "  worker ${i}  data ${worker_dir}  log ${LOG_DIR}/cli-worker-${i}.out"
done

echo "  node 1     data ${GF_DATA_STORE}  (this process, its usual data)"
echo

# not exec: the trap above has to survive to stop the workers when this returns
"${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR}" "${SPRING_ARGS[@]}" "$@"
