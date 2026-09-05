Greenfinger command line
========================================

Every command `greenfinger-cli.sh` accepts, generated from the command classes and checked against
them. Twenty commands in three groups.

Two things are true of all of them:

- **Every option is long form.** There are no one-letter options.
- **A catalog is addressed by its id**, never by its name. `catalog-list` is where the ids come
  from.


## Why the id and not the name
------------------------------

A name is unique, so looking a catalog up by it would work -- which is exactly the problem. A name
is editable. A script written against one is correct right up until somebody renames a catalog, at
which point it either fails or, worse, finds a different catalog that has since taken the name. The
id is minted once and never changes.

``` shell
./greenfinger-cli.sh catalog-list          # the ids
./greenfinger-cli.sh catalog-crawl --id=01a064a8-af7f-7000-b7b3-7628bd8a1fc3
```


## The four launchers
------------------------------

`deploy/` holds four executables and nothing else you have to run. They are four faces on the same
jar, not four programs: the same crawler, the same configuration, the same data store.

| | What it is | Runs until |
|---|---|---|
| `./greenfinger-cli.sh <command>` | One command, printed, done. | the command finishes |
| `./greenfinger-face.sh` | A prompt. Type commands, watch a crawl live. | you type `exit` |
| `./run-local.sh` | The server -- the REST api and the web app -- as background processes here. | `./run-local.sh stop` |
| `./run-docker.sh` | The same server, one container per node, plus the front end container. | `./run-docker.sh down` |

**All four read `deploy/run.conf`.** Which cluster to join, where to write, how much heap, how many
nodes: those describe the installation rather than the command, so they are not options on any of
them. Addresses and passwords live in `deploy/.env`. A prompt and a server started side by side are
therefore one installation, and each sees what the other crawled.

### The command line and the prompt

``` shell
./greenfinger-cli.sh catalog-crawl --id=<id>   # do one thing and exit
./greenfinger-face.sh                          # a session, and stay in it
```

**Two launchers, one program.** `greenfinger-cli.sh` runs the command on the line and exits.
`greenfinger-face.sh` opens a session: one jvm, one prompt -- `greenfinger:>` -- and every command
available inside it without the script name. They are the same implementation behind two entry
points, so the jar, the configuration, the `.env` file and the data store are found identically
whichever you typed; what differs is how long the process lives.

`./greenfinger-cli.sh` with nothing on the line prints `help` rather than opening a prompt: the
prompt has its own launcher now, so an empty line here is a question, not a session.

`help` lists the commands, `help <command>` explains one, and tab completes.

The difference between the two forms is what happens while a crawl runs. At the prompt the crawl
runs behind it: the live view is shown, `q` then return leaves the view without touching the crawl,
and `status` brings it back. On one line there is no prompt to go back to, so the command waits for
the crawl to finish and prints the summary.

### The server

``` shell
./run-local.sh                # the nodes -- the api and nothing else
./run-local.sh all            # the nodes and the front end, http://localhost:9700
./run-local.sh status         # what is up, and on which ports
./run-local.sh stop           # stop it all -- `down` means the same
```

**The front end is a process of its own**, on a port of its own, exactly as it is a container of
its own under `run-docker.sh`. The page and the api are two things: it serves the built app and
spreads `/v2` and `/actuator` across every node, so a browser talks to the cluster rather than to
whichever node somebody happened to type.

It needs the build (`npm run build:deploy` in `frontend/greenfinger-ui`) and `node` on the path.
Without either it says so and the nodes come up regardless. `GF_WEB=1` in `run.conf` makes it the
default so `all` is not needed every time; `GF_API_BASE_URL` writes the api address into the
page's `env.js`, and empty is right whenever the front end is forwarding to the api itself.

Nodes are numbered from `GF_BASE_PORT` (50080), so three nodes are 50080, 50081, 50082. Each gets
its own data directory under `deploy/data/node-N` and, unless `GF_DB_URL` points at a database
server, its own H2 file inside it.

``` shell
GF_NODES=3 ./run-local.sh     # three of them, sharing the crawl
```

### The server, in containers

``` shell
./run-docker.sh               # build the image and start GF_NODES containers plus the front end
./run-docker.sh status        # what is up
./run-docker.sh logs 2        # follow node 2
./run-docker.sh down          # stop and remove them
./run-docker.sh build         # build the image and stop there
```

