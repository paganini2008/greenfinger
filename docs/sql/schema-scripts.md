# Schema scripts

One per database, generated from the entities. Create the schema with these rather than
letting Hibernate do it, on any database you cannot afford to lose.

One script per database, holding the five tables Greenfinger keeps: `crawler_catalog`,
`crawler_resource`, `crawler_image`, `crawler_resource_image` and `crawler_report`.

| File | Tested against |
|---|---|
| `schema-h2.sql` | H2 2.x, file and in-memory |
| `schema-sqlite.sql` | SQLite 3 |
| `schema-mysql.sql` | MySQL 8 |
| `schema-postgresql.sql` | PostgreSQL 16 |
| `schema-sqlserver.sql` | SQL Server 2022 |
| `schema-oracle.sql` | Oracle 19c and later |

## Why they exist

The default is `GF_DB_DDL=update`, which is right for a laptop and wrong for anything you cannot
afford to lose. Hibernate's `update` only ever **adds**: a column whose type changed is left as it
was, a column that was dropped stays behind, an index that was renamed becomes two, and two nodes
starting at the same moment race each other to create the same table. Nothing tells you which of
those happened.

So for a real deployment:

``` shell
# once, against an empty database, having read it
psql -h localhost -U greenfinger -d greenfinger -f schema-postgresql.sql

# and then, in .env, for every node
GF_DB_DDL=validate
```

`validate` makes Hibernate check the schema against the entities at startup and refuse to start if
they disagree — which is what turns a schema mistake into a node that will not come up, rather
than into rows that quietly go missing.

## Keeping them honest

They are generated from the entity classes by `SchemaScriptsTest`, not written by hand: a DDL file
maintained separately from the code it describes drifts, and the first anyone hears of it is a
column that exists on one deployment and not another. Change an entity and run

``` shell
mvn -o -pl greenfinger-core test -Dtest=SchemaScriptsTest
```

and the six files are rewritten. If nothing changed, nothing changes — so a modified file in the
diff means the schema moved and somebody has to decide what existing databases do about it.

## What is not here

**Migrations.** These create a schema from nothing; they do not take you from one version of it to
the next. When the schema changes after release, the change needs a migration script written by
hand, or a tool (Flyway, Liquibase) taking the job over. That decision is still open.

**The other stores.** Only the database is described here. The frontier and the dedup filters are
RocksDB directories the crawler creates for itself, the index and the vector collections are
created on first use, and the pages are files. None of them needs a schema step.
