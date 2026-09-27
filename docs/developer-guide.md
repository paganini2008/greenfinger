---
title: "Developer guide"
---

Greenfinger for developers
========================================

Everything below is a seam the product already uses itself. Nothing here is a plugin API bolted on
the side: the built-in url filters, extractors and stores are ordinary implementations of these
interfaces, registered the same way yours would be.

There are three ways in, and it is worth knowing which one a change needs.

| | How | Reaches |
|---|---|---|
| **A setting** | `.env`, or a field on the catalog | Everything already written, configured differently |
| **A bean** | publish one; the shipped default backs off | One piece of the crawler, for the whole installation |
| **A class name** | on the catalog, instantiated by name | One piece, for one catalog |

A bean wins because every default is `@ConditionalOnMissingBean`. Declare your own and the
shipped one is not created — no registration, no ordering file, no property to set.

```java
@Bean
UrlPathAcceptor mySiteRules() { ... }        // added to the chain
@Bean
WebCrawlerComponentFactory myFactory() { ... }  // replaces all of it
```


Reading a page
----------------------------------------

### Extractor — how a page is fetched

`com.github.greenfinger.core.component.extractor.Extractor`

Five ship: `restclient` (plain http), `htmlunit`, `playwright`, `selenium`, and `adaptive`, which
fetches with http and hands the page to a browser only when what came back looks like a shell.
A catalog names one in `extractor`.

`adaptive` is the default and tries Playwright first, HtmlUnit second. HtmlUnit is pure java and
needs nothing installed, which is why it is the one behind: a container that cannot run a real
browser still renders.

### RenderingDetector — what counts as a shell

Constructed with two lengths (`GF_ADAPTIVE_MIN_TEXT`, `GF_ADAPTIVE_SHELL_TEXT`). Replace the
extractor to replace the judgement.

### ContentExtractor — the text inside a page

`com.github.greenfinger.core.engine.ContentExtractor`

Finds the article and drops the furniture. It is also the one door for text that is *not* html:
`extract(fileType, content, url, encoding)` sends anything that is not a page to a document
parser.

### DocumentContentParser — pdf, Word, Excel, and the rest

`com.github.greenfinger.core.document.DocumentContentParser`

A site is not only its html. The handbook is a pdf, the price list is a spreadsheet, the release
notes are markdown, and a crawl that follows `<a>` into more html walks past all of it.

Two halves, and they are deliberately separate:

- **Collecting.** `PageParser.extractDownloadedFiles(document)` records every link whose extension
  is a document, as `DownloadedFile{url, fileType, linkText}` on `CrawledPage.downloadedFiles`.
  This happens on every crawl already. Nothing is fetched.
- **Reading.** A `DocumentContentParser` turns those bytes into text. **Only txt, markdown, csv
  and the other already-text formats have one that ships** (`PlainTextDocumentParser`). Pdf, Word
  and Excel each mean another dependency with its own licence and its own appetite for memory, and
  that is the application's decision rather than the crawler's.