The containers get fixed addresses on a network of their own and find each other by cluster name.
The front end container is at `http://localhost:9700` and forwards the api to whichever node
answers, so a browser talks to the cluster rather than to one node. `GF_WEB=0` leaves it out.

Two things a container needs that a local process does not: a database every node can reach
(`GF_DB_URL` pointing at PostgreSQL or MySQL -- a file database gives each container its own copy,
which is right for a crawl and wrong for defining a catalog in one place), and
`host.docker.internal` rather than `localhost` in any address meaning "the machine docker runs on".

### Stopping cleanly

`stop` and `down` signal and wait: an interrupted crawl closes its outputs and leaves its frontier
consistent, so a `resume` afterwards continues rather than starts over. Killing the processes
outright loses whatever the last flush had not written.


## Option forms
------------------------------

| Form | Example |
|---|---|
| long | `--id 01a064a8-af7f-7000-b7b3-7628bd8a1fc3` |
| long, joined | `--id=01a064a8-af7f-7000-b7b3-7628bd8a1fc3` |
| boolean | `--refresh` alone means `--refresh=true` |

There used to be five one-letter forms. They are gone. There are two parsers behind this -- the
prompt uses the shell's, a command on the command line uses Greenfinger's own -- and a letter that
meant different things in the two failed silently: an option written under one name and read under
another is ignored rather than rejected, so the crawl ran with a default nobody chose. Two of the
five were doing exactly that when it was last checked. `OptionStyleTest` now walks the annotations
and fails the build if a letter comes back, or if an option is added without saying what it accepts.


## Settings, and where they live
------------------------------

**None of these is a command line option.** They are in `run.conf` beside the launchers, and every
launcher reads the same file -- `greenfinger-cli.sh`, `greenfinger-face.sh`, `run-local.sh` and
`run-docker.sh`. They describe the installation rather than the command being
run: the same answer every time you type anything, and a different answer means a different node.
What varies per command is still on the line, which is what `--id` and `--node` are.

| Setting | Default | What it does |
|---|---|---|
| `GF_DATA_STORE` | `deploy/data` | One directory for everything this node writes |
| `GF_CLUSTER_NAME` | `default` | Which cluster to join; nodes agreeing on the name find each other |
| `GF_CLUSTER_PORT` | `22000` | And the port they elect on; both halves matter |
| `GF_CLUSTER_HOSTS` | `127.0.0.1` | Machines to knock on, comma separated. Only needed across machines |
| `GF_WORKER_ROOT` | `deploy/workers` | Where `--node=3`'s extra processes keep their data |
| `GF_LOG_DIR` | `deploy/logs` | Where the log and pid files go. Separate from `GF_DATA_STORE` so logs can live on a disk you are happy to fill |
| `GF_IDLE_TIMEOUT` | `2m` | How long the counters may stand still before the crawl is wound up |
| `GF_COMPLETION_CHECK_INTERVAL` | `5s` | How often that is asked |
| `GF_MAX_CONSECUTIVE_FAILURES` | `20` | How many fetches in a row may come back with nothing before the crawl gives up on the site. 0 never gives up on that alone |
| `GF_ADAPTIVE_BROWSER` | `playwright` | Which engine `adaptive` renders with when plain http came back as a shell. `playwright`, `htmlunit` or `selenium` |

Anything already exported wins, so a one-off needs no editing:

``` shell
GF_DATA_STORE=/tmp/scratch ./greenfinger-cli.sh catalog-list
```

Secrets and the addresses of databases and stores are in `.env` instead -- two files because they
have different lifetimes: `run.conf` describes a run, `.env` describes the machine, and only one of
them is dangerous to share. Typing a removed option gets a message naming the setting that replaced
it rather than an unknown-argument error.

**`GF_DATA_STORE` is one directory and everything goes under it**, split into two halves that are
worth keeping apart:

| Half | Holds | Losing it costs |
|---|---|---|
| `system/` | the frontier, the two dedup stores, and the H2 or SQLite file when no database server was configured | a resume, and a re-crawl of what it was in the middle of |
| `user/` | `assets/` (pages, text, images -- the `file` output), `index/`, `vector/` | the search itself; this is the half to back up |

`system/` is the crawler's working state and an advanced setting -- it is always local, because the
frontier and the dedup filters are RocksDB whatever else is configured. `user/` is what was
crawled, and it is what a search reads: **a search still works with `system/` deleted.**

