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

## 60 second quick start

``` shell
git clone https://github.com/paganini2008/greenfinger.git
cd greenfinger/backend && mvn clean install
cd ../deploy

./greenfinger-cli.sh --cluster=demo crawl --url=https://books.toscrape.com
```

Output:

``` text
Crawling 'books.toscrape.com' from https://books.toscrape.com

  pages kept    1,000      urls seen   28,411      1 in every 28
  images        3,204      indexed      1,000      elapsed  0h 4m 12s

Finished: reached maxFetchSize
Catalog 01a0c3d5-7d3d-7000-81f6-76057a403db8, version 0, now searchable.
```

Prefer the web interface:

``` shell
./run-local.sh                # nodes plus the page on http://localhost:9700
./run-docker.sh               # the same, in containers
./run-local.sh stop
```

Sign in with `admin` and the password in `deploy/config/api/users.xml`. Seven example catalogs ship
with a fresh install, so there is something to crawl before you have picked anything.

---

## What it solves

| The problem | What Greenfinger does |
|---|---|
| The crawl wanders off into the rest of the web | Two boundary rules that cannot be switched off: same registrable domain, and under the start url |
| One machine is not enough | Every node runs the same jar and pulls its own share. No queue service, no scheduler |
| Killed at page 40,000, restarts from zero | The frontier is on disk. A resume carries on from where it stopped |
| Half the site is pdf, docx, markdown | Document links are recorded on every crawl. Text, markdown and csv read out of the box |
| Navigation and cookie banners get indexed | Boilerplate dropped by link density. No model, no dictionary, any language |
| Files, index and embeddings are three projects | One pass writes all three. Any of them rebuilds from the files, without recrawling |

---

## One pass, three outputs

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

| Output | What goes there | Backends |
|---|---|---|
| **file** | The page as fetched, the article text, every image | local, minio |
| **index** | Full text, searchable by words | lucene, elasticsearch |
| **vector** | Text chunks and image embeddings | lucene, elasticsearch, qdrant, weaviate |

The file layer is always on. The database keeps metadata only, so the other two rebuild from it.

``` shell
# Elasticsearch was down for an hour. Or you changed the analyzer.
# Or you decided six months in that you want embeddings after all.
./greenfinger-cli.sh --cluster=nightly replay --id=<id> --layers=index+vector
```

Switching the file store is three lines, and the object keys are identical to the local paths:

``` shell
GF_FILE_TARGET=minio
GF_MINIO_ENDPOINT=http://minio:9000
GF_MINIO_BUCKET=greenfinger
```

AWS S3 and Google Cloud Storage both publish S3 compatible endpoints, so the same three lines point
at either. MinIO is what ships and what the test matrix covers.

---

## Decentralised, and dynamic

No coordinator process. No scheduler to deploy. One url dispatched is one call to another node.

``` text
   node A ── finds /a/b ──► node C owns it ──► fetches, finds /a/b/c ──► node A owns it ──► ...

   no central queue        no leader in the fetch path        join or leave mid crawl
```

``` shell
# machine A
GF_CLUSTER_NAME=nightly GF_CLUSTER_HOSTS=10.0.0.1,10.0.0.2 ./run-local.sh

# machine B, joining a crawl that is already running
GF_CLUSTER_NAME=nightly GF_CLUSTER_HOSTS=10.0.0.1,10.0.0.2 ./run-local.sh
```

Each node reports what it actually pulled, live and in the report:

![What each node did](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-nodes.jpg)

**Transport is NIO**, with Netty as a drop in replacement that is on by default:

``` shell
GF_CLUSTER_TRANSPORT=NETTY     # the default
GF_CLUSTER_TRANSPORT=NIO       # the built in transport
```

**Completion is decided by everyone.** Every node checks the shared counters against
`maxFetchSize` and `fetchDuration`, and the first to notice writes the reason. Not the leader's
decision, so a leader that dies mid crawl does not leave a crawl that never ends.

