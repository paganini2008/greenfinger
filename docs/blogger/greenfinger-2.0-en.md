---
title: "Greenfinger 2.0: one url in, a searchable archive out"
---

# Greenfinger 2.0: one url in, a searchable archive out

> **Crawl the whole site. Keep every page, every picture, every version.**
> **Search it by words, by meaning, or by describing a picture you remember.**
> **Add a node and it goes faster. Kill one and it keeps going.**
> **Nothing to install. Nothing to sign up for.**

A distributed web crawler for the JVM. One pass writes plain files, a full text index and a vector
collection. Every node runs the same jar, there is no coordinator to deploy, and the first crawl
needs no database, no search server and no API key.

![Watching a crawl, live](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-live.jpg)

*A rebuild in progress. v1 keeps answering searches while v2 is being written, and each node
reports what it pulled.*

---

## What problem does it solve

Most crawler code starts the same way. Someone needs the content of a site, writes a hundred lines
around an http client and an html parser, and it works. Then reality arrives. One advert link and
the crawler is downloading the rest of the web. The process is killed at page 40,000 and the queue
was in memory. Half the site turns out to be pdfs. Navigation and cookie banners get indexed
alongside the article, so every result looks the same.

And then storing it is a second project: files here, an index there, embeddings somewhere else, and
three pieces of glue that each fail differently. Greenfinger answers all of that in the product
rather than in your code, and the first crawl needs nothing running beside it.

---

## Quick start

Three nodes and a console, from the published images. Nothing to build.

``` shell
curl -O https://raw.githubusercontent.com/paganini2008/greenfinger/main/backend/greenfinger-docker/docker-compose.yml
echo "GF_TOKEN_SECRET=$(openssl rand -base64 32)" > .env
docker compose up -d
```

Then `http://localhost:9700`, `admin` and `admin123`. The key is not optional: tokens are signed
rather than kept in a session, so every node needs the same one or it rejects the others'
sign-ins. Give Docker 6GB for three nodes, or start one with `docker compose up -d api-1 ui`.

From source instead, and crawling a site in a single command:

``` shell
git clone https://github.com/paganini2008/greenfinger.git
cd greenfinger/backend && mvn clean install
cd ../deploy

./greenfinger-cli.sh --cluster=demo crawl --url=https://books.toscrape.com
```

Result:

``` text
Crawling 'books.toscrape.com' from https://books.toscrape.com

  pages kept    1,000      urls seen   28,411      1 in every 28
  images        3,204      indexed      1,000      elapsed  0h 4m 12s

Finished: reached maxFetchSize
Catalog 01a0c3d5-7d3d-7000-81f6-76057a403db8, version 0, now searchable.
```

Prefer the console:

``` shell
./run-local.sh                # nodes plus the page on http://localhost:9700
GF_NODES=3 ./run-local.sh     # three nodes sharing one crawl
./run-docker.sh               # the same, in containers
./run-local.sh stop
```

Sign in with `admin` and the password in `deploy/config/api/users.xml`. Seven example catalogs ship
with a fresh install, so there is something to crawl before you have picked anything.

