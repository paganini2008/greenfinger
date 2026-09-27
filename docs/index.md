---
title: Greenfinger
description: One url in. A searchable archive out.
---

# Greenfinger

**One url in. A searchable archive out.**

Crawl the whole site. Keep every page, every picture, every version. Search it by words, by
meaning, or by describing a picture you remember. Add a node and it goes faster. Kill one and it
keeps going. Nothing to install. Nothing to sign up for.

A distributed web crawler for the JVM. One pass writes plain files, a full text index and a vector
collection.

[Source on GitHub](https://github.com/paganini2008/greenfinger)

---

## Documentation

| | |
|---|---|
| [README](https://github.com/paganini2008/greenfinger#readme) | What it does and how to run it. Start here |
| [Command line reference](cli-reference.md) | Every crawl verb, every prompt command |
| [Developer guide](developer-guide.md) | Every seam, with a worked example |
| [Design](design-2.0.md) | How the system is shaped, and why |
| [Schema scripts](sql/schema-scripts.md) | One per database, generated from the entities |
| [Changelog](https://github.com/paganini2008/greenfinger/blob/main/CHANGELOG.md) | What changed in 2.0 |

---

``` shell
cd deploy
./greenfinger-cli.sh --cluster=demo crawl --url=https://books.toscrape.com
```

Apache License 2.0.
