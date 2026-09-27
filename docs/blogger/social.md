---
title: "Titles and social copy"
---

# Titles and social copy

Paste ready. The title in each file is already set to the recommended one.

---

## Blog titles

### English

| | Title | Where it fits |
|---|---|---|
| **Recommended** | **Greenfinger 2.0: one url in, a searchable archive out** | dev.to, Hashnode, Medium, HN. Names the thing and makes the claim in nine words |
| Alternative | A distributed web crawler that hands you files, a search index and vectors in one pass | Longer, better for a subtitle or a LinkedIn article |
| Alternative | I gave a crawler one url and got back a site I can search by describing a picture | First person, better on Reddit than anywhere else |

### 中文

| | 标题 | 适合 |
|---|---|---|
| **推荐** | **Greenfinger 2.0：丢进一个 url，拿回一座可搜索的档案馆** | 掘金、知乎、CSDN、公众号 |
| 备选 | 开源了一个分布式爬虫：一次爬取，同时写出文件、全文索引和向量库 | 偏技术向的平台，说清楚是什么 |
| 备选 | 整站爬完，按词搜、按意思搜、描述一张图也能搜出来 | 标题党一点，适合信息流 |

---

## The generic post

One block, works anywhere. Paste it on X, LinkedIn, Reddit, Hacker News, dev.to
or a WeChat post without rewriting it.

### English

``` text
Point it at a url. Walk away.

Come back to the whole site on disk, a full text index, and vectors you can
query by describing a picture you only half remember. Type "a bright spiral
galaxy against black sky" and it hands you the galaxy.

Greenfinger 2.0 is a distributed web crawler for the JVM. One pass keeps every
page, every picture and every version, reads the pdfs and markdown a site links
to, throws away the navigation, and writes files, a search index and vectors at
the same time.

The first crawl needs no database, no search server, no API key and no model
download. Every node runs the same jar, so add one and it goes faster, kill one
and the crawl carries on. Killed halfway, it resumes. Lose your index and you
rebuild it from the files without touching the site again.

Java 17, Apache 2.0.
https://github.com/paganini2008/greenfinger
```

### 中文

``` text
给它一个 url，然后走开。

回来的时候，整站已经躺在磁盘上，全文索引建好了，向量也建好了。你可以直接打一句
「一张黑色天空上的亮螺旋星系」，它把那张图从几千张里捞给你。

Greenfinger 2.0 是一个跑在 JVM 上的分布式爬虫。一次爬取留下每一个页面、每一张图、
每一个版本，读得懂站点链出去的 pdf 和 markdown，扔掉导航和 cookie 横幅，
同时写出文件、全文索引和向量。

第一次爬取不需要数据库、不需要搜索服务、不需要 API key、不用手动下模型。
每个节点跑同一个 jar，加一个就更快，杀掉一个爬取继续。爬到一半被杀，它接着跑。
索引丢了，从文件重建，不用再碰那个站点。

Java 17，Apache 2.0。
https://github.com/paganini2008/greenfinger
```

---

## X / Twitter

Short, one claim per line, link last.

``` text
Point it at a url. Walk away.

Come back to the whole site on disk, a full text index, and vectors you can
query by describing a picture you only half remember.

No database to install. No API key. No coordinator process.
Add a node and it goes faster. Kill one and it keeps going.

Greenfinger 2.0. Java 17, Apache 2.0.
github.com/paganini2008/greenfinger
```

Shorter still, if you want it under one screen:

``` text
One url in. A searchable archive out.

Whole site on disk, full text index, and image vectors, from one pass.
Search "a bright spiral galaxy against black sky" and get the picture.

Nothing to install. Nothing to sign up for.
github.com/paganini2008/greenfinger
```

---

## Reddit / Hacker News / LinkedIn

Longer, and the exaggeration earns its keep because every line is true.

``` text
I got tired of writing the same hundred lines around an http client, so I spent
a while building the thing I actually wanted.

Give Greenfinger a url. It crawls the entire site, keeps every page, every
picture and every version, reads the pdfs and markdown files the site links to,
throws away the navigation and the cookie banners, and writes what is left into
three places at once: plain files you can open, a full text index, and a vector
store.

Then you can search it three ways. By words. By meaning, so "what happens when a
star runs out of fuel" finds the supernova page that never says it. And by
describing a picture, so "a bright spiral galaxy against black sky" hands you
the galaxy.

The first crawl needs no database, no search server, no API key and no model
download. H2 and an embedded Lucene index are the defaults, and the embedding
models are ONNX running in the crawler's own process.

It is distributed with no coordinator to deploy. Every node runs the same jar.
Start another one, point it at the same cluster name, and it joins a crawl that
is already running. Kill one and the crawl carries on. There is no central queue
and no leader in the fetch path, because one url dispatched is one call to
whichever node owns it.

Killed halfway, it resumes from where it stopped. A rebuild opens a new version
beside the old one and search keeps answering from the old one until the new one
finishes. A rebuild that gets rate limited publishes nothing, so your search is
exactly as it was.

And if you lose your index, you rebuild it from the files without touching the
site again.

Java 17, Spring Boot 4.1, Apache 2.0.
https://github.com/paganini2008/greenfinger
```

---

## 中文社媒

``` text
给它一个 url，然后走开。

回来的时候，整站已经躺在磁盘上，全文索引建好了，向量也建好了。
你可以直接打一句「一张黑色天空上的亮螺旋星系」，它把那张图从几千张里捞给你。

不用装数据库，不用注册账号，不用手动下模型。默认就是 H2 加内嵌 Lucene，
两个 embedding 模型是 ONNX，跑在爬虫自己的进程里。

分布式没有协调者要部署，每个节点跑同一个 jar。再起一个，指向同一个集群名，
它就加入一次已经在跑的爬取。杀掉一个，爬取继续。

爬到一半被杀，它从停下的地方接着跑。
rebuild 在旧版本旁边开新版本，搜索一直由旧的回答，直到新的完成。
rebuild 中途被站点限流，它什么都不发布，你的搜索和原来一模一样。
索引丢了，从文件重建，不用再碰那个站点。

Greenfinger 2.0，Java 17，Apache 2.0。
github.com/paganini2008/greenfinger
```

一句话版，给需要短文案的地方：

``` text
丢进一个 url，拿回一座可搜索的档案馆。
整站、图片、每一个版本都留下，按词搜、按意思搜、描述一张图也能搜。
什么都不用装，什么都不用注册。
```

---

## Before posting

- The blog images point at
  `https://paganini2008.github.io/greenfinger/blogger/assets/`, which is GitHub
  Pages serving from `docs/`. **Push `docs/` to `main` first** or every image is
  a 404, and check Settings then Pages has `main` and `/docs` as the source.
- Verify one image opens in a browser before you post anywhere. A dead image in
  a submission cannot be fixed after it has been seen.
- 微信公众号、知乎、掘金、CSDN 都会把外链图转存到自家图床，粘贴时自动完成。
  dev.to、Hashnode、Reddit 直接吃外链。
- `raw.githubusercontent.com/paganini2008/greenfinger/main/docs/blogger/assets/`
  serves the same files and is the fallback if Pages is ever switched off.