![Catalogs](https://paganini2008.github.io/greenfinger/blogger/assets/catalogs.jpg)

---

## Requirements

| | Version | Needed for |
|---|---|---|
| **JDK** | 17 or later | Running anything |
| **Maven** | 3.9 or later | Building from source |
| **Node** | 20 or later | Building the web interface |
| **Docker** | any current | `run-docker.sh` only |

Everything else is optional and opt in one variable at a time: PostgreSQL, MySQL, SQL Server,
Oracle or SQLite instead of the H2 file, Elasticsearch instead of the embedded Lucene index, Qdrant
or Weaviate instead of the embedded vector store, MinIO or any S3 compatible store instead of local
disk, and Ollama or OpenAI instead of the local ONNX models.

A browser is needed only for the `playwright` and `selenium` extractors. The default `adaptive`
falls back to HtmlUnit, which is pure Java and needs nothing installed.

---

## How it works

### One pass, three outputs

``` text
                            ┌──────────────┐
                            │  file        │  local disk, MinIO, any S3 store
   one crawl  ───────────►  ├──────────────┤
                            │  index       │  Lucene embedded, Elasticsearch
                            ├──────────────┤
                            │  vector      │  Lucene embedded, Qdrant, Weaviate, ES
                            └──────────────┘
                                   │
                    replay ────────┘   rebuild index or vectors from the files,
                                       without fetching the site again
```

The file layer is always on, because the database keeps metadata only and the other two rebuild
from what it wrote. **Search never reads the database**, and that single constraint is where the
rest of the design comes from.

### What one page goes through

``` text
  url from the frontier
        │
        ├─ UrlPathAcceptor chain    domain, start url prefix, assets, robots.txt,
        │                           depth, path patterns. First refusal wins
        ├─ ExistingUrlPathFilter    seen before? RocksDB
        ├─ Extractor                plain http, browser only for unrendered shells
        ├─ ContentExtractor         the article, not the navigation
        │     └─ DocumentContentParser   when it is not html
        ├─ ContentDedupFilter       SHA-256 or SimHash
        ├─ OutputChannels           file, index, vector
        └─ new links ─────────────► back to the frontier, to whichever node owns them
```

Every drop is counted and named. That is why the monitor can say where 22,000 urls went rather than
only that 140 pages were kept.

### How the cluster shares one crawl

``` text
   node A ── finds /a/b ──► node C owns it ──► fetches, finds /a/b/c ──► node A owns it ──► ...

   no central queue        no leader in the fetch path        join or leave mid crawl
```

A crawl is a recursive function, and the only thing distribution changes is that the recursive call
crosses a process. There is no join, because a parent page does not care what its children found.

**Completion is decided by everyone.** Every node checks the shared counters against
`maxFetchSize` and `fetchDuration`, and the first to notice writes the reason. A leader that dies
mid crawl cannot leave a crawl that never ends.

![Members, roles, transport, health](https://paganini2008.github.io/greenfinger/blogger/assets/cluster.jpg)

---

## Code examples

### Crawl, update, rebuild, replay

**Input.** A catalog id, and a verb.

``` shell
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id>            # from the start url
./greenfinger-cli.sh --cluster=nightly update  --id=<id>            # urls that appeared since
./greenfinger-cli.sh --cluster=nightly merge   --id=<id>            # and revisit what is held
./greenfinger-cli.sh --cluster=nightly rebuild --id=<id>            # new version, old one served
./greenfinger-cli.sh --cluster=nightly resume  --id=<id>            # continue after a pause
./greenfinger-cli.sh --cluster=nightly replay  --id=<id> --layers=index+vector
```

**Output.**

| Verb | Version | Fetches | Writes |
|---|---|---|---|
| `crawl` | current | from the start url | everything it saves |
| `update` | current | only urls not seen before | new pages only |
| `merge` | current | new urls **and** pages already held | only the pages that changed |
| `rebuild` | a new one | the whole site again | the new version, old one keeps serving |
| `resume` | current | what is left on the frontier | as the interrupted run would have |
| `replay` | a named one | nothing | rebuilds an output from what is stored |

`replay` is the one to remember. Your Elasticsearch was down for an hour, you changed the analyzer,
or you decided six months in that you want embeddings after all. None of those need the site to be
polite to you a second time.

### Search three ways

**Input.** One box, three modes, from the console or the prompt.

``` text
greenfinger:> search --query="bread"
greenfinger:> search --query="what happens when a star runs out of fuel" --mode=meaning
greenfinger:> search --query="a bright spiral galaxy against black sky" --mode=pictures
```

**Output.** Words goes to the index, so this is exact terms with the matches highlighted.

![Searching by words](https://paganini2008.github.io/greenfinger/blogger/assets/search-words.jpg)

Meaning goes to the text vectors. The top answer below is a supernova remnants page that never
contains the sentence that was typed.

![Searching by meaning](https://paganini2008.github.io/greenfinger/blogger/assets/search-meaning.jpg)

Pictures goes to the image vectors, matched against the picture itself rather than the filename or
the alt text.

![Finding pictures by describing them](https://paganini2008.github.io/greenfinger/blogger/assets/search-pictures.jpg)

The same thing at the prompt:

``` text
╭────────┬──────────────────────────────────────────┬────────────────────────────────────────────────╮
│ Score  │ Title                                    │ Url                                            │
├────────┼──────────────────────────────────────────┼────────────────────────────────────────────────┤
│ 0.9356 │ APOD: 2008 June 4 - Chasing the ISS      │ https://apod.nasa.gov/apod/ap080604.html       │
│ 0.9317 │ APOD Index - Nebulae: Supernova Remnants │ https://apod.nasa.gov/apod/supernova_remnants… │
│ 0.9289 │ APOD Index - Stars: Binary Stars         │ https://apod.nasa.gov/apod/binary_stars.html   │
╰────────┴──────────────────────────────────────────┴────────────────────────────────────────────────╯
```

The embedding models run locally and need no account: `multilingual-e5-small` for text and
`SigLIP 2` for images, both ONNX, both preloaded at startup on a background thread.

### Add a pdf parser

**Input.** A site that links pdfs.

**Code.** Every default component is `@ConditionalOnMissingBean`, so publishing a bean is the whole
registration. There is no plugin registry and no ordering property.

``` java
@Bean
DocumentContentParser pdfParser() {
    return new DocumentContentParser() {
        public Set<String> fileTypes() {
            return Set.of("pdf");
        }

        public String extractText(byte[] content, String url, Charset encoding) throws Exception {
            return new Tika().parseToString(new ByteArrayInputStream(content));
        }
    };
}
```

**Output.** With `GF_DOCUMENTS=true` and `GF_DOCUMENT_TYPES=pdf`, pdfs linked from a crawled page
now contribute text to the index and the vectors. Collecting the links was always happening and
costs nothing. Fetching and reading them is what you just switched on.

The same shape works for every decision the crawler makes:

| Interface | Decides | What ships |
|---|---|---|
| `Extractor` | How a page is fetched | restclient, htmlunit, playwright, selenium, adaptive |
| `UrlPathAcceptor` | Whether a link is followed | Domain, start url, assets, robots.txt, depth, patterns |
| `ContentDedupFilter` | Whether two urls are the same page | sha256, simhash |
| `ContentExtractor` | The text inside a page | Link density boilerplate removal |
| `DocumentContentParser` | Text out of a non html file | text, markdown, csv |
| `CompletionChecker` | When the crawl is over | Saved count, elapsed time |
| `OutputChannel` | Where results are written | file, index, vector |
| `BlobStore` | Pages and images as bytes | local, minio |
| `Searcher` / `VectorStore` | Full text, and meaning | lucene, elasticsearch, qdrant, weaviate |
| `EmbeddingClient` | What turns text into a vector | local ONNX, ollama, openai |

### Embed it in your own application

**Input.** A Spring Boot application of your own.

``` java
@EnableGreenfingerServer
@SpringBootApplication
public class MyApplication { }
```

**Output.** The whole REST api, the login and the page, inside your process. It is explicit rather
than auto configured, because sitting on a classpath is not a reason to open RocksDB and take a
crawl permit. To drive a crawl without the server, `CrawlerLauncher.crawl(catalogId, onReady)`
returns a `CrawlerEngine.Result` with the counters and the reason it ended.

---

## Configuration

### Per catalog

One url is required. Everything else has a default chosen to give a useful crawl of a site you know
nothing about. Run `options` at the prompt for the live values.

| Property | Default | Description |
|---|---|---|
| `url` | required | http:// or https://. The identity and the outer boundary |
| `name` | the domain | Unique text |
| `cat` | `other` | One of nine categories, used to filter and group |
| `start-url` | `= url` | Where fetching begins. Must sit under `url` |
| `sitemap-url` | empty | Empty discovers it from robots.txt |
| `include` | `**.<domain>/**` | Ant path pattern, comma for several |
| `exclude` | empty | Ant path pattern, comma for several |
| `encoding` | `UTF-8` | Page charset, when the server is wrong about it |
| `extractor` | `adaptive` | adaptive, restclient, htmlunit, playwright, selenium |
| `max-size` | `10000` | Saved pages before the crawl stops |
| `depth` | `-1` | Link depth. -1 for no limit |
| `duration` | `30` | Minutes before the crawl stops |
| `interval` | `1000` | Milliseconds between fetches, per node |
| `retry` | `1` | Retries per url |
| `images` | `true` | Whether pictures are fetched at all |
| `output-types` | `file` | `file+index+vector`. file is always on |
| `content` | `text+image` | What reaches the index and the vectors |
| `max-versions` | `10` | Versions kept before the oldest is pruned |

### Node wide, in `deploy/.env`

| Property | Default | Description |
|---|---|---|
| `GF_DB_URL` | an H2 file | PostgreSQL, MySQL, SQL Server, Oracle, SQLite |
| `GF_INDEX_PROVIDER` | `lucene` | Or `elasticsearch`, with `GF_ES_URIS` |
| `GF_VECTOR_STORE` | `lucene` | Or `elasticsearch`, `qdrant`, `weaviate` |
| `GF_FILE_TARGET` | `local` | Or `minio`. Object keys match the local paths exactly |
| `GF_EMBEDDING_PROVIDER` | `local` | Or `ollama`, `openai` |
| `GF_LUCENE_ANALYZER` | `standard` | `standard`, `smartcn` or `cjk` |
| `GF_ES_ANALYZER` | `standard` | `ik_max_word` needs the analysis-ik plugin |
| `GF_DOCUMENTS` | `false` | Whether linked documents are fetched and read |
| `GF_CLUSTER_TRANSPORT` | `NETTY` | Or the built in `NIO` |
| `GF_NODES` | `1` | How many processes a launcher starts |
| `GF_MEMORY` | `2g` | Per container. Caps heap **and** off heap together |

Analyzers matter more than they look. The standard analyzer cuts Chinese into single characters,
and a Chinese analyzer drops French characters outright, so `chaîne` comes back as `cha î ne`.

---

## Performance

Our own numbers only. There is no comparison here against other crawlers, because we have not run
the controlled experiment that would make one honest.

**Test environment.** Apple M2 Max, 12 cores, 32 GB, macOS 26.3.1, JDK 17.0.12. Two nodes started
by `run-local.sh`, each `-Xms256m -Xmx2g`. H2 file, embedded Lucene index, embedded Lucene vector
store, pages and images on local disk, local ONNX embeddings. Home broadband. Polite crawling, so
`fetchInterval` is the floor on throughput rather than the hardware.

| Site | Nodes | Kept | Images | Urls seen | Elapsed |
|---|---|---|---|---|---|
| apod.nasa.gov | 2 | 46 pages | 16 | 3,773 | 1m 41s |
| simplefood.blog | 2 | 140 pages | 2,489 | 22,857 already known | 5m 01s |
| books.toscrape.com | 1 | 1,000 pages | 3,204 | 28,411 | 4m 12s |

The gap between urls seen and pages kept is the point rather than an inefficiency. On simplefood
22,340 urls were filtered out by the boundary rules and 561 were duplicate content, which is work
the outputs never had to do.

**How evenly the work spreads.**

``` text
two nodes, apod.nasa.gov
  node 18bbe738   76 handled   51%   26 pages   9 images
  node 96c82b18   74 handled   49%   19 pages   7 images

three nodes, a 61 page site, one shared PostgreSQL
  dispatched 61      handled 66      saved 61
  node-1: 18 fetches   node-2: 18   node-3: 25     each page exactly once
```

**Everything else we measured.**

| | Measured |
|---|---|
| Word search, embedded Lucene, 151 documents | 38 matches in 16 ms |
| Cluster wire format, one `CrawlTask` | JSON 327 bytes, encode and decode 1,394 ns |
| Both ONNX models preloaded | 4.47 s, on a background thread after the app is ready |
| Idle RSS with both models loaded | 1.77 to 1.88 GB |
| Image vector duplication, before and after the fix | 43× then 1.00× |
| Container memory, vectors or a browser | 1g is OOMKilled, 2g passes, and they want separate runs |
| Tests | 133 classes, 1,098 methods, 80% line coverage gate on four modules |

---

## Limitations and trade-offs

- **The embedded index and vector store cost the extracted text once per node.** Fine for one or
  two nodes. For a real cluster, point it at Elasticsearch or Qdrant, which the startup report
  recommends out loud.
- **Replication is asynchronous.** Immediately after a crawl, one node can answer a search before
  another has caught up. Seconds, not minutes.
- **A node killed mid crawl leaves `runningState` set.** The registry says nothing is running, the
  row says otherwise, and `interrupt` has nothing to interrupt. On the list to fix.
- **Article extraction leaves some template text in the chunks.** Share buttons and footer credits
  turn up in search snippets.
- **Politeness is the throughput ceiling.** `fetchInterval` defaults to a second per node, and that
  is deliberate. This is not the tool for hammering one host as fast as it will answer.
- **One crawl at a time per cluster.** Two crawls means two clusters, which is a name and a port.
- **Pdf, Word and Excel need a bean.** Each is another dependency with its own licence and its own
  appetite for memory, so the choice belongs to the application.

---

## Summary

1. **One url in, and you get a whole site on disk, a full text index and vectors**, from one pass.
2. **Nothing to provision.** H2, an embedded Lucene index and local ONNX models are the defaults.
   No database, no search server, no API key, no model download by hand.
3. **Decentralised by default.** Every node runs the same jar. No coordinator, no central queue, no
   leader in the fetch path.
4. **Scaling is starting another process** and pointing it at the same cluster name, including
   joining a crawl already running.
5. **Replay rebuilds an output from the files**, so a lost index or a changed analyzer never means
   crawling the site again.
6. **Versions make a rebuild safe.** The old version keeps answering searches, and an interrupted
   rebuild publishes nothing.
7. **Three ways to search**, including finding a picture by describing it, with the models running
   locally.
8. **Every decision the crawler makes is an interface** with a shipped default that a bean replaces.
9. **The crawl cannot leave the site.** Two boundary rules, neither of which can be switched off.
10. **Apache 2.0, JDK 17, Spring Boot 4.1.**

---

Source, issues and full documentation: [github.com/paganini2008/greenfinger](https://github.com/paganini2008/greenfinger)
