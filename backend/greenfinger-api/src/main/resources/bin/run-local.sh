#!/usr/bin/env bash
#
# Greenfinger, the server.
#
# This is how the http api and the front end are started -- here, or on a real server; there is no
# separate launcher for one node. A crawl always runs on a cluster and one process is a cluster of
# one, so this starts nodes rather than "a server": GF_NODES=1 is the ordinary installation, and
# more than one is the same thing sharing the work. Nodes with the same cluster name and cluster
# port find each other, on this machine or on somebody else's.
#
# run-docker.sh is this in containers, and nothing else.
#
#     ./run-local.sh                 start what run.conf describes
#     ./run-local.sh status          what is running
#     ./run-local.sh stop            stop them all ('down' is the same thing)
#     ./run-local.sh help            this
#
# How many nodes, on which ports, where the data goes: run.conf, beside this script. There are no
# options -- the shape of a run belongs in a file you can read back later rather than in a line
# somebody typed once, and a run that has to be reproduced tomorrow is the normal case here.
# Anything already in the environment still wins, so `GF_NODES=3 ./run-local.sh` is a one-off.
#
# Several machines is the same command on each, with GF_CLUSTER_HOSTS naming them in run.conf:
#
#     machine A:  GF_CLUSTER_HOSTS=<A>,<B>    # a cluster of one, expecting B
#     machine B:  GF_CLUSTER_HOSTS=<A>,<B>    # joins it, now two
#
# Every node keeps a complete copy of everything -- its own database file, its own pages, its own
# dedup stores -- and replication keeps the copies the same. That is why each node here gets its
# own data directory rather than sharing one: two nodes on one H2 file would spend their time
# negotiating a lock, and the copies are meant to be independent anyway.
#
# The url carries AUTO_SERVER=TRUE all the same, and that is not about the other nodes: it is what
# lets greenfinger-cli.sh and greenfinger-face.sh open node 1's database while node 1 is running.
# They are meant to be one installation -- a catalog created at the prompt is in the front end the
# moment it is refreshed -- and without it the second process is told the file is already in use.
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

COMMAND="start"
while [[ $# -gt 0 ]]; do
  case "$1" in
    # down is stop: docker compose's word for it, and the one people reach for
    start|stop|down|status) COMMAND="$1"; shift ;;
    help) sed -n '2,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "This script takes a verb and nothing else: start, stop (down), status, help." >&2
      echo "Everything else is in run.conf beside it." >&2
      exit 1
      ;;
  esac
done

NODES="${GF_NODES:-1}"
BASE_PORT="${GF_BASE_PORT:-50080}"
DATA_ROOT="${GF_DATA_ROOT:-${SCRIPT_DIR}/data}"
CLUSTER_HOSTS="${GF_CLUSTER_HOSTS:-}"
# One directory for every launcher's log; the file name inside it says which process wrote it.
LOG_DIR="${GF_LOG_DIR:-${SCRIPT_DIR}/logs}"
export GF_LOG_DIR="${LOG_DIR}"

# How much heap. One setting for all four launchers -- run.conf's GF_MEMORY -- because "how much
# memory does it get" is the same question whether the jvm is started here, by run-local.sh or in
# a container, and having to remember three different names for it is how one of them gets missed.
#
# JAVA_OPTS still wins outright, for the run that needs a flag this does not offer.
MEMORY="${GF_MEMORY:-1g}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx${MEMORY} -Dfile.encoding=UTF-8}"

if ! [[ "${NODES}" =~ ^[0-9]+$ ]] || [[ "${NODES}" -lt 1 ]]; then
  echo "GF_NODES in run.conf takes a number of nodes, 1 or more." >&2
  exit 1
fi

# The pid files go with the logs: they are runtime bookkeeping about the same processes, and one
# directory per concern is what keeps deploy/ down to config, lib, data and logs.
RUN_DIR="${LOG_DIR}"
mkdir -p "${RUN_DIR}"

node_pid_file() { echo "${RUN_DIR}/node-$1.pid"; }

