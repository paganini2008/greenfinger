# Greenfinger 2.0: one url in, a searchable archive out

**Crawl the whole site. Keep every page, every picture, every version.
Search it by words, by meaning, or by describing a picture you remember.
Add a node and it goes faster. Kill one and it keeps going.
Nothing to install. Nothing to sign up for.**

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

```
git clone https://github.com/paganini2008/greenfinger.git
cd greenfinger/backend && mvn clean install
cd ../deploy

./greenfinger-cli.sh --cluster=demo crawl \
    --url=https://books.toscrape.com
```

Result:

```
Crawling 'books.toscrape.com' from https://books.toscrape.com

  pages kept    1,000      urls seen   28,411      1 in every 28
  images        3,204      indexed      1,000      elapsed  0h 4m 12s

Finished: reached maxFetchSize
Catalog 01a0c3d5-7d3d-7000-81f6-76057a403db8, version 0, now searchable.
```

Prefer the console:

```
./run-local.sh                # nodes plus the page on :9700
GF_NODES=3 ./run-local.sh     # three nodes sharing one crawl
./run-docker.sh               # the same, in containers
./run-local.sh stop
```

Sign in with `admin` and the password in `deploy/config/api/users.xml`. Seven example catalogs ship
with a fresh install, so there is something to crawl before you have picked anything.

