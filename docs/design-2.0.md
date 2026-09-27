# Greenfinger 2.0 design

How the system is shaped, and why. Reference rather than tutorial. What it does and how to run it
is in the [root README](../README.md), every interface is in the
[developer guide](developer-guide.md).

Status: implemented and released as 2.0.0. Where a decision was measured rather than argued, the
number is in [Evidence](#evidence).

## Contents

[The one constraint](#the-one-constraint) · [Modules](#modules) · [The life of one page](#the-life-of-one-page) · [Three outputs](#three-outputs) · [Data model and ids](#data-model-and-ids) · [Storage layout](#storage-layout) · [Versions](#versions) · [Distribution](#distribution) · [One writer for administrative writes](#one-writer-for-administrative-writes) · [Search](#search) · [Embeddings](#embeddings) · [Deletion](#deletion) · [Seams](#seams) · [Evidence](#evidence) · [Known trade-offs](#known-trade-offs)

---

## The one constraint

Everything below follows from one rule: **search never reads the database.**

``` text
                    ┌─────────────────────────────────────────────┐
   a crawl ────────►│  files        html, article text, images     │──► the bytes a result shows
                    ├─────────────────────────────────────────────┤
                    │  index        full text, self contained      │──┐
                    ├─────────────────────────────────────────────┤  ├──► every search
                    │  vectors      text chunks, image embeddings  │──┘
                    └─────────────────────────────────────────────┘
                    ┌─────────────────────────────────────────────┐
                    │  database     metadata only, no page bodies  │──► defining and running crawls,
                    └─────────────────────────────────────────────┘     resuming, diagnosis
```

The test: drop `crawler_resource` and its child tables, and search is unaffected.

Three consequences that shape the rest of the design:

| Because search never reads the database | So |
|---|---|
| The index and the vector store have to answer alone | Their metadata is self contained, carrying version, file paths and image information |
| A result has to render without a join | Page and image association is duplicated into the search layer |
| A vector point has to explain itself | Its payload carries the page it came from |

---

## Modules

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

| Module | Holds | Packaged |
|---|---|---|
| `greenfinger-core` | The crawler. Knows nothing about clusters or http servers | jar |
| `greenfinger-cluster` | Distribution on top of openspreader | jar |
| `greenfinger-shell` | The prompt and the one line crawler | executable jar |
| `greenfinger-api` | REST, login, static page | executable jar |

**Both front ends depend on core, neither passes through the other.** The service layer therefore
lives in core, and the prompt and the page drive the same one. The command line needs no servlet
container and should not inherit the web layer's dependencies.

**Two enabling annotations, neither auto configured.** Sitting on a classpath is not a reason to
open RocksDB and take a crawl permit.

| Annotation | In | Gives |
|---|---|---|
| `@EnableGreenfingerCrawler` | core | Engine, outputs, persistence, services |
| `@EnableGreenfingerServer` | api | All of that, plus REST, login and the page, in a web environment |

**Three levels, and every operation names the first one.**

``` text
catalog      a site plus the rules for crawling it. crawl, update, rebuild and delete all act here
  └─ resource    one crawled url, carrying a version. The same url in two versions is two resources
       └─ image      a picture a page referenced. resource to image is many to many
```

---

## The life of one page

``` text
   frontier.poll()
        │
        ▼
   ┌─────────────────────┐   refuse
   │ UrlPathAcceptor ×6  │──────────► dropped, counted as "filtered out"
   │ domain scope        │
   │ start url prefix    │      first refusal wins, the first two cannot be switched off
   │ asset check         │
   │ robots.txt          │
   │ depth               │
   │ path patterns       │
   └─────────┬───────────┘
             ▼
   ┌─────────────────────┐   seen
   │ ExistingUrlPathFilter├─────────► dropped, counted as "already known"
   │ RocksDB             │
   └─────────┬───────────┘
             ▼
   ┌─────────────────────┐
   │ Extractor           │  plain http first. A browser only when what came back
   │ adaptive by default │  looks like an unrendered shell
   └─────────┬───────────┘
             ▼
   ┌─────────────────────┐
   │ ContentExtractor    │  the article, not the navigation. Link density, no model
   │   └ DocumentContent │  anything that is not html goes to a parser instead
   │     Parser          │
   └─────────┬───────────┘
             ▼
   ┌─────────────────────┐   same content
   │ ContentDedupFilter  │──────────► dropped, counted as "duplicate"
   │ sha256 or simhash   │
   └─────────┬───────────┘
             ▼
   ┌─────────────────────────────────────────┐
   │ CompositeOutputChannel                   │
   │   file  ──►  index  ──►  vector          │  the order is not optional, see below
   └─────────┬───────────────────────────────┘
             ▼
   new links ──────► frontier, local or to whichever node owns the url
```

Every drop is counted and named, which is why the monitor can say where 22,000 urls went rather
than only that 46 pages were kept.

---

## Three outputs

``` text
   CrawledPage
        │
        ├─1─► FileOutputChannel     html, article .txt, image bytes      ALWAYS ON
        │                                   │
        │                                   │  the other two read the text back out of here
        │                                   ▼
        ├─2─► IndexOutputChannel    lucene | elasticsearch
        │
        └─3─► VectorOutputChannel   lucene | elasticsearch | qdrant | weaviate
```

**The order cannot be reversed.** The index and the vectors are built from the text the file layer
wrote. Building them first indexes nothing at all, and silently, because a missing file reads as
empty rather than as an error.

**The file layer is always on.** That is what makes replay possible.

``` text
   replay --layers=file+index+vector

   file    refetch only the pages and images actually missing, from the urls the rows record
     │
     ▼
   index   rebuild from the files
     │
     ▼
   vector  rebuild from the files

   the site is never crawled again
```

**A failing output does not fail the crawl.** `CompositeOutputChannel` records how many times each
channel failed and says so at the end, because what it missed can be replayed once the reason is
fixed. A crawl that aborted on a vector store timeout would have thrown away the pages too.

---

## Data model and ids

``` text
   crawler_catalog ──┬── crawler_resource ──┐
                     │                      ├── crawler_resource_image ── crawler_image
                     └── crawler_report     ┘

   every child row carries catalog_id and version, denormalised on purpose:
   deletion is "delete from t where catalog_id = ? and version = ?" with no join
```

### One id through the whole chain

The same value is the database primary key, the file name, the MinIO object key, the index
document `_id` and the vector point id. Always the 36 character text form of a UUID.

| Object | UUID version | Built from |
|---|---|---|
| `catalog.id` | **v7** | random, time ordered |
| `resource.id` | **v5** | ns = `catalog.id`, name = `version \| url_hash` |
| `image.id` | **v5** | ns = `catalog.id`, name = `version \| content_hash` |
| `resource_image.id` | **v5** | ns = `catalog.id`, name = `version \| resource_id \| image_id` |
| text vector point | **v5** | ns = `catalog.id`, name = `version \| resource_id \| chunkIndex` |
| image vector point | **v5** | ns = `catalog.id`, name = `version \| image \| image_id` |

**Why catalog is v7 and everything else is v5.** A catalog has no natural key, since its name is
only a label. With `uuid5(name)`, deleting a catalog and recreating it under the same name would
hand back the same id, and orphaned index documents and vectors would silently reattach.

Everything else is deterministic, so the whole chain is idempotent. Replaying, backfilling one
layer, re embedding: none of them can produce a duplicate. A unique constraint firing is therefore
not a conflict to tolerate, it is an assertion that the url dedup layer has a bug.

**Version has to be part of the input.** Without it, a rebuild would overwrite the previous
version's documents and points, and versions could not coexist.

**Image vector points are keyed by the image, not by the reference.** Keying them by
`resource_image.id` produces one point per page that references the picture, which measured at 43
identical points for one image and 43 embedding computations to make them. The channel also keeps
a per run set of image ids so a picture is embedded once no matter how many pages carry it.

### Three decisions worth stating

| | Why |
|---|---|
| `url_hash char(64)` exists | InnoDB caps an index key at 3072 bytes, so a unique constraint on `url varchar(1000)` fails to build on MySQL. The hash also feeds `resource.id` |
| Ids are `char(36)` text, not native `uuid` or `binary(16)` | About 20 bytes more per row, in exchange for an id that reads identically in the database, the index, the vector store and the file name |
| The database holds no page bodies | 100,000 pages of html is tens of gigabytes that neither of the database's two jobs needs. The text lives in the file store, the row points at it |

Enums such as `counting_type` and `output_types` are stored as ordinary columns rather than mapped
enums, because Hibernate 7 infers a check constraint whose H2 form rejects every non null value.

---

## Storage layout

``` text
  data/
  ├── system/                              working state. Local to a node, always
  │   ├── greenfinger.mv.db                H2, when no database server was configured
  │   ├── frontier/{catalogId}/v{n}/       RocksDB, the urls still to visit
  │   └── dedup/url/  dedup/content/       RocksDB, what has been seen
  │
  └── user/                                what was crawled. This is what a search reads
      ├── assets/{catalogId}/v{n}/
      │   ├── settings.json                how this version was configured, and how it went
      │   ├── reports/{stamp}-{action}-{node}.json
      │   ├── pages/ab/cd/{resourceId}.html
      │   ├── pages/ab/cd/{resourceId}.txt
      │   └── images/ab/cd/{imageId}.jpg
      ├── index/{prefix}-{catalogId}/      embedded Lucene, one index per catalog
      └── vector/{collection}_{dims}/      embedded Lucene, one collection per embedding width
```

| Decision | Why |
|---|---|
| Two halves, `system` and `user` | `system` can be deleted without affecting a search, at the cost of a resume. `user` is the half worth backing up |
| Two level fanout `ab/cd/` from the id | One directory per catalog version with a hundred thousand entries is slow to list on every filesystem that matters |
| Paths keyed by catalog **id**, never name | Renaming a catalog moves nothing on disk |
| The version is a path element | Removing one version is removing one directory |
| MinIO keys are identical to local paths | Moving between them rewrites nothing |

`index/` and `vector/` simply do not exist when Elasticsearch, Qdrant or Weaviate is configured.

---

## Versions

``` text
   index_version    the version being written        ── incremented by rebuild
   search_version   the version search reads         ── advanced only when a crawl finishes

   v0  ████████████  published
   v1  ████████████  published, search_version = 1
   v2  ██████░░░░░░  index_version = 2, being written

                     search keeps answering from v1 the whole time
```

**An interrupted crawl publishes nothing.** A site that starts rate limiting halfway through a
rebuild leaves search exactly as it was. What counts as interrupted is narrow: a configured limit
such as `maxFetchSize` or `fetchDuration` is a **normal** end and does publish. Only an external
stop does not. Getting this backwards was a real defect in 1.x, where a crawl that stopped at its
own limit never advanced `search_version` and search returned nothing.

| Verb | Version | Fetches | Writes |
|---|---|---|---|
| `crawl` | current | from the start url | everything it saves |
| `update` | current | only urls not seen before | new pages only |
| `merge` (`update --refresh`) | current | new urls **and** the pages already held | only the pages that actually changed |
| `rebuild` | a new one | the whole site again | into the new version, old one keeps serving |
| `resume` | current | what is left on the frontier | as the interrupted run would have |
| `replay` | a named one | nothing | rebuilds an output from what is stored |

`url` and `start-url` are two different things. `url` is the identity and the boundary. `start-url`
is where fetching begins, and it must sit under `url`. Pointing `url` deep into a site therefore
puts the rest of the site out of scope, which is the usual explanation for a crawl that saved
exactly one page.

---

## Distribution

### The model: fork without join

A crawl is a recursive function.

``` text
   handle(url) {
       html = fetch(url)
       save(html)
       for (link : links(html)) handle(link)      ◄── make this line cross a process
   }
```

That is the whole of it. **There is no join.** A parent page does not care what its children
found, so nothing waits, nothing merges, and there is no return value.

``` text
   node A ── finds /a/b ──► hash(catalogId|version|url) ──► node C
                                                             │
                                             fetches, finds /a/b/c
                                                             │
                                          hash(...) ──► node A ──► ...
```

This is deliberately not openspreader's `RecursiveTask` and `ProcessingPool`, which are request and
reply. Four things conflict with a crawler: depth is unbounded, a blocking `join` holds a thread
per level without triggering work stealing, a remote failure falls back to **recomputing locally**
when fetching a page means writing a row, a file and an index document, and there is no return
value to wait for. Request and reply is right for exactly one operation here, which is replay.

### One process is a cluster of one

There is no standalone mode to grow out of. `CrawlCoordinator` has two methods, "where does the
next hop go" and "is it finished", and the local implementation answers "the local frontier" and
"yes". That is what a cluster degrades to when the only member is you, so there is one code path.

### The channels

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

The record, rocksdb, blob and search channels only carry anything when the store behind them is per
node. A shared PostgreSQL, a MinIO bucket or an Elasticsearch cluster needs none of them.

``` text
   StoreType(replicated?)

   SQLITE ✓   H2 file ✓   LOCAL_FILE ✓   ROCKSDB ✓
   H2 tcp ✗   MYSQL ✗   POSTGRESQL ✗   SQLSERVER ✗   ORACLE ✗   MINIO ✗
```

H2 is both, and the two forms differ by four characters, so the decision reads the jdbc url rather
than the product name.

What replicates is the **operation**, not a snapshot, batched into frames. Delivery is at least
once, so every application has to be idempotent. The frontier is deliberately **not** replicated,
because it is this node's work queue and copying it would make every node crawl the whole site.

The `catalog` row matters more than the rest. A node that has never heard of a catalog cannot open
its half of the crawl, so urls dispatched to it go nowhere. That failure does not raise anything.
The counters simply stop at "dispatched 1, handled 0" for as long as you care to watch.

### Completion is decided by everyone

``` text
   every node ── reads the shared counters ──► maxFetchSize? fetchDuration?
                                                        │
                                          first to notice writes the reason
                                                        │
                                          shared state makes it take effect once
```

Finishing is a question about counters that every node can read, so it is not the leader's
decision. A leader that dies mid crawl therefore cannot leave a crawl that never ends.

### One url, fetched once

At least once delivery is the messaging system working correctly, not a fault. Handling it took
three changes, and all three are needed.

``` text
   1. route by url        consistent hash of catalogId|version|url, deliberately not including
                          the referring page, since the same url found from two pages is exactly
                          what should collapse

   2. frontier enqueues   a "u:" keyspace beside the queue's "f:". Every enqueue path meets here:
      a url once          from a peer, kept local, seeded from a sitemap, recovered from last run

   3. settle every        put() returns whether it enqueued, and the three call sites settle the
      dispatch exactly    counter themselves when it did not. Otherwise handled trails dispatched
      once               forever and the run is judged stalled
```

The `u:` keyspace records "queued during **this run**", not "seen in this version". It is cleared
at startup and rebuilt from the recovered queue, because `refresh` writes the same version into the
same store and the pages it means to revisit are exactly the ones it already fetched.

`ExistingUrlPathFilter` cannot answer this question, wherever it stores its bits, because it is set
**before** dispatch. By the time a duplicate message arrives it gives the duplicate the same answer
it gave the original.

### Completion is also announced

The shared counters hold a **state**. What they cannot give is a **moment**. An application that
wants to act when a crawl ends would otherwise poll a flag, late by up to one interval, with
nowhere to hang a hook.

``` text
   the node that finishes ──► broadcasts COMPLETED once
                                        │
                     every node, including the sender and including nodes that
                     took no part, raises WebCrawlerCompletionEvent locally, once
```

It is a notification and not a decision. It is sent after the version is published, the stores are
closed and the permit is released. The crawler waits for no listener, and a listener that throws is
logged, because somebody else's notification failing is not the crawl failing.

### Why JSON on the wire

Measured: a `CrawlTask` is 327 bytes of JSON and 1394 ns to encode and decode, against 427 bytes
for JDK serialization. At a thousand urls per second, which no polite crawler reaches, that is
0.14% of one core. The bottleneck was never encoding an address.

The decision was made by something else. This is the format between nodes, and nodes are upgraded
one at a time. During the window, a new node sends tasks to an old one. JSON ignores fields it does
not know. A positional format cannot, and the receiving side **discards** what it cannot read, so
the symptom would be pages quietly going missing during an upgrade and nothing else to see.
`@JsonIgnoreProperties(ignoreUnknown = true)` on `CrawlTask` is there for that.

Image bytes travel as binary frames and never pass through an object encoder, because JSON would
make every picture a third larger.

### Replay is the one scatter and gather

``` text
   asked node ──► slices of 200 pages ──► every node ──► each rebuilds its slice
                                                │
                                   a slice that fails is simply done again,
                                   locally, because replay is idempotent
```

Bounded, one shot, idempotent, and the caller needs to know when all of it is done. That is exactly
the shape request and reply fits, which is why it is used here and nowhere else.

---

## One writer for administrative writes

The crawl itself has no write conflicts, since one url goes to one node. Administrative writes are
a different shape. Saving a catalog, deleting a version and publishing a finished version all write
things that every node holds its own copy of.

``` text
   any node ──► LeaderGateway.onLeader(op, arg) ──► the node holding the cluster port
                        │                                        │
       on the leader itself this is a plain                 does the write
       method call with no network, which is               answers back
       what almost every installation is

   CatalogCatchUp ──► a node that joined late asks the leader for the rows it lacks
```

Two copies of one table, edited by two people, means either write timestamps, content hashes and
tombstones, or accepting drift. The third option is to allow one writer, and then none of those
three has to exist.

Crawl output does not take this path. Delivery already guarantees one node per url, and routing
page bytes through the leader would double the traffic and cap the whole cluster's fetch rate at
one process.

**Two classes of bug came out of this**, both worth knowing:

| Symptom | Fix |
|---|---|
| Primary key conflict, twice: catch up racing replication, then "carry the Catalog row with the STARTED message" racing replication | `findById` first, treat a failed insert as somebody else having written it and read back, and `synchronized` on `catchUp` and `align`. In a distributed system the check then write window always exists, and tolerating a duplicate is cheaper than closing it |
| A follower received "start crawling catalog X" before the row replicated, and looked up a catalog that did not exist yet | Catch up covered it but took two to three and a half minutes to converge, which is longer than a short crawl. The broadcast now carries the Catalog row, so it arrives with whoever needs it |

---

## Search

``` text
                   ┌── words     ──► index        exact terms, highlighted
   one query box ──┼── meaning   ──► text vectors      what the page is about
                   └── pictures  ──► image vectors     what a picture looks like

   scope: every published version of every catalog, filtered as "<catalogId>:<searchVersion>"
```

The filter is the published version, so older versions are invisible and a rebuild in progress
never leaks into results.

### Detail pages above listings

A listing page matches the same words as the article it links to, and is almost never the answer.
The signal is **link density**, anchor text length over total text length, from the Boilerpipe work
(Kohlschütter et al., WSDM 2010). It is close to 1 on a listing and close to 0 on an article, and
unlike a raw link count it does not move with page length.

| Store | How |
|---|---|
| Elasticsearch | `function_score` with `script_score`, `1.5 - density`, reordering only documents that already matched |
| Vector stores | No function score, so `VectorSearcher` over fetches by four and reranks client side, deduplicating by resourceId because twenty chunks of one long article is one result to a reader |

`SearchRequest.preferDetailPages` turns it off.

### Deep paging

``` text
   words     search_after cursor, sort [_score desc, id asc]      cost independent of depth
   vectors   offset, capped at 1000                              a vector store walks the offset
```

`from + size` past 10,000 is refused by Elasticsearch, and raising `max_result_window` spends
cluster memory on paging. `id` is a plain field mirroring `_id`, because sorting on `_id` itself
needs fielddata.

### A blank box is a different question

An empty query is not a similarity question, so it does not touch the vector store at all. It lists
what was kept, straight out of the record store, carrying the first 400 characters of the article
so the cards look like the ranked ones. The listing deduplicates images by image id for the same
reason the vector points are keyed that way.

---

## Embeddings

``` text
   EmbeddingClient
     ├── local     ONNX in this process. multilingual-e5-small (384) + SigLIP 2 (768)
     ├── ollama    an http service
     └── openai    an http service

   collection name carries the width:  greenfinger_text_384   greenfinger_image_768
```

**The width is part of the collection name.** Changing provider changes the width, and writing
1536 dimensional vectors into a 384 dimensional collection is an error at best and nonsense at
worst. Binding the two means a provider change creates a new collection instead of corrupting one.

**Preloading happens after the application is ready, not during startup.** Both models load on a
daemon thread once `ApplicationReadyEvent` fires, each behind its own switch
(`GF_PRELOAD_TEXT_MODEL`, `GF_PRELOAD_IMAGE_MODEL`), both on by default. Loading them inside the
Spring context would put four and a half seconds in front of every boot, including boots that never
embed anything. There is no master switch, because a catalog that asks for vectors while the
matching switch is off gains nothing from one.

**Only the local provider preloads.** Ollama and OpenAI are http services, so there is no model to
load into this process.

The models are cached in `GF_MODEL_DIR` and downloaded once, per machine and per container image.

---

## Deletion

``` text
   write order:    file ──► index ──► vector
   delete order:   vector ──► index ──► file ──► db

   the reverse, so a half finished delete never leaves a searchable pointer
   to bytes that are already gone
```

| Layer | What removing it means |
|---|---|
| `vector` | Points for that catalog and version |
| `index` | Documents for that catalog and version. The index itself stays unless `purge=true` |
| `file` | The version directory, or the matching object keys |
| `db` | Rows in four tables, by `(catalog_id, version)` with no join |

`--layers` takes any combination. Two safety properties:

- **A preview is the same walk as the delete.** `dryRun` reports exactly what a real delete would
  touch, because it runs the same code and stops before writing.
- **`dryRun` defaults to true** on the REST endpoint. A caller who forgets it gets a preview, not a
  deletion.

`maxVersions` prunes automatically, oldest first, never the published one.

---

## Seams

Every component the crawler builds is an interface with a default that is
`@ConditionalOnMissingBean`. Publishing a bean replaces it. There is no registration file and no
ordering property.

``` text
   WebCrawlerComponentFactory
        │  builds all of them. Publish your own and none of the defaults are created
        ▼
   Extractor · RenderingDetector · UrlPathAcceptor · ExistingUrlPathFilter
   ContentDedupFilter · ContentExtractor · DocumentContentParser
   CompletionChecker · CrawlFrontier · OutputChannel · BlobStore
   Searcher · IndexAdmin · VectorStore · EmbeddingClient
   CatalogStore · ResourceRecordStore
```

Three ways in, and which one a change needs is worth knowing:

| | How | Reaches |
|---|---|---|
| A setting | `.env`, or a field on the catalog | Everything already written, configured differently |
| A bean | Publish one | One piece, whole installation |
| A class name | On the catalog, instantiated by name | One piece, one catalog |

Two shared objects exist on purpose, in `com.github.greenfinger.utils`:

| | Why one |
|---|---|
| `HttpUtils.getClient()` | One pooled Apache HttpClient 5 for pages, images, documents and every REST call. Three separate http stacks meant a proxy setting that covered half the traffic, and a connection pool per catalog |
| `JsonUtils.MAPPER` | One ObjectMapper. Per call differences belong on the call |

---

## Evidence

Measured on real runs, not mocks.

### Distribution

``` text
   three nodes, 61 page site, one shared PostgreSQL

   dispatched 61      handled 66      saved 61
   node-1: 18 fetches   node-2: 18   node-3: 25        =  61, each page exactly once
```

An earlier run of the same site showed 161 dispatched, because all three nodes found the same link
before each other's dedup replicated. That number moves with timing. `saved` was 61 both times. The
extra hundred dispatches were refused by the frontier before any fetch happened.

Two nodes on a 40 page site, taken from the run report: node A handled 76 and node B 74, a 51 to 49
split, 26 and 19 pages, 9 and 7 images.

### Serialization

``` text
   CrawlTask     JSON 327 bytes, encode + decode 1394 ns
                 JDK serialization 427 bytes
```

### Image vectors

``` text
   before   one point per page that referenced the picture      43× duplication
   after    one point per picture, plus a per run id set        1.00×
```

Measured on the ranked path. The blank query path does not touch the vector store at all, so
measuring duplication there proves nothing.

### Analyzers

Crawling with the wrong analyzer is not a ranking problem, it is a data problem. The standard
analyzer cuts Chinese into single characters. A Chinese analyzer applied to French drops characters
outright, so `chaîne` comes back as `cha î ne`.

### Coverage

``` text
   backend    around 860 tests, JaCoCo line coverage gate at 80% in all four modules
   frontend   27 unit tests, 21 Playwright e2e specs
```

The e2e suite builds its own corpus through the REST api and leaves it in place. A suite that
depends on a catalog somebody happened to have is a suite that stops working.

### Containers

``` text
   1g + vectors      OOMKilled after 20s
   1g + playwright   OOMKilled as the browser starts
   2g               either one passes, separately
   3g               both in one JVM still OOMKilled
```

`MaxRAMPercentage` sizes the heap. The DJL and ONNX models and the Chromium process are both off
heap, and the cgroup limit caps the total. Default `GF_MEMORY` is 2g.

Rendering leaves `headless_shell` zombies when the JVM is PID 1, since it does not reap orphaned
grandchildren. `docker run --init` fixes it: six zombies under the same load before, zero after.

---

## Known trade-offs

| | |
|---|---|
| The embedded index and vector store cost the extracted text once per node | Which is why the startup report recommends Elasticsearch or Qdrant for a cluster of any size |
| Replication is asynchronous | Immediately after a crawl, one node can answer a search before another has caught up. Seconds, not minutes |
| `CatalogCatchUp` makes the leader's table authoritative without comparing ages | A node whose database is stale, offline while catalogs changed, can win an election and push old rows over new ones. It needs per node databases to happen at all |
| A node killed mid crawl leaves `runningState` set | The registry says nothing is running, the row says otherwise, and `interrupt` has nothing to interrupt. A node should reset its own leftover state at startup |
| Article extraction leaves some template text in the chunks | Share buttons and footer credits appear in search snippets |
| Two nodes can both believe they are leader for a few seconds after the cluster forms | A version is then published twice. Publishing is idempotent, and putting it on the leader was never for correctness, only to stop three nodes doing the same work three times |

---

## Further reading

| | |
|---|---|
| [README](../README.md) | What it does and how to run it |
| [Developer guide](developer-guide.md) | Every seam, with a worked example |
| [Command line reference](cli-reference.md) | Every verb and every option |
| [Schema scripts](sql/schema-scripts.md) | One per database, generated from the entities |
| [Changelog](../CHANGELOG.md) | What changed in 2.0 |
