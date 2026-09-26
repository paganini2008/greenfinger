Greenfinger command line
========================================

Two programs, and the difference is what they are for.

| | What it is | Use it to |
|---|---|---|
| `./greenfinger-cli.sh` | A crawler that runs one command and exits | Crawl something, from a script or by hand |
| `./greenfinger-shell.sh` | A terminal on a cluster somebody is running | Look at it, search it, drive it |

The prompt crawls nothing itself. It joins the cluster your nodes are in and asks them, exactly as
the web page does -- so the nodes have to be running first (`./run-local.sh` or
`./run-docker.sh`). The one-line form is the opposite: it is a crawler, it needs nothing else
running, and when the command is done the process is gone.

Every option is long form. There are no one-letter options.


Crawling: `greenfinger-cli.sh`
========================================

``` shell
./greenfinger-cli.sh --cluster=nightly crawl --url=https://books.toscrape.com
```

That is the whole of the quick start: it defines a catalog for the url, crawls it, draws a live
dashboard while it runs, and prints the id and what to do next when it finishes. Nothing has to be
installed -- the pages go on disk and the metadata into an H2 file beside the launcher.

## Naming the cluster

`--cluster=<name>` is required, and there is no default. A cluster crawls one catalog at a time,
so the name is how two runs are kept apart -- and how a second pair of hands finds the first.

``` shell
--cluster=<name>          which cluster this run is           (required)
--cluster-port=<port>     and its port                        (22000)
--cluster-hosts=a,b       where to knock, for other machines  (127.0.0.1)
--join=<name>             crawl inside a cluster already running
```

Two runs at once need two ports:

``` shell
./greenfinger-cli.sh --cluster=books crawl --url=https://books.toscrape.com
./greenfinger-cli.sh --cluster=quotes --cluster-port=22001 crawl --url=https://quotes.toscrape.com
```

Start a second run on a port somebody already holds and it stops before doing anything, rather
than quietly joining a stranger's cluster and queueing behind their crawl. If joining is what you
meant, say so -- `--join` adds this process to that cluster as another crawler, fetching pages
like every node in it:

``` shell
./greenfinger-cli.sh --join=nightly crawl --id=<id>
```

## Which catalog

Three ways of naming the same thing:

``` shell
--id=<id>                 a catalog id, from the prompt or the page
--name=<name>             its name
--url=<url>               its url -- and if there is no catalog for it yet, one is created
```

A url is not an identity: if two catalogs crawl the same site with different settings, `--url`
alone cannot tell them apart and asks you to name one. `--name` with `--url` creates a catalog
under that name when there is not one already.

## The verbs

``` shell
crawl      --id=<id>            Crawl from the start url
update     --id=<id>            Take the urls that have appeared since
merge      --id=<id>            Update, and revisit the pages already held
rebuild    --id=<id>            A new version, the whole site again; the old one keeps serving
resume     --id=<id>            Continue one that was paused or interrupted
pause      --id=<id>            Stop a running crawl where it is
replay     --id=<id> --layers=index    Rebuild an output from what is already stored
help                            These verbs
```

Everything else -- listing catalogs, searching, reports, deleting -- lives in the prompt. Asking
the one-line form for one of those is refused by name rather than ignored.

## Options the verbs take

``` shell
--threads=<n>      worker threads on each node             (16)
--node=<n>         run n processes on this machine         (1)
--from=<url>       for update, where to carry on from
--refresh=true     for update, revisit what is already held (that is what merge is)
--layers=<list>    for replay: index, vector, file, joined with +
```

`--node=3` starts two more processes beside this one. They join the same cluster, take their
share of the urls, and stop when the crawl does.

## Examples

``` shell
# crawl a site you have never crawled before
./greenfinger-cli.sh --cluster=nightly crawl --url=https://books.toscrape.com --name=books

# take what has appeared since, every night
./greenfinger-cli.sh --cluster=nightly update --id=01a0db42-9ce2-7000-b879-bb228a88073f

# revisit what is held and merge the changes
./greenfinger-cli.sh --cluster=nightly merge --id=01a0db42-9ce2-7000-b879-bb228a88073f

# a fresh version of the whole site; searches keep seeing the old one until this finishes
./greenfinger-cli.sh --cluster=nightly rebuild --id=01a0db42-9ce2-7000-b879-bb228a88073f

# the site has not changed, but the index was lost: rebuild it from the database
./greenfinger-cli.sh --cluster=nightly replay --id=<id> --layers=index+vector

# four processes on this machine, sharing one crawl
./greenfinger-cli.sh --cluster=nightly crawl --id=<id> --node=4
```