![Catalogs](https://paganini2008.github.io/greenfinger/blogger/assets/catalogs.jpg)

---

## Requirements

- **JDK 17 or later**, to run anything.
- **Maven 3.9 or later**, to build from source.
- **Node 20 or later**, to build the web interface.
- **Docker**, any current version, for `run-docker.sh` only.

Everything else is optional and opt in one variable at a time: PostgreSQL, MySQL, SQL Server,
Oracle or SQLite instead of the H2 file, Elasticsearch instead of the embedded Lucene index, Qdrant
or Weaviate instead of the embedded vector store, MinIO or any S3 compatible store instead of local
disk, and Ollama or OpenAI instead of the local ONNX models.

A browser is needed only for the `playwright` and `selenium` extractors. The default `adaptive`
falls back to HtmlUnit, which is pure Java and needs nothing installed.

---

## How it works

### One pass, three outputs

```
                        ┌─────────────┐
                        │  file       │  disk, MinIO, any S3
 one crawl  ──────────► ├─────────────┤
                        │  index      │  Lucene, Elasticsearch
                        ├─────────────┤
                        │  vector     │  Lucene, Qdrant,
                        │             │  Weaviate, ES
                        └─────────────┘
                              │
             replay ──────────┘  rebuild index or vectors
                                 from the files, without
                                 fetching the site again
```

- **file** holds the page as fetched, the article text and every image.
- **index** holds the full text, searchable by words.
- **vector** holds text chunks and image embeddings.

The file layer is always on, because the database keeps metadata only and the other two rebuild
from what it wrote. **Search never reads the database**, and that single constraint is where the
rest of the design comes from.

### What one page goes through

```
url from the frontier
     │
     ├─ UrlPathAcceptor chain
     │    domain, start url prefix, assets, robots.txt,
     │    depth, path patterns. First refusal wins
     │
     ├─ ExistingUrlPathFilter     seen before? RocksDB
     │
     ├─ Extractor                 plain http, a browser only
     │                            for unrendered shells
     │
     ├─ ContentExtractor          the article, not the navigation
     │    └─ DocumentContentParser   when it is not html
     │
     ├─ ContentDedupFilter        SHA-256 or SimHash
     │
     ├─ OutputChannels            file, index, vector
     │
     └─ new links ──► back to the frontier, to whichever
                      node owns them
```

Every drop is counted and named. That is why the monitor can say where 22,000 urls went rather than
only that 140 pages were kept.

### How the cluster shares one crawl

```
node A ── finds /a/b ──► node C owns it
                            │
                    fetches, finds /a/b/c
                            │
                     ──► node A owns it ──► ...

no central queue      no leader in the fetch path
join or leave mid crawl
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

```
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id>
./greenfinger-cli.sh --cluster=nightly update  --id=<id>
./greenfinger-cli.sh --cluster=nightly merge   --id=<id>
./greenfinger-cli.sh --cluster=nightly rebuild --id=<id>
./greenfinger-cli.sh --cluster=nightly resume  --id=<id>
./greenfinger-cli.sh --cluster=nightly replay  --id=<id> \
    --layers=index+vector
```

**Output.**

- **crawl** goes from the start url and writes everything it saves.
- **update** fetches only urls not seen before, and writes new pages only.
- **merge** does that and revisits what is held, writing nothing for pages that came back
  unchanged.
- **rebuild** opens a new version, crawls the whole site again, and the old version keeps serving.
- **resume** continues from what is left on the frontier.
- **replay** fetches nothing and rebuilds an output from what is already stored.

`replay` is the one to remember. Your Elasticsearch was down for an hour, you changed the analyzer,
or you decided six months in that you want embeddings after all. None of those need the site to be
polite to you a second time.

### Search three ways

**Input.** One box, three modes, from the console or the prompt.

```
greenfinger:> search --query="bread"
greenfinger:> search --mode=meaning \
        --query="what happens when a star runs out of fuel"
greenfinger:> search --mode=pictures \
        --query="a bright spiral galaxy against black sky"
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

```
Score   Title
0.9356  APOD: 2008 June 4 - Chasing the ISS
0.9317  APOD Index - Nebulae: Supernova Remnants
0.9289  APOD Index - Stars: Binary Stars
```

The embedding models run locally and need no account: `multilingual-e5-small` for text and
`SigLIP 2` for images, both ONNX, both preloaded at startup on a background thread.

### Add a pdf parser

**Input.** A site that links pdfs.

**Code.** Every default component is `@ConditionalOnMissingBean`, so publishing a bean is the whole
registration. There is no plugin registry and no ordering property.

```java
@Bean
DocumentContentParser pdfParser() {
    return new DocumentContentParser() {
        public Set<String> fileTypes() {
            return Set.of("pdf");
        }

        public String extractText(byte[] content, String url,
                Charset encoding) throws Exception {
            return new Tika()
                    .parseToString(new ByteArrayInputStream(content));
        }
    };
}
```

**Output.** With `GF_DOCUMENTS=true` and `GF_DOCUMENT_TYPES=pdf`, pdfs linked from a crawled page
now contribute text to the index and the vectors. Collecting the links was always happening and
costs nothing. Fetching and reading them is what you just switched on.

The same shape works for every decision the crawler makes:

- **Extractor** decides how a page is fetched. Ships restclient, htmlunit, playwright, selenium and
  adaptive.
- **UrlPathAcceptor** decides whether a link is followed. Ships domain, start url, assets,
  robots.txt, depth and path patterns.
- **ContentDedupFilter** decides whether two urls are the same page. Ships sha256 and simhash.
- **ContentExtractor** finds the text inside a page. Ships link density boilerplate removal.
- **DocumentContentParser** gets text out of a non html file. Ships text, markdown and csv.
- **CompletionChecker** decides when the crawl is over. Ships saved count and elapsed time.
- **OutputChannel** decides where results are written. Ships file, index and vector.
- **BlobStore** holds pages and images as bytes. Ships local and minio.
- **Searcher** and **VectorStore** handle full text and meaning. Ships lucene, elasticsearch,
  qdrant and weaviate.
- **EmbeddingClient** turns text into a vector. Ships local ONNX, ollama and openai.

### Embed it in your own application

**Input.** A Spring Boot application of your own.

```java
@EnableGreenfingerServer
@SpringBootApplication
public class MyApplication { }
```

**Output.** The whole REST api, the login and the page, inside your process. It is explicit rather
than auto configured, because sitting on a classpath is not a reason to open RocksDB and take a
crawl permit. To drive a crawl without the server,
`CrawlerLauncher.crawl(catalogId, onReady)` returns a `CrawlerEngine.Result` with the counters and
the reason it ended.

---

## Configuration

### Per catalog

One url is required. Everything else has a default chosen to give a useful crawl of a site you know
nothing about. Run `options` at the prompt for the live values.

- **url** takes http:// or https://. Required, and it is the identity and the outer boundary.
- **name** takes unique text. Defaults to the domain.
- **cat** is one of nine categories, used to filter and group. Defaults to `other`.
- **start-url** is where fetching begins and must sit under `url`. Defaults to `url`.
- **sitemap-url** takes a url. Empty discovers it from robots.txt.
- **include** takes an ant path pattern, comma for several. Defaults to `**.<domain>/**`.
- **exclude** takes an ant path pattern, comma for several. Defaults to empty.
- **encoding** is the page charset, for when the server is wrong about it. Defaults to UTF-8.
- **extractor** takes adaptive, restclient, htmlunit, playwright or selenium. Defaults to adaptive.
- **max-size** is saved pages before the crawl stops. Defaults to 10000.
- **depth** is link depth, with -1 for no limit. Defaults to -1.
- **duration** is minutes before the crawl stops. Defaults to 30.
- **interval** is milliseconds between fetches, per node. Defaults to 1000.
- **retry** is retries per url. Defaults to 1.
- **images** decides whether pictures are fetched at all. Defaults to true.
- **output-types** takes file+index+vector, and file is always on. Defaults to file.
- **content** decides what reaches the index and the vectors. Defaults to text+image.
- **max-versions** is how many versions to keep. Defaults to 10.

### Node wide, in deploy/.env

- **GF_DB_URL** defaults to an H2 file. PostgreSQL, MySQL, SQL Server, Oracle and SQLite all work.
- **GF_INDEX_PROVIDER** defaults to `lucene`, or `elasticsearch` with `GF_ES_URIS`.
- **GF_VECTOR_STORE** defaults to `lucene`, or `elasticsearch`, `qdrant`, `weaviate`.
- **GF_FILE_TARGET** defaults to `local`, or `minio`. Object keys match the local paths exactly.
- **GF_EMBEDDING_PROVIDER** defaults to `local`, or `ollama`, `openai`.
- **GF_LUCENE_ANALYZER** defaults to `standard`, and takes `smartcn` or `cjk`.
- **GF_ES_ANALYZER** defaults to `standard`. `ik_max_word` needs the analysis-ik plugin.
- **GF_DOCUMENTS** defaults to `false`, and decides whether linked documents are fetched and read.
- **GF_CLUSTER_TRANSPORT** defaults to `NETTY`, or the built in `NIO`.
- **GF_NODES** defaults to 1, and is how many processes a launcher starts.
- **GF_MEMORY** defaults to 2g per container, and caps heap **and** off heap together.

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

**Crawling.**

```
site                  nodes  kept        images  urls seen   elapsed
apod.nasa.gov           2     46 pages      16     3,773      1m 41s
simplefood.blog         2    140 pages   2,489    22,857 *    5m 01s
books.toscrape.com      1  1,000 pages   3,204    28,411      4m 12s

* already known
```

The gap between urls seen and pages kept is the point rather than an inefficiency. On simplefood
22,340 urls were filtered out by the boundary rules and 561 were duplicate content, which is work
the outputs never had to do.

**How evenly the work spreads.**

```
two nodes, apod.nasa.gov
  node 18bbe738   76 handled   51%   26 pages   9 images
  node 96c82b18   74 handled   49%   19 pages   7 images

three nodes, a 61 page site, one shared PostgreSQL
  dispatched 61      handled 66      saved 61
  node-1: 18 fetches   node-2: 18   node-3: 25
  each page exactly once
```

**Everything else we measured.**

- **Word search**, embedded Lucene over 151 documents: 38 matches in 16 ms.
- **Cluster wire format**, one `CrawlTask`: JSON 327 bytes, encode and decode 1,394 ns.
- **Both ONNX models preloaded**: 4.47 s, on a background thread after the app is ready.
- **Idle RSS with both models loaded**: 1.77 to 1.88 GB.
- **Image vector duplication**, before and after the fix: 43 times, then 1.00 times.
- **Container memory**: 1g is OOMKilled with either vectors or a browser, 2g passes, and the two
  want separate runs.
- **Tests**: 133 classes, 1,098 methods, with an 80% line coverage gate on four modules.

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

Source, issues and full documentation:
[github.com/paganini2008/greenfinger](https://github.com/paganini2008/greenfinger)
