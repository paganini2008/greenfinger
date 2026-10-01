---
title: "Greenfinger 2.0：丢进一个 url，拿回一座可搜索的档案馆"
---

# Greenfinger 2.0：丢进一个 url，拿回一座可搜索的档案馆

> **整站爬完。每一个页面、每一张图、每一个版本都留下。**
> **按词搜，按意思搜，或者描述一张你记得的图来找图。**
> **加一个节点就更快，杀掉一个它继续跑。**
> **什么都不用装，什么都不用注册。**

一个跑在 JVM 上的分布式网络爬虫。一次爬取同时写出普通文件、全文索引和向量集合。每个节点跑同一个
jar，没有协调者要部署，第一次爬取不需要数据库、不需要搜索服务、不需要 API key。

![实时观察一次爬取](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-live.jpg)

*正在 rebuild。v1 继续回答搜索，v2 同时在写，每个节点各自报告自己拉了多少。*

---

## 它解决什么问题

大多数爬虫代码的开头都一样。有人需要某个站点的内容，围着一个 http 客户端和一个 html 解析器写一百行，
跑通了。然后现实来了。一个广告链接，爬虫开始下载整个互联网。进程在第四万页被杀，而队列在内存里。
一半站点原来是 pdf。导航和 cookie 横幅跟正文一起进了索引，于是每条搜索结果看起来都一样。

然后，把它存下来是第二个项目：文件在这儿，索引在那儿，向量在另一个地方，三段胶水代码各有各的挂法。
这些 Greenfinger 在产品里解决，而不是在你的代码里，而且第一次爬取旁边什么都不用跑。

---

## Quick Start

``` shell
git clone https://github.com/paganini2008/greenfinger.git
cd greenfinger/backend && mvn clean install
cd ../deploy

./greenfinger-cli.sh --cluster=demo crawl --url=https://books.toscrape.com
```

结果：

``` text
Crawling 'books.toscrape.com' from https://books.toscrape.com

  pages kept    1,000      urls seen   28,411      1 in every 28
  images        3,204      indexed      1,000      elapsed  0h 4m 12s

Finished: reached maxFetchSize
Catalog 01a0c3d5-7d3d-7000-81f6-76057a403db8, version 0, now searchable.
```

想要控制台：

``` shell
./run-local.sh                # 节点加页面，http://localhost:9700
GF_NODES=3 ./run-local.sh     # 三个节点共享一次爬取
./run-docker.sh               # 同样的东西，跑在容器里
./run-local.sh stop
```

用 `admin` 和 `deploy/config/api/users.xml` 里的密码登录。全新安装自带七个示例 catalog，
在你想好爬什么之前就有东西可爬。

