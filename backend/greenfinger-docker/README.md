# greenfinger-docker

**Everything about containers, in one place.**

Not a Maven module. It is a directory of image definitions and registry material, read by the
build rather than compiled by it.

## What is here

| File | What it is | Where it ends up |
|---|---|---|
| `Dockerfile` | One crawler node. The same jar the launchers run | `deploy/docker/Dockerfile` |
| `Dockerfile.shell` | The prompt, as a container on the cluster network | `deploy/docker/Dockerfile.shell` |
| `Dockerfile.web` | The console. One address in front of every node | `deploy/docker/Dockerfile.web` |
| `dockerignore` | Keeps the data store and the logs out of the build context | `deploy/.dockerignore` |
| `docker-compose.yml` | Three nodes and a console, pulled from the registry | published as is |
| `dockerhub/*.md` | The overview pages on Docker Hub | pushed by the workflow |

## How they get there

The build context for every image is `deploy/`, because that is where the jar and the front end
build are. Nothing here is copied by hand.

``` text
  greenfinger-api/pom.xml     ──► deploy/docker/Dockerfile
   (maven-resources-plugin)        deploy/docker/Dockerfile.shell
                                   deploy/docker/Dockerfile.web
                                   deploy/dockerignore

  npm run build:deploy        ──► deploy/docker/static/
   (frontend/greenfinger-ui)       deploy/docker/server.js

  run-docker.sh               ──► deploy/.dockerignore   (copied from dockerignore)
                                  then docker build, with deploy/ as the context
```

`dockerignore` is carried under a plain name because docker reads it only at the root of the
context and the Maven resources plugin skips a name starting with a dot. `run-docker.sh` and the
CI workflow both do the rename.

## Two things that are deliberately elsewhere

**`server.js`** stays in `frontend/greenfinger-ui/docker/`. It is the console's http server,
twenty kilobytes of application code with a test beside it, and it is not a container artifact
just because a container runs it. `Dockerfile.web` picks it up out of the build context.

**`run-docker.sh`** stays in `greenfinger-api/src/main/resources/bin/`, next to `run-local.sh`.
They are a pair, the same script shaped for two environments, and splitting them would put four
launchers in two places.

## Building by hand

`run-docker.sh` does all of this. If you want to do it yourself:

``` shell
# the context has to exist first
mvn -f backend/pom.xml package -DskipTests
cd frontend/greenfinger-ui && npm run build:deploy && cd -

cd deploy
cp dockerignore .dockerignore
docker build -f docker/Dockerfile     -t greenfinger-api:local .
docker build -f docker/Dockerfile.web -t greenfinger-ui:local  .
```

## Published images

| | Docker Hub | GHCR |
|---|---|---|
| Crawler node | `fredfeng033/greenfinger-api` | `ghcr.io/paganini2008/greenfinger-api` |
| Console | `fredfeng033/greenfinger-ui` | `ghcr.io/paganini2008/greenfinger-ui` |

Pushed by `.github/workflows/docker-publish.yml` on a `v*` tag, to both registries from one
build, for `linux/amd64` and `linux/arm64`. The same workflow pushes `dockerhub/*.md` to the
overview pages, so the description on the registry is the one in this repository.

The api image installs a headless chromium at build time, and under QEMU that step is slow. The
platforms are `API_PLATFORMS` and `UI_PLATFORMS` at the top of the workflow if it ever needs to
be cut back to amd64.

## Running the published images

``` shell
echo "GF_TOKEN_SECRET=$(openssl rand -base64 32)" > .env
docker compose up -d
```

Two things the compose file exists to get right, both of which fail quietly rather than loudly:

| | |
|---|---|
| **One signing key across the nodes** | Tokens are signed rather than kept in a session, so a node cannot verify one signed with another key. Each node otherwise generates its own, warns about it in the first screen of log, and the console throws a signed in browser back to the login page on two requests in three |
| **2g a node, and 6GB for the three** | At 1.5g a node reports healthy, joins the cluster and takes work, and is killed a minute later as the image model loads. When it is the host that is short rather than the container, docker reports neither an OOM nor a non zero exit, so all you see is a container restarting with an empty log |