Anything that goes wrong stops the command and exits non-zero: a catalog id that is not there, a
crawl already running, a port that belongs to somebody else. There is nothing to ask, so there is
nothing to carry on with.


The prompt: `greenfinger-shell.sh`
========================================

``` shell
./run-local.sh                                      # the nodes
./greenfinger-shell.sh --cluster=greenfinger-local  # the terminal
greenfinger:> catalog-save
greenfinger:> status --all=true
greenfinger:> exit
```

`--cluster=<name>` is required here too: a terminal can attach to any cluster, so it says which.

``` shell
--cluster=<name>          the cluster to attach to            (required)
--cluster-port=<port>     its port                            (22010, run-local's)
--cluster-hosts=a,b       where to knock, for other machines
```

The three launchers run clusters of their own, so the pair to use is:

| Started with | Cluster | Port |
|---|---|---|
| `./greenfinger-cli.sh --cluster=<name>` | whatever you called it | 22000 |
| `./run-local.sh` | `greenfinger-local` | 22010 |
| `./run-docker.sh` | `greenfinger-docker` | 22020 |

Several terminals can be open at once, each attached to a different cluster. A crawl started from
a terminal belongs to the cluster: closing the terminal leaves it running, and opening one again
picks the watching back up.

## What you can type

**Catalogs**

``` shell
catalog-list                        Every stored catalog
catalog-show    --id=<id>           One catalog, in full
catalog-save                        Create or change one, a question at a time
catalog-save    --json='{...}'      The same, in one argument, for a script
catalog-delete  --id=<id>           Remove the definition
catalog-cats                        Every category in use
versions        --id=<id>           Every version, newest first
crawler-report  --id=<id>           What a version's crawls did
```

**Crawling**

``` shell
catalog-crawl   --id=<id>           Crawl from the start url
update          --id=<id>           The urls that have appeared since
resume          --id=<id>           Continue after a pause
rebuild         --id=<id>           A new version, the whole site again
pause           --id=<id>           Stop a running crawl where it is
status                              What is running, live; q stops watching
status --all=true                   The same, with a row per node
delete          --id=<id> ...       Remove versions from any of the four stores
replay          --id=<id> --layers=index
test-url        --url=<url>         Fetch one url and report what came back
options                             Every catalog setting and its default
```

**Searching**

``` shell
search --query=<words>                  By the words on the page
search --query=<words> --mode=meaning   By what it is about
search --query=<words> --mode=pictures  Find pictures by describing them
search                                  With no query: everything that was kept
search --query=<words> --id=<id>        Within one catalog
search --query=<words> --size=25        How many results
index-info                              The full text index, and what is in it
vector-info                             The vector store, and what is in it
```

## Deleting

`catalog-delete` removes the definition. `delete` removes what a crawl produced, and says what it
would remove before it does:

``` shell
delete --id=<id> --version=3                 one version
delete --id=<id> --keep-latest=3             keep the newest three, remove the rest
delete --id=<id> --all=true                  every version; the index stays, empty
delete --id=<id> --all=true --purge=true     every version, and drop the index too
delete --id=<id> --all=true --dry-run=true   what it would remove, and remove nothing
delete --id=<id> --version=3 --layers=vector only the vectors of that version
```

`--layers` takes `db`, `file`, `index`, `vector` or `all`, joined with `+`.


In containers
========================================

The cluster port lives inside the docker network, so a terminal on the host cannot reach it. Run
it inside instead:

``` shell
./run-docker.sh              # the nodes, and the page
./run-docker.sh shell        # the prompt, in a container on the same network
./run-docker.sh logs 1       # what node 1 is saying
./run-docker.sh stop
```


Settings
========================================

What a node is -- which database, where pages go, which outputs are on -- is configuration rather
than command line, and lives beside the launchers in `run.conf` and `.env`. The command line
carries what changes per invocation: which cluster, which catalog, how many nodes and threads.

``` shell
GF_DATA_STORE=/var/gf ./greenfinger-cli.sh --cluster=nightly crawl --id=<id>
```

Anything already in the environment wins over the file, so a one-off stays a one-off.