Only `system/` is created by the launcher. `user/` is left to whoever writes into it, so a node
whose assets go to MinIO and whose search is Elasticsearch and Qdrant has no `user/` directory at
all rather than three empty ones -- these directories describe the local stores, and nothing else.

The default is `data/` beside the launcher, so where you happened to `cd` to before typing the
command does not decide where a crawl is written. A database configured on purpose is left alone;
only the zero-install H2 file moves.

**The prompt and the server are one installation.** They read the same `run.conf`, so
`greenfinger-face.sh` and `run-local.sh` open the same database and the same data store: a
catalog created at the prompt is in the front end the moment it is refreshed, and a crawl started
in the browser is what `status` at the prompt is watching. With the zero-install H2 file that works
because the url carries `AUTO_SERVER=TRUE` -- the first process to open it serves it and the rest
connect over tcp, any number of them, at the price of that first process being the server.

**`GF_CLUSTER_NAME` defaults to `default`**, so two terminals on one machine are one cluster
without anybody saying so, and naming it is how you keep two crawls apart rather than how you join
them up. `run-local.sh` and `run-docker.sh` use `greenfinger-local` and `greenfinger-docker`
instead when the file leaves it empty, so a set of containers left running does not merge with
nodes started by hand.

**`GF_CLUSTER_HOSTS` is only needed to reach another machine.** The configuration default is
`127.0.0.1`, which finds the other processes on this one. Naming hosts also unpins the address this
node advertises: it is fixed at the loopback so that several processes on one machine do not tell
each other to use an interface none of them can reach, and that default is exactly wrong the moment
another machine has to dial this one. Set `GF_CLUSTER_ADVERTISE_HOST` to override either way.

``` shell
./greenfinger-face.sh                                   # the prompt; it takes no arguments
./greenfinger-cli.sh catalog-crawl --id=<id> --node=3
```

`--node` on the launcher line is read before the jvm starts, because starting processes is not
something a command inside one can do. It is left on the line as well -- `catalog-crawl` declares
it too -- so what you typed and what the jvm receives stay the same thing.

The first process is node 1 and keeps the data directory it has always used, so nothing you crawled
before disappears when you ask for more nodes. The other n-1 have no prompt: they join the same
cluster, take their share of the urls, and are stopped when the first one exits. Each gets its own
directory, because every node keeps a complete copy and replication is what keeps the copies the
same -- two nodes on one H2 file is not a shared cache, it is a refused connection.

### `--node` inside a session

Typed at the prompt, `catalog-crawl --id=<id> --node=3` forks the extra nodes itself:

```
greenfinger:> catalog-crawl --id=<id> --node=3
Started 2 worker node(s); this session is node 1.
```

Two, not three, because the session is already a node -- and, having started first and taken the
cluster port, it is already the leader. The workers are the same process the launcher would have
started (`greenfinger-cli.sh --as-worker`, one directory each under `workers/`), so a worker forked
by the prompt and one forked by the launcher are identical.

They are stopped when the crawl that asked for them finishes, so a session that crawls twice with
`--node=3` runs three nodes twice rather than three and then five. Detaching from the live view
with `q` leaves them alone: the crawl is still going and still wants them.

Leaving the session releases the cluster port, and the next node to take it becomes leader -- which
will be one of the workers if any are still running. Starting the prompt again then joins an
existing cluster rather than founding one, and it is a member like any other until the leader
leaves.

`--node` needs the launcher. A jar started directly has none, says so, and crawls on one node.


## Where things live
------------------------------

```
deploy/
  greenfinger-cli.sh      one command, run once
  greenfinger-face.sh     the greenfinger:> prompt
  run-local.sh            the server: the api and the front end, one node or several
  run-docker.sh           the same, in containers
  run.conf                what every launcher reads: nodes, cluster, data store, ports
  lib/                    the executable jars
  config/                 application.yml and its profiles, outside the jar
  .env                    secrets, never committed
  data/                   the default GF_DATA_STORE
    system/               frontier, dedup stores, the H2 file
    user/                 what was crawled, and what a search reads
      assets/{catalogId}/v{n}/   settings.json, reports/, pages/, images/
      index/{prefix}-{catalogId}/
      vector/{collection}_{dimensions}/
  logs/
```

