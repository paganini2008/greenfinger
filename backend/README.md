# Greenfinger backend

Four Maven modules. What the crawler is and how to use it is in the [root README](../README.md).
This is how it is built.

## Modules

``` text
greenfinger-shell ──┐                    greenfinger-api ──┐
  prompt, one line  │                      REST, security  │
  crawler           │                      serves the page │
                    ├──► greenfinger-cluster ◄─────────────┘
                    │      dispatch, shared counters, replication, leader gateway
                    │                │
                    └────────────────┴──► greenfinger-core
                                            engine, components, outputs, persistence
```

| Module | What it is | Packaged |
|---|---|---|
| `greenfinger-core` | The crawler. Engine, frontier, extractors, dedup, the three output channels, the services both front ends drive. Knows nothing about clusters or http servers | jar |
| `greenfinger-cluster` | Distribution on top of openspreader. Task dispatch, shared counters, replication, control messages | jar |
| `greenfinger-shell` | The prompt and the one line crawler. Depends on core alone, starts no servlet container and has no login | executable jar |
| `greenfinger-api` | The REST api, security, and the server that serves the page | executable jar |

Only the last two are runnable and only they carry an `application.yml`. Packaging puts both jars
and all the configuration into `deploy/`, which is a build output and is not in git.

## How a crawl runs

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

## Where the seams are

| Package | Holds |
|---|---|
| `core.engine` | `CrawlerEngine`, `CrawlTask`, `CrawlFrontier`, `ContentExtractor` |
| `core.component.acceptor` | The `UrlPathAcceptor` chain, in `Ordered` sequence |
| `core.component.extractor` | The five `Extractor` implementations and the rendering detector |
| `core.component.dedup` | RocksDB url dedup, SHA-256 and SimHash content dedup |
| `core.component.state` | Counters and the `CompletionChecker`s |
| `core.document` | `DocumentContentParser` and the plain text one that ships |
| `output` | `OutputChannel`, plus `blob`, `index` and `vector` subpackages |
| `output.vector` | Chunking, `EmbeddingClient`, and the four `VectorStore` implementations |
| `service` | `CrawlerLauncher`, `CatalogAdminService`, `ReplayService`, `DeletionService` |
| `utils` | `HttpUtils` (one shared HttpClient 5), `JsonUtils` (one shared ObjectMapper) |

Every default component is `@ConditionalOnMissingBean`, so an application replaces one by
publishing a bean. The catalogue of interfaces is in
[docs/developer-guide.md](../docs/developer-guide.md).

## Cluster internals

| Class | Does |
|---|---|
| `CrawlCoordinator` | One url dispatched is one task for the owning node |
| `GlobalStateManager` | The run's counters, per node and totalled, in the replicated cache |
| `ReplicatedBlobStore` / `ReplicatedIndexChannel` / `ReplicatedVectorStore` | Copy writes to the other nodes, for the stores that are per node |
| `ReplicatedCatalogStore` / `CatalogCatchUp` | One writer for catalog rows, and the catch up that makes a node match the leader |
| `LeaderGateway` | Where an administrative write goes. On a cluster of one it is a plain method call |
| `DeletionBroadcast` | Tells peers to remove what does not replicate itself |
| `ClusterStartupReport` | Asserts at boot that the configured transport and cache are the ones in use |

Transport is openspreader's, NIO built in, Netty when the jar is present, which it is because
`netty-transport` and `netty-handler` are declared dependencies. Configuring Netty without the jar
makes spreader fall back silently, which is what the startup report exists to catch.

## Build

``` shell
mvn -o clean verify                                    # what CI runs
mvn -o clean package -DskipTests -Djacoco.skip=true    # just the jars, into deploy/
mvn -o -pl greenfinger-core install -DskipTests -Djacoco.skip=true   # seconds, for iterating
```

`verify` is the gate. **JaCoCo line coverage must be at least 80% in every module** and the build
fails below it rather than warning.

## Tests

Around 860, in four kinds:

| Kind | What it does |
|---|---|
| unit | The bulk of them, no Spring context |
| integration (`*IntegrationTest`) | A real Spring context with H2 and a temporary directory |
| stub server | Everything that speaks http, so Elasticsearch, Qdrant, Weaviate and MinIO behaviour is asserted without those servers running |
| cluster | Several real nodes in one jvm on a private port range |

``` shell
mvn -o -pl greenfinger-core test -Dtest=CrawlerEngineTest
mvn -o -pl greenfinger-cluster -am test -Dtest=ClusterMessagingTest -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` matters for anything but core. Without it Maven resolves core from the local repository
rather than from the reactor, and you test the jar from the last time somebody ran `install`.

Wiring bugs belong in tests, not in a manual run. A loopback gateway that serialises a request,
deserialises it, calls the handler and serialises the answer back catches protocol mistakes in
fractions of a second, where a real two node run takes minutes to reach the same line.

## Database schema

`docs/sql/` holds one script per supported database, **generated from the entities** by
`SchemaScriptsTest` rather than written by hand, because a DDL file kept separately from the code
it describes drifts.

``` shell
mvn -o -pl greenfinger-core test -Dtest=SchemaScriptsTest -Dsurefire.failIfNoSpecifiedTests=false
```

A modified file in the diff afterwards means the schema moved, and somebody has to decide what
existing databases do about it. See [docs/sql/schema-scripts.md](../docs/sql/schema-scripts.md).

## End to end regression

The unit tests do not start a node. The regression does. It starts a server, creates a catalog
through the api, crawls it the way the front end does, waits for it to finish by itself, searches
it, and empties it, against every combination the project supports:

| Axis | Covered |
|---|---|
| Database | H2, SQLite, MySQL, PostgreSQL, SQL Server, Oracle |
| Blob store | local disk, MinIO |
| Index | Lucene, Elasticsearch |
| Vectors | Lucene, Elasticsearch, Qdrant, Weaviate |
| Topology | one node, three nodes on one shared database, three containers |

Those scripts live outside the repository.

## House rules

- Secrets live in `backend/.env`, read by `spring.config.import` and by the launchers. Never in
  `config/`, never in the repository.
- Method names start with a verb or a preposition. Utility classes end in `Utils` and live in
  `com.github.greenfinger.utils`.
- Imports go at the top of the file. No inline fully qualified names.
- Reuse the shared objects: `HttpUtils.getClient()` and `JsonUtils.MAPPER`. Per request differences
  belong on the request, not on a second client.
- Comments say why in one to three lines. What happened and what was learned goes in
  `change_logs/`, not in the source.
- `history/` is 1.x, kept for reference. Do not delete it and do not build it.
- openspreader, spreader and cockatoo are dependencies read as source when needed, never modified.
