# Greenfinger — backend

Four Maven modules. What the crawler is and how to use it is in the [root README](../README.md);
this is for working on it.

## The modules

| Module | What it is | Packaged |
|---|---|---|
| `greenfinger-core` | The crawler: engine, frontier, extractors, dedup, the three output channels, the services. Knows nothing about clusters or http. | jar (library) |
| `greenfinger-cluster` | Distribution, on top of openspreader: task dispatch, shared counters, replication, control messages. | jar (library) |
| `greenfinger-shell` | The prompt and the one-shot command line. Depends on core alone — it starts no servlet container and has no login. | executable jar |
| `greenfinger-api` | The REST api, security, and the server's own configuration. | executable jar |

Only the last two are runnable, and only they carry an `application.yml`. Packaging puts both jars
and all the configuration into `deploy/`, which is a build output and is not in git.

## Build

``` shell
mvn -o clean verify          # compile, test, and check coverage — what CI would run
mvn -o clean package -DskipTests -Djacoco.skip=true   # just the jars, into deploy/
```

`verify` is the gate: **JaCoCo line coverage must be at least 80% in every module**, and the build
fails below it rather than warning. A change that adds a branch adds the test for it.

## Tests

Around 860, in four kinds:

- **unit**, the bulk of them, no Spring context
- **integration** (`*IntegrationTest`), a real Spring context with H2 and a temporary directory
- **stub-server** tests for everything that speaks http — Elasticsearch, Qdrant, Weaviate, MinIO —
  so their behaviour is asserted without those servers running
- **cluster** tests, several real nodes in one jvm on a private port range

``` shell
mvn -o -pl greenfinger-core test -Dtest=CrawlerEngineTest
mvn -o -pl greenfinger-cluster -am test -Dtest=ClusterMessagingTest -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` matters for anything but core: without it Maven resolves core from the local repository
rather than from the reactor, and you test the jar from last time somebody ran `install`.

## Database schema

`docs/sql/` holds one script per supported database, **generated from the entities** by
`SchemaScriptsTest` — not written by hand, because a DDL file kept separately from the code it
describes drifts. Change an entity and run:

``` shell
mvn -o -pl greenfinger-core test -Dtest=SchemaScriptsTest -Dsurefire.failIfNoSpecifiedTests=false
```

A modified file in the diff afterwards means the schema moved, and somebody has to decide what
existing databases do about it. See [docs/sql/schema-scripts.md](../docs/sql/schema-scripts.md).

## End-to-end regression

The unit tests do not start a node. The regression does: it starts a server, creates a catalog
through the api, crawls it the way the front end does, waits for it to finish by itself, searches
it, and empties it — against every combination of database, blob store and index the project
supports: H2, SQLite, MySQL, PostgreSQL, SQL Server and Oracle; local disk and MinIO; Lucene and
Elasticsearch; Lucene, Elasticsearch, Qdrant and Weaviate for vectors. It also runs three nodes
against one shared database, and three containers. Those scripts live outside the repository.

## House rules

- Secrets live in `backend/.env`, read by `spring.config.import` and by the launchers. Never in
  `config/`, never in the repository.
- `history/` is 1.x, kept for reference. Do not delete it and do not build it.
- openspreader, spreader and cockatoo are dependencies read as source when needed, never modified.
