#!/usr/bin/env bash
#
# Greenfinger, the prompt.
#
#     ./greenfinger-face.sh                    # opens greenfinger:>
#     greenfinger:> catalog-save               # one question per setting
#     greenfinger:> catalog-crawl --id=<id>    # the id it printed
#     greenfinger:> status --all               # the dashboard, live
#     greenfinger:> help                       # every command
#     greenfinger:> exit
#
# Return and you are in. It takes no arguments at all: what to do is typed at the prompt once it
# is open, and how this node is configured -- which cluster, where the data goes -- is in run.conf
# beside this script, the same file greenfinger-cli.sh, run-local.sh and run-docker.sh read.
#
# A session: one jvm that stays open and takes command after command, so the crawl started by one
# line is still running when the next is typed and `status` can watch it. Every command is the
# same command greenfinger-cli.sh runs one at a time -- the difference is how long the process
# lives, not what it can do. `catalog-crawl --id=<id> --node=3` from here forks two more nodes and
# stops them when the crawl ends; this process is the third, and the first to hold the cluster
# port, which is what makes it the leader.
#
# One implementation, two entry points: this hands over to greenfinger-cli.sh with the prompt
# asked for, so the jar, the configuration directory, run.conf, the .env file, the data store and
# the extra nodes are all found by exactly the same code whichever of the two you typed.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -gt 0 ]]; then
  echo "greenfinger-face.sh takes no arguments: it opens the prompt, and commands are typed" >&2
  echo "there. Run '$*' at the prompt, or with ./greenfinger-cli.sh for one command at a time." >&2
  echo "Settings live in ${SCRIPT_DIR}/run.conf" >&2
  exit 1
fi

CLI="${SCRIPT_DIR}/greenfinger-cli.sh"
if [[ ! -x "${CLI}" ]]; then
  if [[ -f "${CLI}" ]]; then
    exec env GREENFINGER_FACE=1 bash "${CLI}"
  fi
  echo "Cannot find greenfinger-cli.sh beside this script (${SCRIPT_DIR})." >&2
  exit 1
fi

export GREENFINGER_FACE=1
exec "${CLI}"