status() {
  local any=0
  for pid_file in "${RUN_DIR}"/node-*.pid; do
    [[ -e "${pid_file}" ]] || continue
    local pid node
    pid="$(cat "${pid_file}")"
    node="$(basename "${pid_file}" .pid)"
    if kill -0 "${pid}" 2>/dev/null; then
      echo "${node}  pid ${pid}  running"
      any=1
    else
      echo "${node}  pid ${pid}  gone"
      rm -f "${pid_file}"
    fi
  done
  [[ "${any}" -eq 1 ]] || echo "No greenfinger nodes running."
}

stop_all() {
  local stopped=0
  for pid_file in "${RUN_DIR}"/node-*.pid; do
    [[ -e "${pid_file}" ]] || continue
    local pid
    pid="$(cat "${pid_file}")"
    if kill -0 "${pid}" 2>/dev/null; then
      # a crawl in flight winds down rather than being killed: pages already fetched still reach
      # the outputs, and the frontier stays consistent for a resume
      kill "${pid}" 2>/dev/null || true
      stopped=$((stopped + 1))
    fi
    rm -f "${pid_file}"
  done
  # the locks go with them, so a restart is not refused by a node that is no longer there
  find "${SCRIPT_DIR}/data" -maxdepth 2 -name '.node.lock' -delete 2>/dev/null || true
  echo "Stopped ${stopped} node(s)."
}

case "${COMMAND}" in
  status)     status; exit 0 ;;
  stop|down)  stop_all; exit 0 ;;
esac

# bin/, by the versioned name packaging gives it; newest wins if several are left there.
JAR="${GREENFINGER_JAR:-}"
if [[ -z "${JAR}" ]]; then
  for candidate in "${SCRIPT_DIR}"/lib/greenfinger-api-*.jar; do
    if [[ -f "${candidate}" ]]; then
      if [[ -z "${JAR}" || "${candidate}" -nt "${JAR}" ]]; then JAR="${candidate}"; fi
    fi
  done
fi
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "Cannot find lib/greenfinger-api-<version>.jar beside this script." >&2
  echo "Build it with 'mvn -pl greenfinger-api -am package'." >&2
  exit 1
fi

if [[ -n "${CLUSTER_HOSTS}" ]]; then
  export GF_CLUSTER_HOSTS="${CLUSTER_HOSTS}"
  if [[ -z "${GF_CLUSTER_ADVERTISE_HOST:-}" ]]; then
    # empty means "work it out", which is what spreader does when it is not told
    export GF_CLUSTER_ADVERTISE_HOST=""
  fi
fi

# A cluster is "everybody with this name on this port", and both halves matter -- for different
# reasons, which is worth spelling out because getting one right and the other wrong produces two
# different failures.
#
# The NAME decides who joins whom. Two deployments sharing it become one cluster and find that out
# by replicating each other's writes, deletes included; that is how a stray set of containers left
# over from an earlier test merged with these nodes and emptied their catalog table.
#
# The PORT is the election: whoever holds it leads, and holding a port is machine-wide rather than
# per cluster. So a second cluster with its own name but the same port never elects anybody at all
# -- the port is already taken, by a node it does not consider a member. Everything then works
# except the things that need a leader, and the counters simply stop moving.
#
# So both are moved: run-local, run-docker and the plain launcher each have their own of each.
export GF_CLUSTER_NAME="${GF_CLUSTER_NAME:-greenfinger-local}"
export GF_CLUSTER_PORT="${GF_CLUSTER_PORT:-22010}"

JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
fi