![Members, roles, transport, health](https://paganini2008.github.io/greenfinger/blogger/assets/cluster.jpg)

---

## What one page goes through

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

**Pictures are first class.** From `<img>`, `srcset`, `<picture>` and `og:image`, filtered by size,
media type and byte count. Identical bytes stored once however many pages point at them.

**Documents are read, not skipped.** Text, markdown and csv ship with a parser. Pdf, Word and Excel
are one bean away, because each means a dependency with its own licence and its own appetite for
memory:

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

---

## Everything a catalog can set

One url is required. Everything else has a default chosen to give a useful crawl of a site you know
nothing about. Run `options` at the prompt for this table with the current values.

| Setting | Accepts | Default |
|---|---|---|
| `url` | http:// or https:// | required, the only one |
| `name` | unique text | the domain |
| `cat` | one of nine categories, used to filter and group | `other` |
| `start-url` | a url under `url`. A seed and a boundary at once | `= url` |
| `sitemap-url` | a url, or empty to discover it from robots.txt | empty |
| `include` | ant path pattern, comma for several | `**.<domain>/**` |
| `exclude` | ant path pattern, comma for several | empty |
| `encoding` | UTF-8, GBK, and the rest | UTF-8 |
| `extractor` | adaptive, restclient, htmlunit, playwright, selenium | adaptive |
| `max-size` | saved pages before it stops | 10000 |
| `depth` | -1 for no limit | -1 |
| `duration` | minutes before it stops | 30 |
| `interval` | milliseconds between fetches, per node | 1000 |
| `retry` | retries per url | 1 |
| `url-dedup` | rocksdb, or a filter of your own by class name | rocksdb |
| `images` | whether pictures are fetched at all | true |
| `output-types` | file+index+vector, file is always on | file |
| `content` | text+image or text, what reaches the index and vectors | text+image |
| `max-versions` | how many versions to keep | 10 |

Node wide defaults for all of these live in `.env`, and a catalog overrides any of them.

---

## Also in the box

- **Sitemaps.** What a site publishes about itself is collected before the crawl starts, from
  `sitemap-url` or discovered through robots.txt.
- **File restore.** `replay --layers=file` fetches back pages and images that were lost, from the
  urls the rows record, only the ones actually missing. Combine with `index` and `vector` to repair
  a version completely.
- **Deep paging.** Word search pages by cursor, past the ten thousandth result Elasticsearch
  refuses. Vector search pages by offset, capped at a thousand.
- **Search ranking.** Detail pages above listings, in both the index and the vector store.
- **An account of every run.** One report file per node beside the pages, plus a merged one, kept
  with the version.
- **Six databases.** H2, SQLite, MySQL, PostgreSQL, SQL Server and Oracle, each through the full
  regression.
- **A proxy when a site needs one.** `greenfinger.extractor.base.proxy-host` and `proxy-port` apply
  to every fetch, pages, images and documents alike, because one shared HttpClient makes them.
- **`test-url`** fetches one url and reports what came back, the fastest way to find out why a site
  is refusing you.
- **Containers.** `run-docker.sh` starts one container per node plus the page container, which
  serves the app and spreads `/v2` and `/actuator` across whichever nodes answer.

---

## Search it three ways

### Words, to the index

![Searching by words](https://paganini2008.github.io/greenfinger/blogger/assets/search-words.jpg)

Exact terms, highlighted, detail pages ranked above listings. Deep paging uses a cursor, so it goes
past the ten thousandth result Elasticsearch refuses.

### Meaning, to the text vectors

![Searching by meaning](https://paganini2008.github.io/greenfinger/blogger/assets/search-meaning.jpg)

Query: *"what happens when a star runs out of fuel"*. Top answer: the supernova remnants page,
which never contains that sentence.

### Pictures, to the image vectors

![Finding pictures by describing them](https://paganini2008.github.io/greenfinger/blogger/assets/search-pictures.jpg)

Query: *"a bright spiral galaxy against black sky"*. Describe what you want to see rather than what
the caption might say.

**The models run locally and need no account.** `multilingual-e5-small` for text and `SigLIP 2` for
images, both ONNX, both preloaded at startup on a background thread.

``` shell
GF_EMBEDDING_PROVIDER=local     # the default, nothing to sign up for
GF_EMBEDDING_PROVIDER=ollama
GF_EMBEDDING_PROVIDER=openai
```

---

## The web interface

Angular 21 with signals, Material 3, talking to the same REST endpoints your own code would.

**Catalogs.** A site, the rules for crawling it, and every version it produced.

![Catalogs](https://paganini2008.github.io/greenfinger/blogger/assets/catalogs.jpg)

**New catalog** asks for a url and nothing else.

![New catalog](https://paganini2008.github.io/greenfinger/blogger/assets/catalog-new.jpg)

**Dashboard.** What this installation kept, and what it threw away to keep it.

![Dashboard](https://paganini2008.github.io/greenfinger/blogger/assets/dashboard.jpg)

**The report of a finished run.** Where every url went, and what the catalog was running with.

![Run report](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-report.jpg)

**Resources.** Every row, in crawl order, with the file paths and the content hash.

![Resources](https://paganini2008.github.io/greenfinger/blogger/assets/resources.jpg)

---

## Two command lines

| | What it is | Use it to |
|---|---|---|
| `greenfinger-cli.sh` | A crawler that runs one command and exits | Crawl from a script or a cron entry |
| `greenfinger-shell.sh` | A terminal on a cluster somebody is running | Look at it, search it, drive it |

``` shell
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id>            # from the start url
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id> --node=3   # three processes here
./greenfinger-cli.sh --cluster=nightly update  --id=<id>            # urls that appeared since
./greenfinger-cli.sh --cluster=nightly merge   --id=<id>            # and revisit what is held
./greenfinger-cli.sh --cluster=nightly rebuild --id=<id>            # new version, old one served
./greenfinger-cli.sh --cluster=nightly resume  --id=<id>            # continue after a pause
./greenfinger-cli.sh --cluster=nightly pause   --id=<id>
./greenfinger-cli.sh --cluster=nightly replay  --id=<id> --layers=index+vector
```

The prompt crawls nothing itself. It joins a running cluster and drives it, exactly as the page
does. Spring Shell, so completion, history and help come with it.

``` text
CATALOGS                              CRAWLING                          SEARCHING
catalog-list                          catalog-crawl  --id=<id>          search --query=<words>
catalog-show    --id=<id>             update         --id=<id>          search --query=<w> --mode=meaning
catalog-save                          resume         --id=<id>          search --query=<w> --mode=pictures
catalog-save    --json='{...}'        rebuild        --id=<id>          search                (lists everything)
catalog-delete  --id=<id>             pause          --id=<id>          search --query=<w> --id=<id>
catalog-cats                          status                            index-info
versions        --id=<id>             status --all=true                 vector-info
crawler-report  --id=<id>             delete         --id=<id> ...
                                      replay         --id=<id> --layers=index
                                      test-url       --url=<url>
                                      options
```

Real output from a running cluster:

``` text
greenfinger:> catalog-list
╭──────────────────────────────────────┬───────────────────────────────────┬─────────────────────────────┬───────────┬───────────────────┬─────────┬────────╮
│ Id                                   │ Name                              │ Url                         │ Category  │ Outputs           │ Version │ Search │
├──────────────────────────────────────┼───────────────────────────────────┼─────────────────────────────┼───────────┼───────────────────┼─────────┼────────┤
│ 01a0c3ac-90bc-7000-82aa-adcb256bc8bc │ NASA Astronomy Picture of the Day │ https://apod.nasa.gov/apod/ │ education │ file+vector+index │ v1      │ v1     │
│ 01a0c3ac-9152-7000-a9aa-7749058dd231 │ Rust Blog                         │ https://blog.rust-lang.org/ │ tech      │ file+index        │ v0      │ v0     │
│ 01a0c3d5-7d3d-7000-81f6-76057a403db8 │ Simple Food                       │ https://simplefood.blog/    │ food      │ file+vector+index │ v2      │ v1     │
╰──────────────────────────────────────┴───────────────────────────────────┴─────────────────────────────┴───────────┴───────────────────┴─────────┴────────╯

greenfinger:> search --query="what happens when a star runs out of fuel" --mode=meaning --size=3
╭────────┬──────────────────────────────────────────┬────────────────────────────────────────────────╮
│ Score  │ Title                                    │ Url                                            │
├────────┼──────────────────────────────────────────┼────────────────────────────────────────────────┤
│ 0.9356 │ APOD: 2008 June 4 - Chasing the ISS      │ https://apod.nasa.gov/apod/ap080604.html       │
│ 0.9317 │ APOD Index - Nebulae: Supernova Remnants │ https://apod.nasa.gov/apod/supernova_remnants… │
│ 0.9289 │ APOD Index - Stars: Binary Stars         │ https://apod.nasa.gov/apod/binary_stars.html   │
╰────────┴──────────────────────────────────────────┴────────────────────────────────────────────────╯

greenfinger:> vector-info
╭─────────────────────┬───────────────────┬  ╭──────────────────────────────┬─────────┬──────────────────────┬────────╮
│ Setting             │ Value             │  │ Catalog                      │ Version │ Collection           │ Points │
├─────────────────────┼───────────────────┤  ├──────────────────────────────┼─────────┼──────────────────────┼────────┤
│ Store               │ lucene            │  │ NASA Astronomy Picture of... │ v1      │ greenfinger_text_384 │    147 │
│ Chunk size          │ 1000              │  ╰──────────────────────────────┴─────────┴──────────────────────┴────────╯
│ Chunk overlap       │ 200               │
│ Max chunks per page │ 20                │
╰─────────────────────┴───────────────────╯
```

Multi word queries need double quotes, because the prompt tokenises the line before the command
sees it.

---

## Versions, and why a rebuild is safe

``` text
  v0  ████████████  searchable
  v1  ████████████  searchable          rebuild opens v2 beside v1
  v2  ██████░░░░░░  writing             search keeps answering from v1

  interrupted ──► publishes nothing ──► search is exactly as it was
```

| Verb | Does |
|---|---|
| `crawl` | From the start url |
| `update` | Only the urls that have appeared since |
| `merge` | That, and revisit what is held. Writes nothing for pages that came back unchanged |
| `rebuild` | A new version, whole site again, old one keeps serving |
| `resume` | Continue after a pause or a kill |
| `replay` | Rebuild an output from what is stored, without fetching |

---

## Extending it

Every default is `@ConditionalOnMissingBean`. Publish your own bean and the shipped one is not
created. No registration file, no ordering property.

``` java
@Bean
UrlPathAcceptor mySiteRules() { ... }           // added to the chain

@Bean
WebCrawlerComponentFactory myFactory() { ... }  // replaces all of it
```

| Interface | Decides | What ships |
|---|---|---|
| `Extractor` | How a page is fetched | restclient, htmlunit, playwright, selenium, adaptive |
| `RenderingDetector` | Whether it came back as an unrendered shell | Two length thresholds |
| `UrlPathAcceptor` | Whether a link is followed | Domain, start url, assets, robots.txt, depth, patterns |
| `ExistingUrlPathFilter` | Whether a url was seen before | rocksdb |
| `ContentDedupFilter` | Whether two urls are the same page | sha256, simhash |
| `ContentExtractor` | The text inside a page | Link density boilerplate removal |
| `DocumentContentParser` | Text out of a non html file | text, markdown, csv |
| `CompletionChecker` | When the crawl is over | Saved count, elapsed time |
| `CrawlFrontier` | The queue of what is left | rocksdb |
| `OutputChannel` | Where results are written | file, index, vector |
| `BlobStore` | Pages and images as bytes | local, minio |
| `Searcher` / `IndexAdmin` | Full text | lucene, elasticsearch |
| `VectorStore` | Meaning and pictures | lucene, elasticsearch, qdrant, weaviate |
| `EmbeddingClient` | What turns text into a vector | local ONNX, ollama, openai |
| `CatalogStore` / `ResourceRecordStore` | The metadata | JPA, json file, memory |

Three ways in, and it is worth knowing which one a change needs:

| | How | Reaches |
|---|---|---|
| A setting | `.env`, or a field on the catalog | Everything already written, configured differently |
| A bean | Publish one | One piece, for the whole installation |
| A class name | On the catalog, instantiated by name | One piece, for one catalog |

Embedding the api in your own application stays explicit:

``` java
@EnableGreenfingerServer
@SpringBootApplication
public class MyApplication { }
```

---

## Running it for real

Nothing is required to start. H2 and an embedded Lucene index are the defaults. Every external
system is opt in, one variable at a time.

``` shell
# deploy/.env
GF_DB_URL=jdbc:postgresql://db:5432/greenfinger   # or MySQL, SQL Server, Oracle, SQLite
GF_INDEX_PROVIDER=elasticsearch
GF_ES_URIS=http://es:9200
GF_VECTOR_STORE=qdrant
GF_QDRANT_URL=http://qdrant:6333
GF_FILE_TARGET=minio

GF_LUCENE_ANALYZER=smartcn        # standard | smartcn | cjk
GF_ES_ANALYZER=ik_max_word        # with the analysis-ik plugin
```

Analyzers matter more than people expect. The standard analyzer cuts Chinese into single
characters. A Chinese analyzer drops French characters outright. One setting per node covers it.

Sign in is a file, with two roles. SUPPORT can read every page and is offered no button that
writes:

``` xml
<users>
  <user name="admin"  password="..." roles="ADMIN"/>
  <user name="tester" password="..." roles="SUPPORT"/>
</users>
```

| Layer | Version |
|---|---|
| JDK | 17 or later |
| Spring Boot | 4.1 |
| Spring Shell | 4.0 |
| RocksDB | 10.x, frontier and both dedup stores |
| Apache HttpClient | 5.x |
| Jsoup | 1.23 |
| Lucene | 9.12, embedded index and vector store |
| Netty | 4.1, cluster transport, NIO fallback built in |
| Angular | 21, signals and Material 3 |

---

## Who it is for

| | Why |
|---|---|
| **A developer** | One command to point it at a site, one bean to change how it behaves |
| **A team** | A reproducible search or RAG corpus. Versioned, rebuildable, no external service until you want one |
| **An enterprise** | No single point of failure, scale by starting processes, storage and database are whatever you already run |

---

Source, issues and full documentation: **https://github.com/paganini2008/greenfinger**

Apache License 2.0.
