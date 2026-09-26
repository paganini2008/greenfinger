#!/usr/bin/env bash
#
# Greenfinger, the prompt -- a terminal on a running cluster.
#
#     ./greenfinger-shell.sh --cluster=greenfinger-local     # opens greenfinger:>
#     greenfinger:> catalog-save               # one question per setting
#     greenfinger:> catalog-crawl --id=<id>    # the id it printed
#     greenfinger:> status --all               # the dashboard, live
#     greenfinger:> help                       # every command
#     greenfinger:> exit
#
# This is a client, exactly as the web page is. It crawls nothing, opens no database and keeps no
# index: it joins the cluster your nodes are in, under an application name of its own, and every
# command is a question put to whoever is leading. So the nodes have to be running first:
#
#     ./run-local.sh        # the nodes on this machine, and the page
#     ./run-docker.sh       # the same, in containers
#
# Each of those runs a cluster of its own, and this attaches to any of them by name:
#
#     ./greenfinger-shell.sh --cluster=greenfinger-docker --cluster-port=22020
#
# A crawl started here belongs to the cluster. Closing this terminal leaves it running, and
# opening the prompt again -- or the page -- picks the watching back up.
#
# Several terminals can run at once, each attached to a cluster of its own: they crawl nothing,
# hold nothing and share nothing, so there is no more to it than opening another one.
#
# It takes no arguments: what to do is typed at the prompt once it is open, and which cluster to
# join is in run.conf beside this script, the same file greenfinger-cli.sh and the two run scripts
# read.
#
# To crawl in this process instead of asking a cluster to, that is the other launcher:
#
#     ./greenfinger-cli.sh catalog-crawl --id=<id>
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# A key left empty in run.conf means "not set", and the yaml behind it reads
# ${GF_CLUSTER_PORT:22000} as "22000 unless the variable exists" -- an exported empty string
# exists, so the default never applies and the bind fails on an empty int. Dropped here, as
# greenfinger-cli.sh drops them, so run.conf can ship a key with no value beside its explanation.
drop_empty_gf() {
  local name
  for name in $(compgen -v | grep -E '^GF_' || true); do
    [[ -z "${!name}" ]] && unset "${name}"
  done
  return 0
}

# run.conf and .env, read exactly as the other launchers read them: which cluster to join is an
# installation setting, and a terminal that guessed differently would join a cluster of its own
# and report that nothing is running.
# Settings, in one place per reader and in one order.
#
#   run.conf   the launcher: how many processes, on which ports, with how much heap, and where
#              each one's directories go. Shell questions, answered before a jvm exists.
#   .env       the application: everything application.yml resolves with ${GF_...}, secret or not.
#              Spring imports this file too, so a main class started from an IDE reads the same
#              values with no launcher in sight -- which is why nothing the application reads is
#              allowed to live in run.conf.
#   the shell  whatever the caller already exported, which beats both.
#
# `set -a` because these names are the same GF_* the yaml reads: they have to be exported before
# the jvm starts. The caller's values are captured with `export -p`, whose output is made to be
# read back, rather than an associative array -- that is bash 4 and macOS ships 3.2. `declare -x`
# is what `export -p` prints, and `declare` inside a function makes a *local*, so it is rewritten
# to `export` before being evaluated or the caller's one-off vanishes on return.
load_settings() {
  local preset
  # Beside this launcher, which is the installation it belongs to. GREENFINGER_ENV names another
  # one -- a second configuration on the same machine, or a file kept outside the directory
  # because deploy/ is a build output and anything in it is replaced by the next build.
  ENV_FILE="${GREENFINGER_ENV:-${SCRIPT_DIR}/.env}"
  preset="$(export -p | grep -E ' GF_[A-Za-z0-9_]+=' | sed 's/^declare -x /export /' || true)"
  set -a
  if [[ -f "${SCRIPT_DIR}/run.conf" ]]; then
    # shellcheck disable=SC1091
    source "${SCRIPT_DIR}/run.conf"
    # An installation from before the split may still have the application's own settings in here.
    # They work -- this is sourced either way -- but only a launcher will ever see them, so say so
    # once rather than let somebody wonder why the IDE disagrees with the script.
    if grep -qE '^\s*(GF_CLUSTER_NAME|GF_CLUSTER_PORT|GF_CLUSTER_HOSTS|GF_DATA_STORE|GF_IDLE_TIMEOUT|GF_COMPLETION_CHECK_INTERVAL|GF_MAX_CONSECUTIVE_FAILURES)=[^[:space:]]' "${SCRIPT_DIR}/run.conf"; then
      echo "run.conf still sets values the application reads; move them to .env -- only the launchers can see them here." >&2
    fi
  fi
  if [[ -f "${ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
  fi
  set +a
  eval "${preset}"
  drop_empty_gf
}
load_settings

# Which cluster to attach to. A terminal can attach to any of them -- it watches and drives, it
# does not crawl -- so it says which, and there is no default: attaching to the wrong cluster
# reports an installation that is not the one you meant.
#
#   --cluster=<name>        the cluster to attach to
#   --cluster-port=<port>   its port; 22010 (run-local) unless said otherwise
#
CLUSTER_NAMED=0
[[ -n "${GF_CLUSTER_NAME:-}" ]] && CLUSTER_NAMED=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --cluster=*) CLUSTER_NAMED=1; export GF_CLUSTER_NAME="${1#*=}"; shift ;;
    --cluster) CLUSTER_NAMED=1; export GF_CLUSTER_NAME="${2:-}"; shift 2 ;;
    --cluster-port=*) export GF_CLUSTER_PORT="${1#*=}"; shift ;;
    --cluster-port) export GF_CLUSTER_PORT="${2:-}"; shift 2 ;;
    --cluster-hosts=*) export GF_CLUSTER_HOSTS="${1#*=}"; shift ;;
    --cluster-hosts) export GF_CLUSTER_HOSTS="${2:-}"; shift 2 ;;
    *)
      echo "greenfinger-shell.sh takes --cluster, --cluster-port and --cluster-hosts, and" >&2
      echo "nothing else: commands are typed at the prompt once it is open." >&2
      echo "Got: $1" >&2
      exit 1
      ;;
  esac