# A node keeps a complete copy: its own database file, its own pages, its own dedup stores.
# Two nodes pointed at one of those is not a shared cache, it is two processes writing the same
# file -- and the failure is not a clean one. H2 in file mode refuses the second opener, SQLite
# lets both in and they fight over the write lock, and the pages simply overwrite each other.
if [[ "${NODES}" -gt 1 && -n "${GF_DB_URL:-}" ]]; then
  case "${GF_DB_URL}" in
    jdbc:h2:file:*|jdbc:h2:./*|jdbc:h2:/*|jdbc:sqlite:*)
      echo "GF_DB_URL points every node at one file:" >&2
      echo "  ${GF_DB_URL}" >&2
      echo >&2
      echo "Each node needs its own, because each keeps a full copy and replication is what" >&2
      echo "keeps them the same. Unset GF_DB_URL and this script gives each node a database" >&2
      echo "under its own data directory, or point it at a shared server (MySQL, PostgreSQL," >&2
      echo "SQL Server, Oracle) which every node can use at once." >&2
      exit 1
      ;;
  esac
fi

# Same for the pages: GF_USER_DATA_DIR in .env would send every node's files to one directory.
if [[ "${NODES}" -gt 1 && -n "${GF_USER_DATA_DIR:-}" ]]; then
  echo "GF_USER_DATA_DIR sends every node's pages to ${GF_USER_DATA_DIR}." >&2
  echo "Use -d to give the nodes a common parent instead; each gets its own directory under it." >&2
  exit 1
fi

echo "Starting ${NODES} node(s) from port ${BASE_PORT}."
for ((i = 0; i < NODES; i++)); do
  port=$((BASE_PORT + i))
  node="node-$((i + 1))"
  data_dir="${DATA_ROOT}/${node}"

  # Somebody else's data directory, or this one's from a run that is still going. The lock is a
  # file holding a pid, checked rather than trusted: a node killed with -9 leaves one behind.
  lock="${data_dir}/.node.lock"
  if [[ -f "${lock}" ]] && kill -0 "$(cat "${lock}" 2>/dev/null)" 2>/dev/null; then
    echo "A node is already running on ${data_dir} (pid $(cat "${lock}"))." >&2
    echo "Stop it with './run-local.sh stop', or start these somewhere else with -d." >&2
    exit 1
  fi
  if [[ -d "${data_dir}" ]] && [[ -n "$(ls -A "${data_dir}" 2>/dev/null)" ]]; then
    echo "  ${node} is resuming what is already in ${data_dir}"
  fi

  mkdir -p "${data_dir}/system" "${LOG_DIR}"

  # Its own database file, its own pages, its own dedup stores. Replication is what makes the
  # copies agree; sharing the files instead would only move the problem into the filesystem.
  #
  # system/ is what the crawl needs while it runs; user/ is what it produced -- assets, index and
  # vector -- and is the half a search reads and the half worth backing up.
  # What is set stays set: a database url naming a server -- MySQL, PostgreSQL -- is the whole
  # point of the prod profile, and every node can use one at once. Only what nobody chose is
  # filled in, and the guards above have already refused the one combination that would corrupt
  # data: several nodes pointed at one file.
  GF_DATA_STORE="${data_dir}" \
  GF_SYSTEM_DATA_DIR="${GF_SYSTEM_DATA_DIR:-${data_dir}/system}" \
  GF_USER_DATA_DIR="${GF_USER_DATA_DIR:-${data_dir}/user}" \
  GF_DB_URL="${GF_DB_URL:-jdbc:h2:file:${data_dir}/system/greenfinger;AUTO_SERVER=TRUE}" \
  GF_LOG_FILE="${LOG_DIR}/${node}.log" \
  nohup "${JAVA_BIN}" ${JAVA_OPTS} \
      -jar "${JAR}" \
      --server.port="${port}" \
      --spring.config.additional-location="file:${SCRIPT_DIR}/config/,file:${SCRIPT_DIR}/config/api/" \
      > "${LOG_DIR}/${node}.out" 2>&1 &

  echo $! > "$(node_pid_file "$((i + 1))")"
  echo $! > "${lock}"
  echo "  ${node}  http://localhost:${port}  data ${data_dir}"
done

echo
echo "Watch them join:   tail -f ${LOG_DIR}/node-1.out | grep -i cluster"
echo "Stop them:         ./run-local.sh stop"
