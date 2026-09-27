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

## Start here

| | |
|---|---|
| [Introducing Greenfinger 2.0](blogger/greenfinger-2.0-en.md) | The tour, with screenshots. Start here |
| [Greenfinger 2.0 中文介绍](blogger/greenfinger-2.0-zh.md) | The same tour in Chinese |

## Reference

| | |
|---|---|
| [Command line reference](cli-reference.md) | Every crawl verb, every prompt command |
| [Developer guide](developer-guide.md) | Every seam, with a worked example |
| [Design](design-2.0.md) | How the system is shaped, and why |
| [Schema scripts](sql/schema-scripts.md) | One per database, generated from the entities |

---

``` shell
cd deploy
./greenfinger-cli.sh --cluster=demo crawl --url=https://books.toscrape.com
```

Apache License 2.0.