Under `user/`, assets and index are one directory per catalog, addressed by the catalog's **id**
and never by its name: a rename then moves nothing on disk. Vectors are not split per catalog --
a collection is one embedding width (`greenfinger_text_384`), and which catalog and which version
a vector belongs to is a field on the point, filtered at query time. That is how Qdrant and
Weaviate are addressed too, so the embedded store and the servers behave the same way.

`index/` and `vector/` only exist when the embedded Lucene stores are in use; with Elasticsearch,
Qdrant or Weaviate configured there is nothing local to see.

The configuration is outside the jar on purpose: a setting changes with an editor, not with a
rebuild.


## Catalog
------------------------------

### `catalog-save`

Create a catalog, or change one. **The one command that asks rather than reads.**

| Option | Accepts |
|---|---|
| `--id` | An existing catalog id to update; omit to create one |

Seventeen settings do not fit on a line anybody types twice, and as flags they came with the
failure mode that gives command lines a bad name: mistype one and the crawl starts anyway, with a
default nobody chose. Asked in order, each with what it accepts and what it currently says, the
whole thing is return pressed seventeen times and the two that matter typed in the middle.

```
greenfinger:> catalog-save
New catalog
Return keeps what is in the brackets. 'cancel' abandons the whole thing.
  url            (http:// or https://) []: https://example.com
  name           (unique; defaults to the domain) [example]:
  cat            (your own label) [default]:
  ...
Saved 'example'
Id: 01a064a8-af7f-7000-b7b3-7628bd8a1fc3
  start now      (no | crawl | update | rebuild) [no]:
```

**`cancel` at any question abandons the whole thing** and writes nothing: half a catalog written
because somebody changed their mind at question twelve is worse than no catalog. Input that simply
ends counts as a cancel too, so a piped invocation never hangs waiting for an answer.

It needs a terminal, and says so rather than hanging when it has not got one.

The questions, in order:

| Setting | Accepts | Default |
|---|---|---|
| `url` | http:// or https:// | (required) |
| `name` | unique text | the domain |
| `cat` | your own label | `default` |
| `start-url` | a url under `--url` | = url |
| `sitemap-url` | a url, or empty to discover it | (empty) |
| `include` | ant path pattern, `,` for several | `**.<domain>` |
| `exclude` | ant path pattern, `,` for several | (empty) |
| `encoding` | UTF-8 \| GBK \| … ; only a fallback | UTF-8 |
| `extractor` | adaptive \| restclient \| htmlunit \| playwright \| selenium | adaptive |
| `max-size` | 1 or more saved pages | 10000 |
| `depth` | -1 for no limit, or 1 or more | -1 |
| `duration` | minutes, 1 or more | 30 |
| `interval` | milliseconds between fetches, 0 or more | 1000 |
| `retry` | retries per url, 0 or more | 1 |
| `url-dedup` | rocksdb (built in), or a filter of your own | rocksdb |
| `images` | true \| false | true |
| `output-types` | `file+index+vector`, joined with `+` (file is always on) | file |
| `content` | `text+image` \| `text` | text+image |
| `max-versions` | 1 or more | 10 |

`options` prints this same table without asking anything.

Three of them are worth a sentence. **`url-dedup` decides how urls already seen are remembered.**
`rocksdb` keeps every url it has seen, which is exact and durable, and costs a key on disk per
url. It is the only one that ships. The field is a name rather than a fixed choice: an
application that supplies its own `WebCrawlerComponentFactory` answers to whatever name it
likes, and an unknown one is refused when the crawl starts rather than when it is typed.

### `catalog-list`

Every stored catalog: id, name, url, category, outputs, version, and which version search is
serving. No options. This is where every other command's `--id` comes from.

### `catalog-show`

One catalog's whole definition -- every setting, read only.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list. **Omit it and you get whichever crawl is running**, which is usually the one you wanted while watching one. |

What it prints is the catalog as the crawler will read it, not the row as it is stored: a setting
left blank shows the default that is actually in force rather than a blank. `catalog-list` is the
summary and this is the detail; `catalog-save --id=<id>` shows the same values but in an editor
rather than a table. What a catalog's crawls *did* is `crawler-report`.

### `catalog-delete`

Removes the definition. The data it produced is untouched -- that is `delete`.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |

A crawl running for that catalog is asked to stop first. Getting that order wrong is worse than it
sounds: a crawl reads what to fetch from the frontier rather than from the catalog table, so
deleting the row underneath it does not stop it -- it keeps fetching, for a catalog that no longer
exists, while disappearing from the running list, and never gives back the permit every later crawl
needs.