done


if [[ "${CLUSTER_NAMED}" != "1" ]]; then
  echo "Say which cluster to attach to: --cluster=<name>." >&2
  echo >&2
  echo "A terminal can attach to any of them, so there is no default:" >&2
  echo "  ./run-local.sh   runs  greenfinger-local  on 22010" >&2
  echo "  ./run-docker.sh  runs  greenfinger-docker on 22020" >&2
  echo "  ./greenfinger-cli.sh --cluster=<name>     on 22000" >&2
  echo >&2
  echo "  ./greenfinger-shell.sh --cluster=greenfinger-local" >&2
  echo "  ./greenfinger-shell.sh --cluster=nightly --cluster-port=22000" >&2
  exit 1
fi
# run-local's port unless another is named: attaching to the nodes on this machine is what a
# terminal does most of the time
export GF_CLUSTER_NAME
export GF_CLUSTER_PORT="${GF_CLUSTER_PORT:-22010}"

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
JAVA_OPTS="${GREENFINGER_JAVA_OPTS:--Xms256m -Xmx512m}"

find_jar() {
  local newest=""
  for dir in "$@"; do
    for candidate in "${dir}"/greenfinger-shell-*.jar; do
      if [[ -f "${candidate}" ]]; then
        if [[ -z "${newest}" || "${candidate}" -nt "${newest}" ]]; then newest="${candidate}"; fi
      fi
    done
  done
  printf '%s' "${newest}"
}

JAR="${GREENFINGER_JAR:-$(find_jar "${SCRIPT_DIR}/lib" "${SCRIPT_DIR}/.." "${SCRIPT_DIR}")}"
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "Cannot find greenfinger-shell-<version>.jar." >&2
  echo "Build it with 'mvn -pl greenfinger-shell -am package', or set GREENFINGER_JAR." >&2
  exit 1
fi

CONFIG_DIR="${GREENFINGER_CONFIG:-}"
if [[ -z "${CONFIG_DIR}" && -d "${SCRIPT_DIR}/config" ]]; then
  CONFIG_DIR="${SCRIPT_DIR}/config"
fi

LOG_DIR="${GF_LOG_DIR:-${SCRIPT_DIR}/logs}"
mkdir -p "${LOG_DIR}"

# One terminal per cluster is the ordinary case, and several at once is allowed: they share
# nothing but this directory, so the cluster's name goes in the file name.

# leader-eligible=false: it joins, sees everyone and uses every component, and never contends for
# the cluster port. Leadership means holding the lock register and the cache's authoritative copy,
# and a terminal that is closed when somebody stops typing is the worst candidate there is.
#
# The database autoconfiguration is switched off here rather than in the code: the jar carries six
# jdbc drivers and a JPA starter for the other face, and this one has no business opening a
# database. A different application name in the same cluster is the rest of the mechanism: the nodes are
# 'greenfinger', this is 'greenfinger-shell', and membersOf() tells them apart. It takes no part
# in the replicated cache or the task pool either -- both carry a crawl's work, and this process
# has none.
echo "Joining cluster '${GF_CLUSTER_NAME}' on port ${GF_CLUSTER_PORT} as a terminal."

exec "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR}" \
    --greenfinger.shell.client=true \
    --spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration \
    --spring.spreader.application-name="${GF_CLUSTER_SHELL_APP:-greenfinger-shell}" \
    --spring.spreader.leader-eligible=false \
    --spring.spreader.multiprocessing.cache.enabled=false \
    --spring.spreader.multiprocessing.pooling.enabled=false \
    --spring.banner.location=classpath:banner-shell.txt \
    --spring.shell.interactive.enabled=true \
    --logging.file.name="${GF_LOG_FILE:-${LOG_DIR}/greenfinger-shell-${GF_CLUSTER_NAME}.log}" \
    ${CONFIG_DIR:+--spring.config.additional-location="file:${CONFIG_DIR}/"}
