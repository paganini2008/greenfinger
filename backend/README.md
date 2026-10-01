# Greenfinger backend

**Four Maven modules, two of which are executable jars.**

What the crawler is and how to run it is in the [root README](../README.md). This is how it is
built, tested and extended.

``` text
  greenfinger-shell                          greenfinger-api
  prompt + one line crawler                  REST, security, serves the page
  no servlet container, no login                       │
          │                                            │
          └──────────────► greenfinger-cluster ◄───────┘
                           dispatch, shared counters, replication,
                           leader gateway
                                   │
                                   ▼
                           greenfinger-core
                           engine, pluggable components, three output
                           channels, vector stores, embeddings, JPA,
                           and the services both front ends drive
```

## Table of contents

[Modules](#modules) · [Architecture](#architecture) · [Requirements](#requirements) · [Quick start](#quick-start) · [Examples](#examples) · [Configuration](#configuration) · [Performance](#performance) · [Documentation](#documentation) · [House rules](#house-rules)

---

## Modules

| Module | Holds | Main classes | Packaged |
|---|---|---|---|
| `greenfinger-core` | The crawler. Engine, frontier, extractors, dedup, the three output channels, vector stores, embeddings, persistence, and the services both front ends drive. Knows nothing about clusters or http servers | 182 | jar |
| `greenfinger-cluster` | Distribution on top of openspreader. Task dispatch, shared counters, replication, leader gateway | 37 | jar |
| `greenfinger-api` | The REST api, security, and the server that serves the page | 25 | executable jar |
| `greenfinger-shell` | The prompt and the one line crawler. Depends on core alone | 24 | executable jar |

Only the last two are runnable, and only they carry an `application.yml`. Packaging puts both jars
and all the configuration into `deploy/`, which is a build output and is not in git.

**Both front ends depend on core, and neither passes through the other.** The service layer
therefore lives in core, and the prompt and the page drive the same one. The command line needs no
servlet container and should not inherit the web layer's dependencies.

**Two enabling annotations, neither auto configured.** Sitting on a classpath is not a reason to
open RocksDB and take a crawl permit.

| Annotation | In | Gives |
|---|---|---|
| `@EnableGreenfingerCrawler` | core | Engine, outputs, persistence, services |
| `@EnableGreenfingerServer` | api | All of that, plus REST, login and the page, in a web environment |

---

## Architecture

### How a crawl runs

``` text
CrawlerLauncher.crawl(catalogId, onReady)
        │
        ├─ CatalogDetailsService              loads the catalog, resolves every setting
        ├─ WebCrawlerComponentFactory         builds acceptors, filters, extractor, frontier,
        │                                     dedup, completion checkers, output channels
        ├─ DefaultWebCrawlerExecutionContext  holds them for this run, destroyed at the end
        │
        └─ CrawlerEngine
              worker pool ──► CrawlTask per url
                                │
                                ├─ PageParser        links, images, document links
                                ├─ ContentExtractor  article text
                                ├─ CompositeOutputChannel ──► file, index, vector
                                └─ dispatch new urls ──► CrawlCoordinator
```

`CrawlCoordinator` has two implementations. On one process it is a local call. On a cluster it is
one openspreader task per url, sent to whichever node owns it, so the recursion spreads itself.

Counters, the completion flag and the reason a crawl ended live in the cluster's replicated cache.
Every node runs the `CompletionChecker`s against those shared counters, which is why finishing is
not the leader's decision.

### Where the seams are

| Package | Holds |
|---|---|
| `core.engine` | `CrawlerEngine`, `CrawlTask`, `CrawlFrontier`, `ContentExtractor` |
| `core.component.acceptor` | The `UrlPathAcceptor` chain, in `Ordered` sequence |
| `core.component.extractor` | The five `Extractor` implementations and the rendering detector |
| `core.component.dedup` | RocksDB url dedup, SHA-256 and SimHash content dedup |
| `core.component.state` | Counters and the `CompletionChecker`s |
| `core.document` | `DocumentContentParser` and the plain text one that ships |
| `output` | `OutputChannel`, plus the `blob`, `index` and `vector` subpackages |
| `output.vector` | Chunking, `EmbeddingClient`, and the four `VectorStore` implementations |
| `service` | `CrawlerLauncher`, `CatalogAdminService`, `ReplayService`, `DeletionService` |
| `utils` | `HttpUtils` (one shared HttpClient 5), `JsonUtils` (one shared ObjectMapper) |

Every default component is `@ConditionalOnMissingBean`, so an application replaces one by
publishing a bean. The full catalogue is in
[docs/developer-guide.md](../docs/developer-guide.md).

### Cluster internals

| Class | Does |
|---|---|
| `CrawlCoordinator` | One url dispatched is one task for the owning node |
| `GlobalStateManager` | The run's counters, per node and totalled, in the replicated cache |
| `ReplicatedBlobStore` / `ReplicatedIndexChannel` / `ReplicatedVectorStore` | Copy writes to the other nodes, for the stores that are per node |
| `ReplicatedCatalogStore` / `CatalogCatchUp` | One writer for catalog rows, and the catch up that makes a node match the leader |
| `LeaderGateway` | Where an administrative write goes. On a cluster of one it is a plain method call |
| `DeletionBroadcast` | Tells peers to remove what does not replicate itself |
| `ClusterStartupReport` | Asserts at boot that the configured transport and cache are the ones in use |

Seven channels carry the traffic:

``` text
   greenfinger.crawl     unicast, consistent hash on url       buffered   many consumers
   greenfinger.record    multicast, excluding self             buffered   1 consumer
   greenfinger.rocksdb   multicast, excluding self             buffered   1 consumer
   greenfinger.blob      multicast, excluding self             buffered   1 consumer
   greenfinger.search    multicast, excluding self             buffered   1 consumer
   greenfinger.control   multicast, including self          UNBUFFERED    acted on first
   greenfinger.leader    request and reply, with a request id  buffered   1 consumer
```

The control channel is unbuffered on purpose. A stop has to take effect now, and queueing it behind
the ten thousand urls that are creating the traffic is the one place buffering costs more than it
saves.

Transport is openspreader's: NIO built in, Netty when the jar is present, which it is because
`netty-transport` and `netty-handler` are declared dependencies. Configuring Netty without the jar
makes spreader fall back silently, which is what the startup report exists to catch.

---

## Requirements

| | Version | Needed for |
|---|---|---|
| **JDK** | 17 or later | Compiling and running |
| **Maven** | 3.9 or later | The build |
| **Docker** | any current | Only the container regression, not the build |

The unit and integration tests need **nothing running**. H2, an embedded Lucene index and a
temporary directory are the defaults, and everything that speaks http is tested against a stub
server rather than a real Elasticsearch, Qdrant, Weaviate or MinIO.

---

## Quick start

``` shell
mvn -o clean verify                                    # what CI runs
mvn -o clean package -DskipTests -Djacoco.skip=true    # just the jars, into deploy/
```

Expected output of a clean `verify`:

``` text
[INFO] greenfinger ........................................ SUCCESS
[INFO] greenfinger-core ................................... SUCCESS
[INFO] greenfinger-cluster ................................ SUCCESS
[INFO] greenfinger-api .................................... SUCCESS
[INFO] greenfinger-shell .................................. SUCCESS
[INFO] BUILD SUCCESS
```

`verify` is the gate. **JaCoCo line coverage must be at least 80% in every module**, and the build
fails below it rather than warning.

For iterating, build one module and skip the gate:

``` shell
mvn -o -pl greenfinger-core install -DskipTests -Djacoco.skip=true
```

---

## Examples

### Run one test

**Input.** A test class name.

``` shell
mvn -o -pl greenfinger-core test -Dtest=CrawlerEngineTest
mvn -o -pl greenfinger-cluster -am test -Dtest=ClusterMessagingTest \
    -Dsurefire.failIfNoSpecifiedTests=false
```

**Output.** `-am` matters for anything but core. Without it Maven resolves core from the local
repository rather than from the reactor, and you test the jar from the last time somebody ran
`install`.

### Regenerate the schema scripts

**Input.** An entity you changed.

``` shell
mvn -o -pl greenfinger-core test -Dtest=SchemaScriptsTest \
    -Dsurefire.failIfNoSpecifiedTests=false
```

**Output.** `docs/sql/` holds one script per supported database, **generated from the entities**
rather than written by hand, because a DDL file kept separately from the code it describes drifts.
A modified file in the diff afterwards means the schema moved, and somebody has to decide what
existing databases do about it. See
[docs/sql/schema-scripts.md](../docs/sql/schema-scripts.md).

### Test the wiring rather than the behaviour

**Input.** A change to a protocol, a message, or a Spring context.

**Code.** A loopback gateway that serialises a request, deserialises it, calls the handler and
serialises the answer back.

**Output.** Protocol mistakes surface in fractions of a second. A real two node run takes minutes
to reach the same line, and most of what it finds is wiring rather than behaviour. Keep the manual
end to end run for cross process behaviour that nothing else can show: dispatch across nodes, the
live dashboard, a real site.

---

## Configuration

Build time only. Everything a running node reads is in the
[root README](../README.md#configuration).

| | Where | What it is |
|---|---|---|
| Secrets | `backend/.env` | Read by `spring.config.import` and by the launchers. Never in `config/`, never in the repository |
| Packaged config | `greenfinger-shell/src/main/resources/config/` | Copied into `deploy/config/` by the build |
| Launcher scripts | `greenfinger-shell/.../bin/`, `greenfinger-api/.../bin/` | Copied into `deploy/` by the build |
| Coverage gate | the parent `pom.xml` | JaCoCo, 80% line coverage, four modules |

| Maven property | Effect |
|---|---|
| `-DskipTests` | Skip the tests |
| `-Djacoco.skip=true` | Skip the coverage gate |
| `-o` | Offline, which is what you want for an iteration loop |
| `-pl <module> -am` | One module and the ones it depends on |

---

## Performance

**Code size.**

| Module | Main classes | Lines | Test classes |
|---|---|---|---|
| `greenfinger-core` | 182 | 26,208 | 87 |
| `greenfinger-cluster` | 37 | 6,464 | 32 |
| `greenfinger-api` | 25 | 2,993 | 17 |
| `greenfinger-shell` | 24 | 4,107 | 20 |

**Tests.** 133 classes and 1,098 test methods, in four kinds:

| Kind | What it does |
|---|---|
| unit | The bulk of them, no Spring context |
| integration (`*IntegrationTest`) | A real Spring context with H2 and a temporary directory |
| stub server | Everything that speaks http, so Elasticsearch, Qdrant, Weaviate and MinIO behaviour is asserted without those servers running |
| cluster | Several real nodes in one jvm on a private port range |

**The jars are large.** Around 530 MB each, because they are fat jars carrying DJL, the ONNX
runtime, Playwright, Selenium, HtmlUnit, four vector store clients and six jdbc drivers. That is
the price of an install that needs nothing provisioned. An application embedding
`greenfinger-core` as a library brings in only what it uses.

**The schema scripts were verified by execution**, not by inspection. Every one of the six was run
against a real empty database:

| Database | Version it ran on |
|---|---|
| SQLite | 3 |
| H2 | 2.4.240 |
| PostgreSQL | 16 |
| MySQL | 9.6.0 |
| SQL Server | 2022 RTM-CU26 |
| Oracle | AI Database 26ai Free 23.26.3 |

---

## Documentation

| | |
|---|---|
| [Root README](../README.md) | What the crawler is and how to run it |
| [Developer guide](../docs/developer-guide.md) | Every seam, with a worked example |
| [Design](../docs/design-2.0.md) | How the system is shaped and why |
| [Command line reference](../docs/cli-reference.md) | Every verb and every option |
| [Schema scripts](../docs/sql/schema-scripts.md) | One per database, generated from the entities |

---

## House rules

- **Secrets live in `backend/.env`.** Never in `config/`, never in the repository.
- **Method names start with a verb or a preposition.** Utility classes end in `Utils` and live in
  `com.github.greenfinger.utils`.
- **Imports go at the top of the file.** No inline fully qualified names.
- **Reuse the shared objects.** `HttpUtils.getClient()` and `JsonUtils.MAPPER`. Per request
  differences belong on the request, not on a second client.
- **Comments say why, in one to three lines.** What happened and what was learned goes in
  `change_logs/`, not in the source.
- **`history/` is 1.x**, kept for reference. Do not delete it and do not build it.
- **openspreader, spreader and cockatoo** are dependencies read as source when needed, never
  modified.

A change has to pass `mvn -o clean verify`, which fails below 80% line coverage, so a change that
adds a branch adds the test for it.

Apache License 2.0. See [LICENSE](../LICENSE).