### `catalog-cats`

Every category in use. No options.

### `versions`

Every version of one catalog, newest first: how many pages and images each holds, which one is
current, which one search is serving, and when it was first built and last run.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |

### `crawler-report`

The stored report of one version.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--version` | Which version; omit for the newest one with a report |

**The report is the dashboard, frozen.** One row per `(catalog, version)` in `crawler_report`,
written by the node that started the run, holding as json:

| Section | What is in it |
|---|---|
| `run` | action, refresh, start and end, why it stopped, what it left over |
| `dashboard` | every counter the live view was showing, at the moment it ended |
| `nodes` | the same counters again, per node -- the only thing that says whether one node did all of the work |
| `cluster` | members, leader, channel metrics, buffer levels, split brain |
| `database` | product, version, url (without its query string), rows in this version |
| `storage` | local or MinIO, where, how many files, how many bytes |
| `outputs` | the index and the vector store this version was written to |
| `settings` | `settings.json` whole -- the definition the crawl actually ran under |

Everything it can find out is written down, including the things that are empty: a section that is
missing reads as "nobody looked", and a section that says zero reads as what happened.

There is also a report per *run* beside the pages themselves, written by every node. The two are
different documents on purpose. The file report is one node's account of its own share and survives
the database being thrown away; this row is one per version, describes the whole crawl, and can be
queried. Re-running a version -- an update, a resume -- rewrites the row and moves `updated_at`;
`created_at` keeps saying when that version was first built. Deleting a version's `db` layer takes
its report with it.


## Crawl
------------------------------

Every verb takes `--id`. None of them takes a url: a catalog is defined by `catalog-save`, and
running one is a separate thing from defining it.

### `catalog-crawl`

Crawl a catalog from its start url.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--node` | How many processes to run on this machine, 1 or more. Read by the launcher |
| `--threads` | Worker threads on each node, 1 or more; default 16 |

### `update`

Continue: take the urls that have appeared since.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--from` | A url to start from instead of where the last run stopped |
| `--refresh` | true \| false; true also revisits pages already crawled and merges what changed. Default false |
| `--threads` | Worker threads on each node, 1 or more; default 16 |

`--refresh` does **not** mean "start again from the start url" -- that is `rebuild`. Both settings
stay on the same version and keep the url filter populated. The difference is only whether pages
already saved are fetched again:

- `--refresh=false` (the default) takes only urls that have never been seen. Cheap.
- `--refresh=true` also re-fetches every page already stored and merges whatever changed. Costs one
  fetch per known page, and writes nothing for the ones that came back the same.

A merge is refused up front when `max-size` is smaller than what is already stored, because it
could not possibly finish -- and a merge that stops halfway is worse than one that refuses: the
pages it never reached silently keep their old content while the ones it did reach are up to date,
and nothing records which is which.

### `resume`

Continue a crawl that was paused or interrupted.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--threads` | Worker threads on each node, 1 or more; default 16 |

The same thing as `update --refresh=false`, under the name that says what it is for. The version is
unchanged and the url filter is still populated, so the run picks the frontier up where it was left
and skips everything already saved.

### `rebuild`

Start a new version and crawl the whole site again.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--threads` | Worker threads on each node, 1 or more; default 16 |

Nothing is deleted. The previous version keeps serving search until this one finishes, which is the
one thing 1.x could not do -- it incremented the version at the start of a rebuild while search took
the maximum, so search went blank for the whole rebuild.

### `pause`

Stop a running crawl where it is.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |

Asked, not killed: the crawl winds down at its next check, so whatever is in flight still reaches
the output channel and the frontier stays consistent. Which is what lets `resume` carry on
afterwards without re-fetching anything.

### `status`

Watch the crawl that is running.

| Option | Accepts |
|---|---|
| `--all` | true \| false; true adds a row per node. Default false |

The live view: a progress bar against whichever limit is nearer, and the counters, redrawn in place.
**`q` then return stops watching. It does not stop the crawl** -- `pause` does that. With nothing
running it prints every catalog and its state instead.

`--all` adds a row per node underneath the totals. Across a cluster it is the only thing that says
whether one node is doing all of the work: the totals say the crawl saved forty-four pages, and only
this says whether one node saved forty of them because the other two spent the run unable to reach
the site.

### `delete`

Remove data from any combination of the four stores. **Three scopes, and they are three different
operations rather than one with three spellings.**

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--version` | One version number to remove, 0 or more |
| `--keep-latest` | Keep the newest n versions and remove the rest, 1 or more |
| `--all` | true \| false; every version goes, the index stays. Default false |
| `--purge` | true \| false; every version goes and the index is dropped too. Default false |
| `--layers` | `db` \| `file` \| `index` \| `vector` \| `all`, joined with `+`. Default all |
| `--dry-run` | true \| false; true reports and deletes nothing. Default false |
| `--force` | true \| false; true allows removing the version search is serving. Default false |