![Catalogs](https://paganini2008.github.io/greenfinger/blogger/assets/catalogs.jpg)

---

## 环境要求

| | 版本 | 用来做什么 |
|---|---|---|
| **JDK** | 17 或更高 | 跑任何东西 |
| **Maven** | 3.9 或更高 | 从源码构建 |
| **Node** | 20 或更高 | 构建网页界面 |
| **Docker** | 当前版本 | 只有 `run-docker.sh` 需要 |

其余全是可选的，一次一个变量：用 PostgreSQL、MySQL、SQL Server、Oracle 或 SQLite 换掉 H2 文件，
用 Elasticsearch 换掉内嵌 Lucene 索引，用 Qdrant 或 Weaviate 换掉内嵌向量库，
用 MinIO 或任何兼容 S3 的存储换掉本地磁盘，用 Ollama 或 OpenAI 换掉本地 ONNX 模型。

浏览器只有 `playwright` 和 `selenium` 两种抽取器才需要。默认的 `adaptive` 会退到 HtmlUnit，
那是纯 Java 的，什么都不用装。

---

## 工作原理

### 一次爬取，三种输出

``` text
                            ┌──────────────┐
                            │  file        │  本地磁盘、MinIO、任何 S3 存储
   一次爬取  ─────────────► ├──────────────┤
                            │  index       │  内嵌 Lucene、Elasticsearch
                            ├──────────────┤
                            │  vector      │  内嵌 Lucene、Qdrant、Weaviate、ES
                            └──────────────┘
                                   │
                    replay ────────┘   从文件重建索引或向量，
                                       不用再抓一次站点
```

file 层永远开着，因为数据库只存元数据，另外两层都从它写下的东西重建。**搜索从不查数据库**，
后面整个设计都是从这一条推出来的。

### 一个页面要过哪几关

``` text
  从 frontier 取出一个 url
        │
        ├─ UrlPathAcceptor 链       域边界、起始 url 前缀、静态资源、robots.txt、
        │                           深度、路径模式。第一个拒绝的说了算
        ├─ ExistingUrlPathFilter    见过吗？查 RocksDB
        ├─ Extractor                普通 http，只有空壳页面才开浏览器
        ├─ ContentExtractor         要正文，不要导航
        │     └─ DocumentContentParser   当它不是 html 的时候
        ├─ ContentDedupFilter       SHA-256 或 SimHash
        ├─ OutputChannels           file、index、vector
        └─ 新链接 ────────────────► 回到 frontier，交给归属它的节点
```

每一次丢弃都被计数并且有名字。所以监控页能告诉你两万两千个 url 分别去了哪儿，
而不是只说留下了 140 页。

### 集群怎么分担一次爬取

``` text
   节点 A ── 发现 /a/b ──► 归属节点 C ──► 抓取，发现 /a/b/c ──► 归属节点 A ──► ...

   没有中心队列        抓取路径上没有 leader        可以中途加入或离开
```

一次爬取本质上是个递归函数，分布式改变的只是那次递归调用跨了进程。**没有 join**，
因为父页面不关心子页面抓到了什么。

**结束由所有人判定。** 每个节点都拿共享计数器去比 `maxFetchSize` 和 `fetchDuration`，
第一个发现的写下原因。leader 中途挂掉，也留不下一次永远结束不了的爬取。

![成员、角色、传输、健康检查](https://paganini2008.github.io/greenfinger/blogger/assets/cluster.jpg)

---

## 代码示例

### 爬取、增量、重建、重放

**输入。** 一个 catalog id，加一个动词。

``` shell
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id>            # 从起始 url 开始
./greenfinger-cli.sh --cluster=nightly update  --id=<id>            # 之后新出现的 url
./greenfinger-cli.sh --cluster=nightly merge   --id=<id>            # 并且重访已有的
./greenfinger-cli.sh --cluster=nightly rebuild --id=<id>            # 新版本，旧版本继续服务
./greenfinger-cli.sh --cluster=nightly resume  --id=<id>            # 暂停后继续
./greenfinger-cli.sh --cluster=nightly replay  --id=<id> --layers=index+vector
```

**输出。**

| 动词 | 版本 | 抓什么 | 写什么 |
|---|---|---|---|
| `crawl` | 当前 | 从起始 url | 它存下的一切 |
| `update` | 当前 | 只抓没见过的 url | 只写新页面 |
| `merge` | 当前 | 新 url **加上**已有的页面 | 只写真的变了的 |
| `rebuild` | 新开一个 | 整站重来 | 写进新版本，旧版本继续服务 |
| `resume` | 当前 | frontier 上剩下的 | 和被中断那次一样 |
| `replay` | 指定一个 | 什么都不抓 | 从已存的东西重建某一层 |

`replay` 是最值得记住的一个。Elasticsearch 停了一小时、你换了分析器、或者你在半年后才决定终究还是
想要向量，这些都不需要目标站点再对你客气一次。

### 三种搜法

**输入。** 一个框，三种模式，控制台和提示符都一样。

``` text
greenfinger:> search --query="bread"
greenfinger:> search --query="what happens when a star runs out of fuel" --mode=meaning
greenfinger:> search --query="a bright spiral galaxy against black sky" --mode=pictures
```

**输出。** Words 走索引，所以这是精确词加高亮。

![按词搜](https://paganini2008.github.io/greenfinger/blogger/assets/search-words.jpg)

Meaning 走文本向量。下面排第一的是超新星遗迹那一页，而那一页从没出现过输入的这句话。

![按语义搜](https://paganini2008.github.io/greenfinger/blogger/assets/search-meaning.jpg)

Pictures 走图片向量，匹配的是图片本身，不是文件名也不是 alt 文字。

![描述一张图来找图](https://paganini2008.github.io/greenfinger/blogger/assets/search-pictures.jpg)

在提示符里是同一件事：

``` text
╭────────┬──────────────────────────────────────────┬────────────────────────────────────────────────╮
│ Score  │ Title                                    │ Url                                            │
├────────┼──────────────────────────────────────────┼────────────────────────────────────────────────┤
│ 0.9356 │ APOD: 2008 June 4 - Chasing the ISS      │ https://apod.nasa.gov/apod/ap080604.html       │
│ 0.9317 │ APOD Index - Nebulae: Supernova Remnants │ https://apod.nasa.gov/apod/supernova_remnants… │
│ 0.9289 │ APOD Index - Stars: Binary Stars         │ https://apod.nasa.gov/apod/binary_stars.html   │
╰────────┴──────────────────────────────────────────┴────────────────────────────────────────────────╯
```

向量模型在本地跑，不需要账号：文本用 `multilingual-e5-small`，图片用 `SigLIP 2`，都是 ONNX，
都在启动后用后台线程预热。

### 加一个 pdf 解析器

**输入。** 一个链出 pdf 的站点。

**代码。** 每个默认组件都是 `@ConditionalOnMissingBean`，所以发布一个 bean 就是全部的注册动作。
没有插件注册表，也没有排序属性。

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

**输出。** 配上 `GF_DOCUMENTS=true` 和 `GF_DOCUMENT_TYPES=pdf`，被爬页面链出去的 pdf 就开始向
索引和向量贡献文本了。**收集链接这件事本来就一直在做，而且不花钱**，你刚打开的是去抓取和读取它们。

爬虫做的每一个决定都是同一个形状：

| 接口 | 决定什么 | 自带实现 |
|---|---|---|
| `Extractor` | 页面怎么抓 | restclient、htmlunit、playwright、selenium、adaptive |
| `UrlPathAcceptor` | 一个链接跟不跟 | 域边界、起始 url、静态资源、robots.txt、深度、路径模式 |
| `ContentDedupFilter` | 两个 url 是不是同一个页面 | sha256、simhash |
| `ContentExtractor` | 页面里的正文 | 链接密度法去模板 |
| `DocumentContentParser` | 非 html 文件里的文本 | text、markdown、csv |
| `CompletionChecker` | 爬取什么时候结束 | 已存页数、已用时间 |
| `OutputChannel` | 结果写到哪儿 | file、index、vector |
| `BlobStore` | 页面和图片的字节 | local、minio |
| `Searcher` / `VectorStore` | 全文，以及语义 | lucene、elasticsearch、qdrant、weaviate |
| `EmbeddingClient` | 把文本变成向量 | 本地 ONNX、ollama、openai |

### 嵌进你自己的应用

**输入。** 你自己的一个 Spring Boot 应用。

``` java
@EnableGreenfingerServer
@SpringBootApplication
public class MyApplication { }
```

**输出。** 整套 REST api、登录和页面，都在你的进程里。它是显式的而不是自动装配的，
因为"躺在 classpath 上"不构成去打开 RocksDB、去占一个爬取许可的理由。
不要服务端只要爬取的话，`CrawlerLauncher.crawl(catalogId, onReady)` 返回一个
`CrawlerEngine.Result`，带着计数器和结束原因。

---

## 配置

### 每个 catalog

只有 url 是必填。其余每一项的默认值，都是为了让一个你一无所知的站点也能被有效地爬一遍。
在提示符里敲 `options` 看当前值。

| 属性 | 默认 | 说明 |
|---|---|---|
| `url` | 必填 | http:// 或 https://。既是身份也是外边界 |
| `name` | 域名 | 唯一的名字 |
| `cat` | `other` | 九个分类之一，用于过滤和分组 |
| `start-url` | 等于 `url` | 从哪儿开始抓。必须位于 `url` 之下 |
| `sitemap-url` | 空 | 留空则从 robots.txt 里发现 |
| `include` | `**.<domain>/**` | ant 路径模式，多个用逗号 |
| `exclude` | 空 | ant 路径模式，多个用逗号 |
| `encoding` | `UTF-8` | 服务器声明错了的时候用 |
| `extractor` | `adaptive` | adaptive、restclient、htmlunit、playwright、selenium |
| `max-size` | `10000` | 存够多少页就停 |
| `depth` | `-1` | 链接深度，-1 表示不限 |
| `duration` | `30` | 跑多少分钟就停 |
| `interval` | `1000` | 两次抓取之间的毫秒数，按节点算 |
| `retry` | `1` | 每个 url 重试几次 |
| `images` | `true` | 到底抓不抓图片 |
| `output-types` | `file` | `file+index+vector`，file 永远开着 |
| `content` | `text+image` | 什么进索引和向量 |
| `max-versions` | `10` | 保留几个版本，超出就删最旧的 |

### 节点级，写在 `deploy/.env`

| 属性 | 默认 | 说明 |
|---|---|---|
| `GF_DB_URL` | 一个 H2 文件 | PostgreSQL、MySQL、SQL Server、Oracle、SQLite |
| `GF_INDEX_PROVIDER` | `lucene` | 或 `elasticsearch`，配 `GF_ES_URIS` |
| `GF_VECTOR_STORE` | `lucene` | 或 `elasticsearch`、`qdrant`、`weaviate` |
| `GF_FILE_TARGET` | `local` | 或 `minio`。对象键和本地路径完全一致 |
| `GF_EMBEDDING_PROVIDER` | `local` | 或 `ollama`、`openai` |
| `GF_LUCENE_ANALYZER` | `standard` | `standard`、`smartcn` 或 `cjk` |
| `GF_ES_ANALYZER` | `standard` | `ik_max_word` 需要装 analysis-ik 插件 |
| `GF_DOCUMENTS` | `false` | 链出去的文档要不要抓取并读取 |
| `GF_CLUSTER_TRANSPORT` | `NETTY` | 或内置的 `NIO` |
| `GF_NODES` | `1` | 一个启动器起几个进程 |
| `GF_MEMORY` | `2g` | 每个容器。**堆和堆外一起**受它限制 |

分析器比看上去更要紧。standard 分析器把中文切成一个个单字，中文分析器会把法文字符直接丢掉，
`chaîne` 会变成 `cha î ne`。

---

## 性能

只有自家数据。这里没有跟其他爬虫的对比，因为我们没跑过那种能让对比站得住脚的对照实验。

**测试环境。** Apple M2 Max，12 核，32 GB，macOS 26.3.1，JDK 17.0.12。
`run-local.sh` 起两个节点，每个 `-Xms256m -Xmx2g`。H2 文件、内嵌 Lucene 索引、内嵌 Lucene 向量库、
页面和图片在本地磁盘、本地 ONNX 向量模型。家用宽带。礼貌爬取，所以吞吐的上限是 `fetchInterval`
而不是硬件。

| 站点 | 节点 | 留下 | 图片 | 看到的 url | 耗时 |
|---|---|---|---|---|---|
| apod.nasa.gov | 2 | 46 页 | 16 | 3,773 | 1 分 41 秒 |
| simplefood.blog | 2 | 140 页 | 2,489 | 22,857 条已知 | 5 分 01 秒 |
| books.toscrape.com | 1 | 1,000 页 | 3,204 | 28,411 | 4 分 12 秒 |

"看到的 url"和"留下的页数"之间的差距是重点，不是低效。simplefood 那次有 22,340 个 url 被边界规则
挡掉、561 个是重复内容，这些都是输出层从来不用做的工作。

**工作分得有多均匀。**

``` text
两个节点，apod.nasa.gov
  节点 18bbe738   处理 76   51%   26 页   9 张图
  节点 96c82b18   处理 74   49%   19 页   7 张图

三个节点，61 页的站点，共享一个 PostgreSQL
  派发 61      处理 66      保存 61
  node-1: 抓 18   node-2: 18   node-3: 25     每页恰好一次
```

**其余量过的数字。**

| | 实测 |
|---|---|
| 词搜，内嵌 Lucene，151 个文档 | 38 条命中，16 毫秒 |
| 集群线格式，一个 `CrawlTask` | JSON 327 字节，编解码 1,394 纳秒 |
| 两个 ONNX 模型预热 | 4.47 秒，在应用就绪之后的后台线程上 |
| 两个模型都加载后的空载 RSS | 1.77 到 1.88 GB |
| 图片向量重复率，修前和修后 | 43 倍，然后 1.00 倍 |
| 容器内存，向量或浏览器 | 1g 必被 OOMKilled，2g 通过，而且两者要分开跑 |
| 测试 | 133 个类、1,098 个方法，四个模块 80% 行覆盖门禁 |

---

## 已知限制与取舍

- **内嵌的索引和向量库，每个节点都要付一份正文的代价。** 一两个节点没问题。真要组集群，
  指向 Elasticsearch 或 Qdrant，启动报告也会大声这么建议。
- **复制是异步的。** 刚爬完那会儿，一个节点可能已经能答，另一个还没追上。是秒级，不是分钟级。
- **节点被杀后 catalog 卡在 running。** 注册表说没有东西在跑，数据库里那行说有，
  而 `interrupt` 没东西可中断。在待修列表上。
- **正文抽取还会漏进一些模板文字。** 分享按钮和页脚署名会出现在搜索片段里。
- **礼貌就是吞吐的天花板。** `fetchInterval` 默认每节点一秒，这是有意的。
  想把一个站点榨到它能答多快就多快，这不是那个工具。
- **一个集群一次只跑一个爬取。** 想跑两个就起两个集群，也就是一个名字加一个端口。
- **pdf、Word、Excel 要你自己加 bean。** 每一个都是多一个依赖、多一份许可证、多一份对内存的胃口，
  这个选择属于应用。

---

## 小结

1. **丢进一个 url，拿回整站的文件、全文索引和向量**，一次爬取全有。
2. **什么都不用准备。** 默认就是 H2、内嵌 Lucene 索引和本地 ONNX 模型。
   不用数据库、不用搜索服务、不用 API key、不用手动下模型。
3. **天生去中心化。** 每个节点跑同一个 jar。没有协调者，没有中心队列，抓取路径上没有 leader。
4. **扩容就是多起一个进程**，指向同一个集群名，哪怕那次爬取已经在跑了。
5. **replay 从文件重建输出**，所以索引丢了或者换了分析器，都不意味着要重爬站点。
6. **版本让 rebuild 变得安全。** 旧版本继续回答搜索，而被中断的 rebuild 什么都不发布。
7. **三种搜法**，包括描述一张图把它找出来，模型全在本地跑。
8. **爬虫做的每一个决定都是一个接口**，自带默认实现，发布一个 bean 就能替换。
9. **爬取跑不出站外。** 两条边界规则，哪一条都关不掉。
10. **Apache 2.0，JDK 17，Spring Boot 4.1。**

---

源码、issue 和完整文档：[github.com/paganini2008/greenfinger](https://github.com/paganini2008/greenfinger)
