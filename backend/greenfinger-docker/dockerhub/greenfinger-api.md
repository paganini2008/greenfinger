# Greenfinger

**One url in. A searchable archive out.**

A distributed web crawler for the JVM. One pass writes plain files, a full text index and a vector
collection. Every node runs the same jar, there is no coordinator to deploy, and the first crawl
needs no database, no search server and no API key.

This image is one crawler node. For the console in front of it, see
[fredfeng033/greenfinger-ui](https://hub.docker.com/r/fredfeng033/greenfinger-ui).

![Watching a crawl](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-live.jpg)

- **Source:** https://github.com/paganini2008/greenfinger
- **Documentation:** https://paganini2008.github.io/greenfinger/
- **Licence:** Apache 2.0

---

## Quick start

One node, nothing else running:

```
docker run -d --init --name greenfinger \
  -p 50080:50080 \
  -v greenfinger-data:/app/data \
  fredfeng033/greenfinger-api:latest
```

Then open `http://localhost:50080`. The jar serves the console itself, so a single container is a
complete installation. Sign in with `admin` and `admin123`.

Give the container 2GB. Both the ONNX embedding models and the headless chromium live off heap,
so a 1g node is killed the first time it meets either.

For three nodes with one address in front of them, see **Running a cluster** below.

---

## What it does

| | |
|---|---|
| **Crawls a whole site** | Two boundary rules that cannot be switched off: the registrable domain has to match, and every url has to sit under the start url. robots.txt on top |
| **Three outputs, one pass** | Files on disk or in MinIO, a full text index in Lucene or Elasticsearch, vectors in Lucene, Qdrant, Weaviate or Elasticsearch |
| **Rebuilds without recrawling** | `replay` rebuilds the index or the vectors from the files. The site is never touched again |
| **Keeps pictures** | From `<img>`, `srcset`, `<picture>` and `og:image`, filtered by size and type. Identical bytes stored once |
| **Reads past html** | Document links recorded on every crawl. Text, markdown and csv parse out of the box once reading is switched on |
| **Searches three ways** | By words, by meaning, and by describing a picture. The embedding models run locally as ONNX |
| **Resumable and versioned** | The frontier is on disk. A rebuild opens a new version beside the old one, which keeps answering searches |
| **Distributed** | One url dispatched is one call to whichever node owns it. No central queue and no leader in the fetch path |

---

## Tags

| Tag | What it is |
|---|---|
| `latest` | The most recent release, pre-releases included |
| `2.0.0` | An exact version |
| `2.0` | The latest patch of that minor version |
| `edge` | Built by hand from `main`, between releases |

Both `linux/amd64` and `linux/arm64`. The same images are on GHCR as
`ghcr.io/paganini2008/greenfinger-api`.

---

## Running a cluster

Nodes agreeing on the cluster **name and port** find each other and share one crawl. Every node is
told the whole peer list including itself, so they can start in any order.

Save this as `docker-compose.yml`:

```yaml
name: greenfinger

# Three crawler nodes and one console in front of them. Static addresses because the cluster
# setting is a list of ip addresses, so a node has to advertise something its peers can dial.
x-api: &api
  image: fredfeng033/greenfinger-api:latest
  restart: unless-stopped
  init: true      # reaps the headless browser's helper processes
  mem_limit: 2g   # the embedding models and chromium are both off heap
  environment: &api-env
    GF_CLUSTER_NAME: greenfinger
    GF_CLUSTER_PORT: '22020'
    GF_CLUSTER_HOSTS: 172.28.0.10,172.28.0.11,172.28.0.12
    # Tokens are signed, not looked up, so every node needs the same key or it rejects the
    # others' sign-ins and the console logs you out on two requests in three.
    GF_TOKEN_SECRET: "${GF_TOKEN_SECRET:?missing. Run - echo GF_TOKEN_SECRET=$$(openssl rand -base64 32) > .env}"
  networks:
    cluster:

services:
  api-1:
    <<: *api
    environment:
      <<: *api-env
      GF_CLUSTER_ADVERTISE_HOST: 172.28.0.10
    networks:
      cluster:
        ipv4_address: 172.28.0.10
    ports:
      - "50080:50080"   # one node published, for curl. The console does not need it
    volumes:
      - api-1-data:/app/data

  api-2:
    <<: *api
    environment:
      <<: *api-env
      GF_CLUSTER_ADVERTISE_HOST: 172.28.0.11
    networks:
      cluster:
        ipv4_address: 172.28.0.11
    volumes:
      - api-2-data:/app/data

  api-3:
    <<: *api
    environment:
      <<: *api-env
      GF_CLUSTER_ADVERTISE_HOST: 172.28.0.12
    networks:
      cluster:
        ipv4_address: 172.28.0.12
    volumes:
      - api-3-data:/app/data

  ui:
    image: fredfeng033/greenfinger-ui:latest
    restart: unless-stopped
    environment:
      GF_UPSTREAMS: 172.28.0.10:50080,172.28.0.11:50080,172.28.0.12:50080
    ports:
      - "9700:80"
    networks:
      cluster:
    depends_on: [api-1, api-2, api-3]

networks:
  cluster:
    ipam:
      config:
        - subnet: 172.28.0.0/16

volumes:
  api-1-data:
  api-2-data:
  api-3-data:
```

Then:

```
echo "GF_TOKEN_SECRET=$(openssl rand -base64 32)" > .env
docker compose up -d
open http://localhost:9700
```

Or take the annotated version, which has commented blocks for PostgreSQL, MySQL, Elasticsearch,
Qdrant and MinIO:

```
curl -O https://raw.githubusercontent.com/paganini2008/greenfinger/main/backend/greenfinger-docker/docker-compose.yml
```

Four things in there are easy to get wrong:

| | Why |
|---|---|
| `GF_TOKEN_SECRET`, the same on every node | Tokens are signed rather than kept in a session. Without a shared key each node signs with its own and rejects the others', so the console logs you out on two requests in three |
| `init: true` | The entry point execs java, so without it nothing reaps the browser's helper processes when a page is rendered and they stay as zombies for the life of the container |
| A volume per node | Each node keeps its own complete copy. A container replaced without one starts from an empty database and the whole crawl has to be sent to it again |
| Static addresses | The cluster setting is a list of ip addresses, so a node has to advertise something its peers can dial. Service names do not work here |

**Give Docker 6GB for this.** A warm node holds about 1.7GB. Short of it the host kernel kills
whichever jvm it reaches first, and because that is the host rather than the container's own
limit, docker reports neither an OOM nor a non zero exit: all you see is containers restarting
with nothing in the log. One node and the console fit in 2GB.

---

## Configuration

Nothing below is required. H2 and an embedded Lucene index are the defaults.

### Metadata store

| Variable | Default | Description |
|---|---|---|
| `GF_DB_URL` | an H2 file in the volume | PostgreSQL, MySQL, SQL Server, Oracle, SQLite |
| `GF_DB_USERNAME` | empty | |
| `GF_DB_PASSWORD` | empty | |
| `GF_DB_DDL` | `update` | Use `none` with the schema scripts on anything you cannot lose |

### Full text and vectors

| Variable | Default | Description |
|---|---|---|
| `GF_INDEX_PROVIDER` | `lucene` | Or `elasticsearch`, with `GF_ES_URIS` |
| `GF_ES_URIS` | empty | Comma separated |
| `GF_LUCENE_ANALYZER` | `standard` | `standard`, `smartcn` or `cjk` |
| `GF_ES_ANALYZER` | `standard` | `ik_max_word` needs the analysis-ik plugin |
| `GF_VECTOR_STORE` | `lucene` | Or `elasticsearch`, `qdrant`, `weaviate` |
| `GF_QDRANT_URL` | empty | |
| `GF_WEAVIATE_URL` | empty | |

### Files

| Variable | Default | Description |
|---|---|---|
| `GF_FILE_TARGET` | `local` | Or `minio`. Object keys match the local paths exactly |
| `GF_MINIO_ENDPOINT` | empty | AWS S3 and GCS publish S3 compatible endpoints too |
| `GF_MINIO_ACCESS_KEY` | empty | |
| `GF_MINIO_SECRET_KEY` | empty | |
| `GF_MINIO_BUCKET` | `greenfinger` | |

### Embeddings

| Variable | Default | Description |
|---|---|---|
| `GF_EMBEDDING_PROVIDER` | `local` | ONNX in this process. Or `ollama`, `openai` |
| `GF_PRELOAD_TEXT_MODEL` | `true` | Loaded at startup on a background thread |
| `GF_PRELOAD_IMAGE_MODEL` | `true` | |
| `GF_MODEL_DIR` | `/app/data/models` | Inside the volume, so they are downloaded once |
| `OPENAI_API_KEY` | empty | |

### Cluster

| Variable | Default | Description |
|---|---|---|
| `GF_CLUSTER_NAME` | `greenfinger` | Nodes agreeing on name **and** port find each other |
| `GF_CLUSTER_PORT` | `22000` | |
| `GF_CLUSTER_HOSTS` | `127.0.0.1` | The whole peer list, including this node |
| `GF_CLUSTER_ADVERTISE_HOST` | `127.0.0.1` | What peers dial. Set it per node |
| `GF_CLUSTER_TRANSPORT` | `NETTY` | Or the built in `NIO` |
| `GF_TOKEN_SECRET` | generated | **Set the same value on every node.** Tokens are signed rather than kept in a session, so a node cannot verify one signed with another key. Left unset, each node generates its own and warns about it |

### Crawl defaults

Every one of these is also a per catalog setting, and the catalog wins.

| Variable | Default | Description |
|---|---|---|
| `GF_OUTPUT_TYPES` | `file` | `file,index,vector`. file is always on |
| `GF_EXTRACTOR` | `adaptive` | adaptive, restclient, htmlunit, playwright, selenium |
| `GF_MAX_FETCH_SIZE` | `10000` | Saved pages before a crawl stops |
| `GF_FETCH_INTERVAL` | `1000` | Milliseconds between fetches, per node |
| `GF_WORK_THREADS` | `16` | |
| `GF_DOCUMENTS` | `false` | Whether linked documents are fetched and read |
| `GF_MAX_VERSIONS` | `10` | |

### Server

| Variable | Default | Description |
|---|---|---|
| `GF_SERVER_PORT` | `50080` | |
| `GF_SECURITY_ENABLED` | `true` | |
| `GF_USERS_FILE` | `config/api/users.xml` | Two roles, ADMIN and SUPPORT |
| `GF_TOKEN_SECRET` | generated | Set it so a token survives a restart |
| `GF_TOKEN_VALIDITY` | `8h` | |
| `GF_PROFILE` | `dev` | `dev` or `prod` |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=70` | No fixed `-Xmx`, so the heap follows the container limit |

---

## Volumes and ports

| | |
|---|---|
| `/app/data` | Everything. `system/` is working state, `user/` is what was crawled. **Use a volume** |
| `/app/config` | Mount your own `users.xml` or `application.yml` over the packaged ones |
| `50080` | The api and the console |
| `22000` | Cluster traffic between nodes. Not for the outside world |

---

## Memory

`GF_MEMORY` or `--memory` caps heap **and** off heap together, and both the ONNX embedding models
and a headless chromium live off heap.

| Limit | Vectors | A browser |
|---|---|---|
| 1g | OOMKilled after about 20 seconds | OOMKilled as the browser starts |
| 1.5g | OOMKilled while the image model loads | not reached |
| 2g | passes | passes |

The 1.5g row is the one worth knowing about, because a node looks fine at 1.5g until the image
model arrives: the text model loads, the node reports healthy, joins the cluster and takes work,
and the kill comes a minute later on a thread called `greenfinger-emb`.

Give a node 2g, and give it more if it is doing both at once.

That is the container's own limit. The host needs the sum of them: a warm node settles around
1.7GB, so three nodes and the console want **6GB of Docker memory**. When the host runs short it
is the host kernel that kills a jvm, not the container's limit, and docker then reports neither
an OOM nor a non zero exit. A container that restarts every minute or two with nothing in its log
is this, every time.

---

## Own your own accounts

Two roles, in `users.xml`. ADMIN can crawl, edit, replay and delete. SUPPORT can read every page
and is offered no button that writes.

```
docker run -d --init \
  -v ./users.xml:/app/config/api/users.xml:ro \
  -v greenfinger-data:/app/data \
  -p 50080:50080 \
  fredfeng033/greenfinger-api:latest
```

```xml
<users>
  <user name="admin"  password="..." roles="ADMIN"/>
  <user name="tester" password="..." roles="SUPPORT"/>
</users>
```

---

Apache License 2.0. Issues and pull requests at
https://github.com/paganini2008/greenfinger.