| Scope | What it does |
|---|---|
| `--version=<n>`, `--keep-latest=<n>` | **by version.** Those versions' documents, points, rows and files. Everything else, including the index itself, is untouched |
| `--all=true` | **every version.** The catalog is emptied and can be crawled again into what it already had: the index is still there, the collections keep their other catalogs, the tables keep everybody else's rows |
| `--purge=true` | **the catalog.** The same, and the index is dropped rather than emptied |

The index is the only store where the last two differ, because it is the only one that belongs to a
single catalog. A vector collection is shared by every catalog -- its name carries the width of the
vectors, not the catalog -- so is a table, and a directory is emptied by removing its contents. For
those three, cleaning and purging are the same statements.

Why it matters: emptying an index is a delete by query, which marks documents and gives the space
back only when segments merge. Dropping it is immediate, complete, has nothing to merge afterwards,
and takes with it any documents belonging to versions nothing else remembers.

Whole-catalog deletes issue **one statement per layer**, not one per layer per version -- the report
is still read version by version because the counts are taken first.

One of the four is required: there is no default, because every possible default is somebody's data.
Start with `--dry-run=true`, which prints exactly what would go, per version and per layer, and
removes nothing.

**`--force`** allows removing the version search is currently serving. Without it that version is
refused, which is the one safety net between a typo and a search that returns nothing. It does not
override the other refusal: a version being crawled right now is never removed, forced or not.

Neither form touches the definition. `catalog-delete --id=<id>` removes that, and the two are
deliberately separate: a catalog whose data has been cleared out is still a catalog you can crawl
again.

### `replay`

Rebuild the index, the vectors, or the files of a version from what is already stored.

| Option | Accepts |
|---|---|
| `--id` | The catalog id, from catalog-list |
| `--version` | Which version, 0 or more; default the current one |
| `--layers` | `index` \| `vector` \| `file`, joined with `+`. Default `index+vector` |

`index` and `vector` are rebuilt from the pages on disk and the rows in the database -- no fetching,
no re-crawl. Turning on vector search for a catalog crawled without it is a replay, not a re-crawl.

`file` is different and is the one to be careful with: it re-fetches, from the original urls the
database kept, any page or image whose file is missing. Across a cluster it is not sliced but
broadcast, because every node holds its own copy and only that node knows which of its files are
gone. It is the one layer that can come back incomplete -- a page taken down since the crawl cannot
be restored at all -- so the command says out loud how many pages and images it wrote, how many were
already there, how many were unreachable, and how many came back different from what was stored.

`--layers` is taken literally here. Unlike a crawl, naming `index` does not quietly add `file`:
that would turn `replay --layers=index` into a second crawl of the whole site.

### `test-url`

Fetch one url and report what came back.

| Option | Accepts |
|---|---|
| `--url` | http:// or https:// |
| `--extractor` | adaptive \| restclient \| htmlunit \| playwright \| selenium; default adaptive |

### `options`

Every catalog setting, what it accepts and its default. No options. The same table
`catalog-save` walks through.


## Search
------------------------------

Search never touches the database: the index carries its own copy of the metadata, so a catalog
table that has been emptied does not take the search results with it. Which versions are looked at
comes from each catalog's `search_version`, so a rebuild in progress is invisible until it finishes.

### `search`

| Option | Accepts |
|---|---|
| `--query` | Words to look for |
| `--id` | A catalog id to search within; omit for all of them |
| `--size` | How many results, 1 or more; default 10 |
| `--image` | true \| false; true finds pictures by describing them and shows where they are. Default false |

Words by default. There is no `--semantic`: two search commands wearing one name, told apart by a
flag, meant every result table had to be read twice -- once for what it said and once for which
engine had produced it. Pictures are the one genuinely different question, and they get the flag.

### `index-info`

