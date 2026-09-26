# Changelog

All notable changes to this project. Versions follow [Semantic Versioning](https://semver.org/).

---

## 2.0.0 — unreleased

A rewrite. 1.0.0 was a Spring Boot starter you embedded in an application of your own; 2.0.0 is
four modules, two of which run on their own, with a web interface and a command line of its own.
Everything 1.x could do it can still do, apart from the two features listed under
[Removed](#removed) — the 111 classes of the 1.x implementation were checked one by one against
this one.

The whole of this section is the comparison with 1.0.0.

### Where it runs

| | 1.0.0 | 2.0.0 |
|---|---|---|
| Shape | one starter jar, embedded in a host application, plus a runnable demo host (`greenfinger-ui`) | `greenfinger-core`, `greenfinger-cluster`, `greenfinger-api`, `greenfinger-shell` |
| Runnable on its own | no | the api server and the shell, each an executable jar |
| Web interface | a prebuilt bundle shipped under `static/ui` | Angular 21 (signals, Material, Tailwind), built from source in `frontend/` |
| Command line | none | 20 commands at the `greenfinger:>` prompt (`greenfinger-shell.sh`), a terminal that attaches to a running cluster and drives it exactly as the page does; `greenfinger-cli.sh` is a crawler that runs one verb and exits |
| Framework | Spring Cloud, `doodler-framework-*` | Spring Boot 4.1, no framework of our own |
| Start-up cost | Redis, PostgreSQL and Elasticsearch before the first page | nothing: H2 and an embedded Lucene index are the defaults |

`@EnableGreenfingerServer` is kept, and still explicit rather than auto-configured, so the api can
go on being embedded in somebody else's application.

### Added

**Three outputs, and they stack.** 1.x indexed into Elasticsearch or did nothing. 2.0 writes to
files (local disk or MinIO), to an index (embedded Lucene or Elasticsearch) and to a vector store
(embedded Lucene, Qdrant, Weaviate or Elasticsearch), in any combination. The file layer is always
on, because the database keeps metadata only and the other two are rebuilt from what it wrote.

**Replay, and file restore.** A finished crawl can be written into the index or the vectors again
without fetching the site — `replay --layers index,vector`. Files that were lost are fetched back
from the urls the rows record, one page at a time, and only the ones actually missing. This
replaces 1.x's `POST /index/sync`, which could only rebuild the whole index from the database.

**Images.** New in 2.0 — 1.x crawled text. Images come from `<img>`, from `srcset`/`<picture>` and
from `og:image`, filtered by declared size, media type, byte count and real decoded dimensions.
Identical bytes are stored once however many pages point at them, and the wording around each
picture is kept, so an image with no alt text is still findable by words.

**Semantic and cross-modal search.** Text vectors and image vectors, in separate collections
because the two are not comparable. The default provider runs `multilingual-e5-small` and
`SigLIP 2` locally, so there is no account to open; Ollama and OpenAI are configuration, not code.
Searching for pictures by describing them is one of the three search modes.

**The article, not the furniture.** Navigation, sidebars and cookie banners are dropped before
anything is indexed, by link density — the method boilerplate detection has used since Boilerpipe.
No model and no dictionary, so it works in any language, and CJK text is weighted so one threshold
means the same thing in either script.

**Javascript only when it is needed.** The default extractor fetches over plain http and starts a
browser only for pages that came back as an unrendered shell, so a site that does not need one
never starts one. 1.x had the same four engines but chose between them per catalog, in advance.

**Content deduplication.** 1.x deduplicated urls. 2.0 also fingerprints the content, exactly
(SHA-256) or approximately (SimHash with banded lookup), so the same article republished under a
second url is stored once.

**Sitemaps and robots.txt.** The urls a site publishes about itself are collected before the crawl
starts guessing: `Sitemap:` directives first, then `/sitemap.xml`, indexes followed one level down.
They are candidates, not exceptions — every one still goes through the frontier and the acceptors.

**Resume.** The frontier lives in RocksDB, so a crawl killed halfway carries on from where it
stopped. 1.x kept it in memory and, on restart, tried to reconstruct it from the single most
recently saved page, which is unrelated to what was pending; everything queued at the moment of
interruption was lost.

**A cluster that shares one crawl.** Nodes join by gossip (openspreader). A url found on a page is
dispatched to one node by consistent hashing, and the engine on the other side calls the same
function — that is the whole of the distribution, and it is why there is no join anywhere. The
per-process stores (a file database, RocksDB, a local blob directory, the embedded index and
vector store) are replicated so every node's copy is complete; a shared server needs none of it,
and which it is comes off the jdbc url. 1.x scaled by adding nodes that each ran their own crawl.

**Completion decided from shared counters.** Whether a crawl is over is a question about counters
every node can read, so every node reaches the same answer and the first to notice writes the flag
— no leader is a single point of failure for it. Administrative writes are the opposite and do go
through one writer, which is what removes the need for write stamps, content hashes and tombstones.

**Six databases.** H2, SQLite, MySQL, PostgreSQL, SQL Server and Oracle, each taken through an
end-to-end regression. 1.x shipped one schema script, in PostgreSQL syntax.

**Two roles.** `SUPPORT` sees everything and changes nothing; `ADMIN` may also create a catalog,
crawl, update, rebuild and delete. The search page can be handed to somebody without the buttons
that start and destroy work.

**Deleting, as a first-class operation.** By catalog or by version, by layer (files, index,
vectors, database), with a preview that counts what would go before anything goes.

**An account of every run.** A report file beside the pages for each node's share, plus one
`crawler_report` row per version holding the whole picture — counters, the cluster as it stood,
the stores written into, and the settings the run used. Storage and RocksDB usage are reported on
demand.

### Changed

**Versions.** `rebuild` opens a new version beside the old one instead of deleting first, and
`search_version` keeps the old one serving until the new one finishes — 1.x searched `max(version)`
and therefore found nothing at all between the start of a rebuild and the end of it. Versions are
now strictly per catalog; 1.x took the maximum per `cat`, so a catalog left at v5 became invisible
as soon as a sibling reached v6. Retention prunes the oldest, never the version being written or
the one search is serving.

**Deduplication store.** RocksDB, for both urls and content. 1.x used a Redisson bloom filter in
Redis, which is one more service to run and answers "probably" by design.

**Elasticsearch layout.** One index per catalog, `<prefix>-<catalogId>`, holding every version and
keeping them apart by a `catalogVersion` field. 1.x put everything in `webcrawler_resource_0`.
The vector stores and the embedded index carry the identical field, so all three are queried the
same way.

**The database keeps metadata only.** The page body lives in the file store and the row points at
it. 1.x kept the whole page in `resource.html`; a hundred thousand pages made that one column tens
of gigabytes, and neither of the database's jobs needs it.

**Schema.** `crawler_catalog_index` is merged into `crawler_catalog`. Ids are UUID text rather
than `bigint`, the same string in the database, the file system, MinIO, Elasticsearch and the
vector store. `url_hash char(64)` carries the unique constraint, because 1.x put it straight on
`url varchar(1000)`, which exceeds InnoDB's 3072-byte key limit under utf8mb4. Tables are mapped
with Spring Data JPA and the scripts in `docs/sql/` are generated from the entities; 1.x wrote its
SQL by hand.

**Search ranking.** Detail pages are pushed above listings, in Elasticsearch and in the embedded
index and in the vector store, by the same arithmetic. Deep paging goes past Elasticsearch's
10,000-result ceiling with a cursor.

**Login.** A signed, stateless token: any node verifies it by recomputing the signature, which is
what lets a proxy send a browser to whichever node is free. 1.x used HTTP Basic, and its `/logout`
was an empty method.

**Enums where there was free text.** `category` and `extractor` were free-text columns, so a typo
was discovered when the crawl went to build itself. Both are enums now, refused where they are
typed. The 1.x spellings `default` and `resttemplate` are still accepted for `restclient`, and an
unrecognised category becomes `other` rather than an error, so a catalog carried over still loads.

**Renames.** `WebCrawlerHandler`/`WebCrawlerService` → `CrawlerEngine`/`CrawlerLauncher`.
`ResourceManager`, one interface for everything → `CatalogStore` + `ResourceRecordStore`.
`WebCrawlerExecutionContextUtils`, a static map → `CrawlRegistry`, an injected bean.
`InterruptionChecker` → `CompletionChecker`, same semantics. The `WebCrawling` AOP aspect →
`WebCrawlerSemaphore` and an explicit check.

### Fixed

- A crawl that stopped at its configured `maxFetchSize` was treated as unfinished, so
  `search_version` never advanced and the search found nothing. A configured limit is now a normal
  ending; only an external interruption withholds the version.
- `rebuild` searched `max(version)` across a whole `cat`, hiding every catalog that had not caught
  up. Versions are per catalog.
- Between the start of a rebuild and the end of it, search returned nothing. `search_version`
  closes the window.
- Urls held by a node that stopped answering are told apart from a crawl that simply ran out of
  urls, so half a version is never published as a whole one.

### Removed

- **Built-in scheduling.** 1.x carried a delay queue and an Amber Job integration. Scheduling a
  crawl is `cron` and one command, and keeping a scheduler inside a crawler meant it had to be
  right about clocks, restarts and overlap as well.
- **Authenticated crawling.** The 1.x `CredentialHandler` and its extractor path are gone. Nothing
  in 2.0 signs into a site before crawling it.
- **`PUT /index/upgrade`**, which overlapped entirely with `replay`.

### Engineering

- JDK 17, Spring Boot 4.1, Spring Shell 4.0.
- JaCoCo line coverage of 80% is a build gate on all four modules; the build currently measures
  core 81.7, cluster 80.9, api 81.9, shell 87.6.
- One version number for the build, `<revision>`, resolved by the flatten plugin; the front end
  reads its own from `.env`, where every front-end setting now lives.
- `deploy/` is produced by the build and is not in the repository, so each launcher, config file
  and jar has exactly one copy and it is the one you edit.

### Upgrading from 1.0.0

There is no data migration. The schema, the ids and the Elasticsearch layout are all different,
and 2.0 keeps the page body outside the database, which 1.x rows do not have anywhere. Create the
catalogs again and crawl; a 1.x catalog definition maps field for field, and the legacy `extractor`
and `category` spellings are accepted.