```java
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

A format nothing reads comes back empty rather than throwing: a site linking a pdf is not a broken
site, and a crawl that stopped over one would be the wrong answer.

> **The charset is yours to get right.** `encoding` is what the catalog's `pageEncoding` says,
> not what the server declared -- html carries a meta tag the extractor sniffs and a plain file
> carries nothing. A server can be wrong about it: Debian publishes a GB2312 file as
> `charset=utf-8`. `PlainTextDocumentParser` decodes strictly first and logs the file and the
> charset when the bytes do not fit, then falls back to a lenient decode so the crawl continues.
> If your parser reads bytes as text, do the same rather than producing a page of U+FFFD quietly.

> **About the extensions that are filtered.** `AssetUrlPathAcceptor` refuses to *crawl a pdf as a
> page*, and that stays true whether or not you have a parser for one. The two paths do not
> overlap: the acceptor decides what is followed as a page, `extractDownloadedFiles` records what
> is not. Taking `pdf` out of `GF_SKIP_EXTENSIONS` does not turn document parsing on — it only
> spends a request discovering that a pdf is not html, and that request counts towards
> `maxConsecutiveFailures`.


Deciding what to crawl
----------------------------------------

### UrlPathAcceptor — whether a link is followed

`com.github.greenfinger.core.component.acceptor.UrlPathAcceptor`

Six ship, in `Ordered` sequence, first refusal wins: domain scope, start-url prefix, the asset
check, robots.txt, depth, and the catalog's path patterns. The first two are boundaries and cannot
be removed; the rest narrow what is inside them.

A catalog adds its own by class name in `urlPathAcceptor`; an application adds one for every
catalog by publishing a bean.

### ExistingUrlPathFilter — url dedup

`rocksdb` ships and is the only one, on purpose: a bloom filter answers "seen" with a probability
rather than a fact, and about one page in a hundred would go missing silently. A catalog names its
filter in `urlPathFilter`, so an application that wants a different trade supplies its own factory.

### ContentDedupFilter — two urls, the same page

`sha256` and `simhash` ship. Configured per catalog.

### CompletionChecker — when a crawl is over

`com.github.greenfinger.core.component.CompletionChecker`

Two ship: one counts what was saved against `maxFetchSize`, one watches the clock against
`fetchDuration`. Every node runs them against shared counters and the first to notice writes the
reason, so this is not the leader's decision.

### CrawlFrontier — the queue of what is left

RocksDB backed, one per catalog and version. Replace it through the component factory.


Where the results go
----------------------------------------

### OutputChannel — a place crawled pages are written

`com.github.greenfinger.core.output.OutputChannel`

Three ship, and they stack: `file` (always on), `index`, `vector`. A catalog picks them in
`outputTypes`.

### BlobStore — pages and images as bytes

`local` and `minio` ship, with an identical layout, so moving between them needs no rewrite.

### Searcher / IndexAdmin — full text

`lucene` (embedded, nothing to install) and `elasticsearch`.

### VectorStore — meaning and pictures

`lucene`, `elasticsearch`, `qdrant`, `weaviate`.

### EmbeddingClient — what turns text into a vector

`local` (ONNX in this process, no account), `ollama`, `openai`.

### ResourceRecordStore / CatalogStore — the metadata

JPA backed. `CatalogStore` has three modes: memory, a json file, and the database.


The cluster
----------------------------------------

### DeletionBroadcast — telling peers to remove what does not replicate

Rows, blobs and vectors replicate themselves. The embedded index and the RocksDB directories do
not, so the instruction travels instead.

### LeaderGateway — where an administrative write goes

Saving a catalog, deleting a version, publishing a finished version: all to the leader, wherever
it was asked for. On a cluster of one it is a plain method call.


Starting data
----------------------------------------

### initial_catalogs.json — the catalogs a fresh install already has

`classpath:initial_catalogs.json` is read once at startup and every catalog in it that is not
already stored is defined. Matching is by name, so a second start defines nothing and editing a
catalog afterwards is never undone. Point `GF_INITIAL_CATALOGS` at your own file to ship a
different set; set it empty and none are loaded.

```json
[
  {
    "name": "My site",
    "url": "https://example.com/docs/",
    "cat": "tech",
    "description": "Why this one is worth having; stripped before the catalog is saved.",
    "maxFetchSize": 200,
    "depth": 2,
    "fetchInterval": 1000,
    "outputTypes": ["FILE", "INDEX"]
  }
]
```

Every field a catalog has is allowed here, including `extractor`, `pathPattern` and
`excludedPathPattern`. The shipped file is not a demo reel -- each entry exists because it
exercises something no other entry does, and a new one is worth adding on the same terms.

Replacing everything at once
----------------------------------------

`WebCrawlerComponentFactory` builds every component above. Publish your own and none of the
defaults are created:

```java
@Bean
WebCrawlerComponentFactory webCrawlerComponentFactory() {
    return new MyFactory();
}
```

That is the whole extension story: one interface for each decision the crawler makes, and one
factory that makes all of them.