The full text index: where it is, its analyzer, whether it exists, how many documents each catalog
and version put in it, and every index under the prefix. No options.

One command rather than the two there were. "How many documents" and "which indices exist" are the
same question at two zoom levels, and answering them separately meant running both and reading them
side by side to discover that the count was zero because the crawl had written to a different index
than the one being counted.

### `vector-info`

The vector store: which one, where it is, the two collections, the chunking settings, and how many
points each catalog and version put in them. No options.


## Two engines, and nothing to install
------------------------------

Both `index` and `vector` are embedded by default, in Lucene, in the same process. A fresh clone
crawls a site and then searches it -- words and meaning both -- with nothing installed and no
account opened.

| Output | Default | The other option |
|---|---|---|
| `index` | Lucene, in `data/user/index` | Elasticsearch (`GF_INDEX_PROVIDER=elasticsearch`) |
| `vector` | Lucene, in `data/user/vector` | Qdrant or Weaviate (`GF_VECTOR_STORE=qdrant`) |

It is the same engine at both ends rather than a toy and a real one: Lucene 9 is the version
Elasticsearch 8 is built on, and Lucene's HNSW is what Elasticsearch's own knn search uses. The
documents are identical too -- the same fields, the same `catalogVersion` filter, the same
one-index-per-catalog naming -- so moving from one to the other is a `replay`, not a migration:

``` shell
GF_INDEX_PROVIDER=elasticsearch ./greenfinger-cli.sh replay --id=<id> --layers=index+vector
```

**One index per catalog**, named `<prefix>-<catalog id>`, holding every version of that catalog.
Three things get better: deleting a catalog is dropping an index rather than a delete-by-query that
only marks documents; a catalog can have its own analyzer, which matters the moment one site is
Chinese and another is not; and a search that names its catalogs reads that many indices instead of
filtering the whole corpus. The name comes from the id and never the catalog's name, for the same
reason every command takes `--id`.

**The analyzer** is `standard` by default, which is the right answer for English and is what is
mostly crawled. Chinese is a capability rather than the expected case: set `GF_LUCENE_ANALYZER` to
`smartcn` for a real segmenter (the embedded counterpart of installing IK into an Elasticsearch
server) or to `cjk` for bigrams, which need no dictionary and cover Japanese and Korean too. On the
Elasticsearch side the equivalent is `GF_ES_ANALYZER=ik_max_word`, with the IK plugin installed.

**Across a cluster** the embedded engines are one copy per node, exactly like a local blob
directory -- so a page indexed on one node is missing from the others' answers, and a url is
dispatched to exactly one node. Documents and vectors are therefore copied to every node as they
are written, on their own channel. It works, and it is not free: the extracted text travels once
per node on top of the copy already going over the blob channel. A cluster that crawls seriously
wants a real Elasticsearch, and the startup report says so.


## Everything at a glance
------------------------------

| Command | Options |
|---|---|
| `catalog-save` | `--id` |
| `catalog-list` | |
| `catalog-show` | `--id` |
| `catalog-delete` | `--id` |
| `catalog-cats` | |
| `versions` | `--id` |
| `crawler-report` | `--id` `--version` |
| `catalog-crawl` | `--id` `--node` `--threads` |
| `update` | `--id` `--from` `--refresh` `--threads` |
| `resume` | `--id` `--threads` |
| `rebuild` | `--id` `--threads` |
| `pause` | `--id` |
| `status` | `--all` |
| `delete` | `--id` `--version` `--keep-latest` `--all` `--purge` `--layers` `--dry-run` `--force` |
| `replay` | `--id` `--version` `--layers` |
| `test-url` | `--url` `--extractor` |
| `search` | `--query` `--id` `--size` `--image` |
| `index-info` | |
| `vector-info` | |
| `options` | |
| `help` | |


## A first run, start to finish
------------------------------

``` shell
./greenfinger-face.sh                     # a session

greenfinger:> catalog-save                 # answer, or press return, seventeen times
                                           # it prints the id
greenfinger:> catalog-crawl --id=<id>              # q leaves the view; the crawl keeps going
greenfinger:> status                       # come back to it
greenfinger:> pause --id=<id>              # stop it where it is
greenfinger:> resume --id=<id>             # carry on

greenfinger:> versions --id=<id>           # what there is
greenfinger:> crawler-report --id=<id>     # what it cost
greenfinger:> search --query=example       # what is in it
```
