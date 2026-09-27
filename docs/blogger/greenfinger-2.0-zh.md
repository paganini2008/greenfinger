---
title: "Greenfinger 2.0：丢进一个 url，拿回一座可搜索的档案馆"
---

# Greenfinger 2.0：丢进一个 url，拿回一座可搜索的档案馆

> **整站爬完。每一个页面、每一张图、每一个版本都留下。**
> **按词搜，按意思搜，或者描述一张你记得的图来找图。**
> **加一个节点就更快，杀掉一个它继续跑。**
> **什么都不用装，什么都不用注册。**

一个跑在 JVM 上的分布式网络爬虫。一次爬取同时写出普通文件、全文索引和向量集合。每个节点跑同一个 jar，
没有协调者要部署，第一次爬取不需要数据库、不需要搜索服务、不需要 API key。

![实时观察一次爬取](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-live.jpg)

*正在 rebuild。v1 继续回答搜索，v2 同时在写，每个节点各自报告自己拉了多少。*

---

## 六十秒上手

``` shell
git clone https://github.com/paganini2008/greenfinger.git
cd greenfinger/backend && mvn clean install
cd ../deploy

./greenfinger-cli.sh --cluster=demo crawl --url=https://books.toscrape.com
```

输出：

``` text
Crawling 'books.toscrape.com' from https://books.toscrape.com

  pages kept    1,000      urls seen   28,411      1 in every 28
  images        3,204      indexed      1,000      elapsed  0h 4m 12s

Finished: reached maxFetchSize
Catalog 01a0c3d5-7d3d-7000-81f6-76057a403db8, version 0, now searchable.
```

想要网页界面：

``` shell
./run-local.sh                # 节点加页面，http://localhost:9700
./run-docker.sh               # 同样的东西，跑在容器里
./run-local.sh stop
```

用 `admin` 和 `deploy/config/api/users.xml` 里的密码登录。全新安装自带七个示例 catalog，
在你想好爬什么之前就有东西可爬。

---

## 它解决什么

| 痛点 | Greenfinger 怎么做 |
|---|---|
| 爬着爬着跑出站外，开始下载整个互联网 | 两条关不掉的边界规则：可注册域一致，且位于起始 url 之下 |
| 一台机器不够用 | 每个节点跑同一个 jar 各领各的份额。没有队列服务，没有调度器 |
| 爬到第四万页被杀，重启从零开始 | frontier 在磁盘上，resume 从停下的地方继续 |
| 一半站点是 pdf、docx、markdown | 每次爬取都记录文档链接。纯文本、markdown、csv 开箱即读 |
| 导航和 cookie 横幅跟正文一起进索引 | 按链接密度去模板。不用模型不用词典，任何语言一视同仁 |
| 文件、索引、向量是三个项目 | 一次爬取三份全写。任何一份都能从文件重建，不用重爬 |

---

## 一次爬取，三种输出

``` text
                            ┌──────────────┐
                            │  file        │  本地磁盘、MinIO、任何 S3 存储
   一次爬取  ─────────────► ├──────────────┤
                            │  index       │  内嵌 Lucene、Elasticsearch
                            ├──────────────┤
                            │  vector      │  内嵌 Lucene、Qdrant、Weaviate、ES
                            └──────────────┘
                                   │
                    replay ────────┘   从文件重建索引或向量，不用再抓一次站点
```

| 输出 | 写什么 | 后端 |
|---|---|---|
| **file** | 抓到的原始页面、正文文本、每一张图片 | local、minio |
| **index** | 全文，按词搜 | lucene、elasticsearch |
| **vector** | 文本分块和图片向量 | lucene、elasticsearch、qdrant、weaviate |

file 层永远开着。数据库只存元数据，所以另外两层都能从它重建。

``` shell
# Elasticsearch 停了一小时。或者你换了分析器。
# 或者你在半年后才决定终究还是想要向量。
./greenfinger-cli.sh --cluster=nightly replay --id=<id> --layers=index+vector
```

换文件存储就三行，对象键和本地路径完全一致：

``` shell
GF_FILE_TARGET=minio
GF_MINIO_ENDPOINT=http://minio:9000
GF_MINIO_BUCKET=greenfinger
```

AWS S3 和 Google Cloud Storage 都提供兼容 S3 的端点，同样三行指过去就行。随包发布并且在测试矩阵里覆盖
的是 MinIO。

---

## 去中心化，而且是动态的

没有协调者进程，没有要部署的调度器。派发一个 url 就是对另一个节点的一次调用。

``` text
   节点 A ── 发现 /a/b ──► 归属节点 C ──► 抓取，发现 /a/b/c ──► 归属节点 A ──► ...

   没有中心队列            抓取路径上没有 leader            可以中途加入或离开
```

``` shell
# 机器 A
GF_CLUSTER_NAME=nightly GF_CLUSTER_HOSTS=10.0.0.1,10.0.0.2 ./run-local.sh

# 机器 B，加入一次已经在跑的爬取
GF_CLUSTER_NAME=nightly GF_CLUSTER_HOSTS=10.0.0.1,10.0.0.2 ./run-local.sh
```

每个节点实际拉了多少，实时和报告里都看得见：

![每个节点做了什么](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-nodes.jpg)

**底层传输走 NIO**，Netty 是可原位替换的实现，而且默认就开着：

``` shell
GF_CLUSTER_TRANSPORT=NETTY     # 默认
GF_CLUSTER_TRANSPORT=NIO       # 内置传输
```

**结束由所有人判定。** 每个节点都拿共享计数器去比 `maxFetchSize` 和 `fetchDuration`，第一个发现的写下
原因。这不是 leader 的决定，所以 leader 中途挂掉不会留下一次永远结束不了的爬取。

![成员、角色、传输、健康检查](https://paganini2008.github.io/greenfinger/blogger/assets/cluster.jpg)

---

## 一个页面要过哪几关

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

**图片是一等公民。** 来自 `<img>`、`srcset`、`<picture>` 和 `og:image`，按尺寸、媒体类型和字节数过滤。
字节相同的图只存一份，不管多少页面引用它。

**文档是读进来的，不是跳过的。** 纯文本、markdown、csv 自带解析器。pdf、Word、Excel 差一个 bean，
因为每一个都意味着多一个依赖、多一份许可证、多一份对内存的胃口：

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

---

## 一个 catalog 能设置的全部

只有 url 是必填。其余每一项的默认值，都是为了让一个你一无所知的站点也能被有效地爬一遍。
在提示符里敲 `options` 就能看到这张表的当前值。

| 设置 | 取值 | 默认 |
|---|---|---|
| `url` | http:// 或 https:// | 必填，唯一必填项 |
| `name` | 唯一的名字 | 域名 |
| `cat` | 九个分类之一，用于过滤和分组 | `other` |
| `start-url` | `url` 之下的一个 url。既是种子也是边界 | `= url` |
| `sitemap-url` | 一个 url，留空则从 robots.txt 里发现 | 空 |
| `include` | ant 路径模式，多个用逗号 | `**.<domain>/**` |
| `exclude` | ant 路径模式，多个用逗号 | 空 |
| `encoding` | UTF-8、GBK 等 | UTF-8 |
| `extractor` | adaptive、restclient、htmlunit、playwright、selenium | adaptive |
| `max-size` | 存够多少页就停 | 10000 |
| `depth` | -1 表示不限 | -1 |
| `duration` | 跑多少分钟就停 | 30 |
| `interval` | 两次抓取之间的毫秒数，按节点算 | 1000 |
| `retry` | 每个 url 重试几次 | 1 |
| `url-dedup` | rocksdb，或者按类名指定你自己的 | rocksdb |
| `images` | 到底抓不抓图片 | true |
| `output-types` | file+index+vector，file 永远开着 | file |
| `content` | text+image 或 text，决定什么进索引和向量 | text+image |
| `max-versions` | 保留几个版本 | 10 |

这些在 `.env` 里都有节点级默认值，catalog 可以逐项覆盖。

---

## 还有这些

- **Sitemap。** 站点自己公布的 url 在爬取开始前就收集好，来自 `sitemap-url`，或者通过 robots.txt
  自动发现。
- **文件恢复。** `replay --layers=file` 按记录里的 url 把丢掉的页面和图片抓回来，只抓真正缺的那些。
  和 `index`、`vector` 一起用就能完整修复一个版本。
- **深翻页。** 词搜按游标翻页，能越过 Elasticsearch 拒绝的第一万条。向量搜索按 offset 翻页，
  上限一千。
- **搜索排序。** 详情页排在列表页之前，索引和向量库两边都是。
- **每次运行都有账可查。** 每个节点在页面旁边留一份报告文件，外加一份合并的，跟版本一起保存。
- **六种数据库。** H2、SQLite、MySQL、PostgreSQL、SQL Server、Oracle，每一种都过完整回归。
- **需要代理的站点。** `greenfinger.extractor.base.proxy-host` 和 `proxy-port` 对每一次抓取生效，
  页面、图片、文档一视同仁，因为它们共用同一个 HttpClient。
- **`test-url`** 抓一个 url 并报告拿回了什么，想知道一个站点为什么拒绝你，这是最快的办法。
- **容器。** `run-docker.sh` 每个节点一个容器，外加页面容器，它同时负责把 `/v2` 和 `/actuator`
  分摊到能应答的节点上。

---

## 三种搜法

### Words，走索引

![按词搜](https://paganini2008.github.io/greenfinger/blogger/assets/search-words.jpg)

精确词、高亮，详情页排在列表页之前。深翻页走游标，能越过 Elasticsearch 拒绝的第一万条。

### Meaning，走文本向量

![按语义搜](https://paganini2008.github.io/greenfinger/blogger/assets/search-meaning.jpg)

查询：*"what happens when a star runs out of fuel"*。排第一的是超新星遗迹那一页，
而那一页从没出现过这句话。

### Pictures，走图片向量

![描述一张图来找图](https://paganini2008.github.io/greenfinger/blogger/assets/search-pictures.jpg)

查询：*"a bright spiral galaxy against black sky"*。你描述想看到什么，而不是猜图注可能怎么写。

**模型在本地跑，不需要账号。** 文本用 `multilingual-e5-small`，图片用 `SigLIP 2`，都是 ONNX，
都在启动后用后台线程预热。

``` shell
GF_EMBEDDING_PROVIDER=local     # 默认，不用注册任何东西
GF_EMBEDDING_PROVIDER=ollama
GF_EMBEDDING_PROVIDER=openai
```

---

## 网页界面

Angular 21 signals、Material 3，调用的就是你自己代码会调用的那套 REST 端点。

**Catalogs。** 一个站点、爬它的规则，以及它产出的每一个版本。

![Catalogs](https://paganini2008.github.io/greenfinger/blogger/assets/catalogs.jpg)

**New catalog** 只要一个 url，别的都不问。

![新建 catalog](https://paganini2008.github.io/greenfinger/blogger/assets/catalog-new.jpg)

**Dashboard。** 这套安装留下了什么，以及为了留下它扔掉了什么。

![Dashboard](https://paganini2008.github.io/greenfinger/blogger/assets/dashboard.jpg)

**一次已完成运行的报告。** 每个 url 的去向，以及这个 catalog 是用什么配置跑的。

![运行报告](https://paganini2008.github.io/greenfinger/blogger/assets/monitor-report.jpg)

**Resources。** 每一行，按爬取顺序，带文件路径和内容哈希。

![Resources](https://paganini2008.github.io/greenfinger/blogger/assets/resources.jpg)

---

## 两个命令行

| | 它是什么 | 拿来做什么 |
|---|---|---|
| `greenfinger-cli.sh` | 跑完一条命令就退出的爬虫 | 在脚本或 cron 里爬取 |
| `greenfinger-shell.sh` | 连到别人正在跑的集群上的终端 | 看它、搜它、驱动它 |

``` shell
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id>            # 从起始 url 开始
./greenfinger-cli.sh --cluster=nightly crawl   --id=<id> --node=3   # 本机三个进程
./greenfinger-cli.sh --cluster=nightly update  --id=<id>            # 之后新出现的 url
./greenfinger-cli.sh --cluster=nightly merge   --id=<id>            # 并且重访已有的
./greenfinger-cli.sh --cluster=nightly rebuild --id=<id>            # 新版本，旧版本继续服务
./greenfinger-cli.sh --cluster=nightly resume  --id=<id>            # 暂停后继续
./greenfinger-cli.sh --cluster=nightly pause   --id=<id>
./greenfinger-cli.sh --cluster=nightly replay  --id=<id> --layers=index+vector
```

提示符自己不爬任何东西。它加入一个已经在跑的集群并驱动它，方式和网页完全一样。基于 Spring Shell，
补全、历史和帮助都自带。

``` text
CATALOG 相关                          爬取相关                           搜索相关
catalog-list                          catalog-crawl  --id=<id>          search --query=<words>
catalog-show    --id=<id>             update         --id=<id>          search --query=<w> --mode=meaning
catalog-save                          resume         --id=<id>          search --query=<w> --mode=pictures
catalog-save    --json='{...}'        rebuild        --id=<id>          search                (列出全部)
catalog-delete  --id=<id>             pause          --id=<id>          search --query=<w> --id=<id>
catalog-cats                          status                            index-info
versions        --id=<id>             status --all=true                 vector-info
crawler-report  --id=<id>             delete         --id=<id> ...
                                      replay         --id=<id> --layers=index
                                      test-url       --url=<url>
                                      options
```

跑着的集群上的真实输出：

``` text
greenfinger:> catalog-list
╭──────────────────────────────────────┬───────────────────────────────────┬─────────────────────────────┬───────────┬───────────────────┬─────────┬────────╮
│ Id                                   │ Name                              │ Url                         │ Category  │ Outputs           │ Version │ Search │
├──────────────────────────────────────┼───────────────────────────────────┼─────────────────────────────┼───────────┼───────────────────┼─────────┼────────┤
│ 01a0c3ac-90bc-7000-82aa-adcb256bc8bc │ NASA Astronomy Picture of the Day │ https://apod.nasa.gov/apod/ │ education │ file+vector+index │ v1      │ v1     │
│ 01a0c3ac-9152-7000-a9aa-7749058dd231 │ Rust Blog                         │ https://blog.rust-lang.org/ │ tech      │ file+index        │ v0      │ v0     │
│ 01a0c3d5-7d3d-7000-81f6-76057a403db8 │ Simple Food                       │ https://simplefood.blog/    │ food      │ file+vector+index │ v2      │ v1     │
╰──────────────────────────────────────┴───────────────────────────────────┴─────────────────────────────┴───────────┴───────────────────┴─────────┴────────╯

greenfinger:> search --query="what happens when a star runs out of fuel" --mode=meaning --size=3
╭────────┬──────────────────────────────────────────┬────────────────────────────────────────────────╮
│ Score  │ Title                                    │ Url                                            │
├────────┼──────────────────────────────────────────┼────────────────────────────────────────────────┤
│ 0.9356 │ APOD: 2008 June 4 - Chasing the ISS      │ https://apod.nasa.gov/apod/ap080604.html       │
│ 0.9317 │ APOD Index - Nebulae: Supernova Remnants │ https://apod.nasa.gov/apod/supernova_remnants… │
│ 0.9289 │ APOD Index - Stars: Binary Stars         │ https://apod.nasa.gov/apod/binary_stars.html   │
╰────────┴──────────────────────────────────────────┴────────────────────────────────────────────────╯

greenfinger:> vector-info
╭─────────────────────┬───────────────────╮  ╭──────────────────────────────┬─────────┬──────────────────────┬────────╮
│ Setting             │ Value             │  │ Catalog                      │ Version │ Collection           │ Points │
├─────────────────────┼───────────────────┤  ├──────────────────────────────┼─────────┼──────────────────────┼────────┤
│ Store               │ lucene            │  │ NASA Astronomy Picture of... │ v1      │ greenfinger_text_384 │    147 │
│ Chunk size          │ 1000              │  ╰──────────────────────────────┴─────────┴──────────────────────┴────────╯
│ Chunk overlap       │ 200               │
│ Max chunks per page │ 20                │
╰─────────────────────┴───────────────────╯
```

多个词的查询要用双引号，因为提示符在命令看到之前就把整行切成了词。

---

## 版本，以及为什么 rebuild 是安全的

``` text
  v0  ████████████  可搜索
  v1  ████████████  可搜索              rebuild 在 v1 旁边开出 v2
  v2  ██████░░░░░░  正在写              搜索继续由 v1 回答

  中途被打断 ──► 什么都不发布 ──► 搜索和原来一模一样
```

| 动词 | 做什么 |
|---|---|
| `crawl` | 从起始 url 开始 |
| `update` | 只取之后新出现的 url |
| `merge` | 在此基础上重访已有页面。原样返回的页面一个字都不写 |
| `rebuild` | 新开一个版本整站重来，旧版本继续服务 |
| `resume` | 暂停或被杀之后继续 |
| `replay` | 从已存的东西重建某一层输出，不抓站 |

---

## 怎么扩展

每个默认实现都是 `@ConditionalOnMissingBean`。发布你自己的 bean，自带的那个就不会被创建。
没有注册文件，也没有排序属性。

``` java
@Bean
UrlPathAcceptor mySiteRules() { ... }           // 加进链里

@Bean
WebCrawlerComponentFactory myFactory() { ... }  // 整体替换
```

| 接口 | 决定什么 | 自带实现 |
|---|---|---|
| `Extractor` | 页面怎么抓 | restclient、htmlunit、playwright、selenium、adaptive |
| `RenderingDetector` | 拿回来的是不是没渲染的空壳 | 两个长度阈值 |
| `UrlPathAcceptor` | 一个链接跟不跟 | 域边界、起始 url、静态资源、robots.txt、深度、路径模式 |
| `ExistingUrlPathFilter` | 这个 url 是不是见过 | rocksdb |
| `ContentDedupFilter` | 两个 url 是不是同一个页面 | sha256、simhash |
| `ContentExtractor` | 页面里的正文 | 链接密度法去模板 |
| `DocumentContentParser` | 非 html 文件里的文本 | text、markdown、csv |
| `CompletionChecker` | 爬取什么时候结束 | 已存页数、已用时间 |
| `CrawlFrontier` | 还剩什么要爬的队列 | rocksdb |
| `OutputChannel` | 结果写到哪儿 | file、index、vector |
| `BlobStore` | 页面和图片的字节 | local、minio |
| `Searcher` / `IndexAdmin` | 全文 | lucene、elasticsearch |
| `VectorStore` | 语义和图片 | lucene、elasticsearch、qdrant、weaviate |
| `EmbeddingClient` | 把文本变成向量的那一环 | 本地 ONNX、ollama、openai |
| `CatalogStore` / `ResourceRecordStore` | 元数据 | JPA、json 文件、内存 |

三条路进去，值得知道一次改动该走哪条：

| | 怎么做 | 影响范围 |
|---|---|---|
| 一项配置 | `.env`，或 catalog 上的一个字段 | 已经写好的一切，换个配置 |
| 一个 bean | 发布一个 | 某一块，整套安装都生效 |
| 一个类名 | 写在 catalog 上，按名字实例化 | 某一块，只对一个 catalog |

把 api 嵌进你自己的应用，仍然是显式的：

``` java
@EnableGreenfingerServer
@SpringBootApplication
public class MyApplication { }
```

---

## 真正跑起来

启动不需要任何前置。默认就是 H2 加内嵌 Lucene 索引。每一个外部系统都是可选的，一次一个变量。

``` shell
# deploy/.env
GF_DB_URL=jdbc:postgresql://db:5432/greenfinger   # 或 MySQL、SQL Server、Oracle、SQLite
GF_INDEX_PROVIDER=elasticsearch
GF_ES_URIS=http://es:9200
GF_VECTOR_STORE=qdrant
GF_QDRANT_URL=http://qdrant:6333
GF_FILE_TARGET=minio

GF_LUCENE_ANALYZER=smartcn        # standard | smartcn | cjk
GF_ES_ANALYZER=ik_max_word        # 需要装 analysis-ik 插件
```

分析器比大多数人以为的更重要。standard 分析器把中文切成一个个单字，中文分析器会把法文字符直接丢掉。
两边各一个节点级设置就够了。

登录就是一个文件，两个角色。SUPPORT 能读每一个页面，而任何会写的按钮都不会出现在他面前：

``` xml
<users>
  <user name="admin"  password="..." roles="ADMIN"/>
  <user name="tester" password="..." roles="SUPPORT"/>
</users>
```

| 层 | 版本 |
|---|---|
| JDK | 17 或更高 |
| Spring Boot | 4.1 |
| Spring Shell | 4.0 |
| RocksDB | 10.x，frontier 和两套去重 |
| Apache HttpClient | 5.x |
| Jsoup | 1.23 |
| Lucene | 9.12，内嵌索引和向量库 |
| Netty | 4.1，集群传输，内置 NIO 兜底 |
| Angular | 21，signals 加 Material 3 |

---

## 它适合谁

| | 为什么 |
|---|---|
| **开发者** | 一条命令指向一个站点，一个 bean 改掉它的行为 |
| **团队** | 一份可复现的搜索或 RAG 语料。带版本、可重建，在你想要外部服务之前不需要任何外部服务 |
| **企业** | 没有单点故障，靠多起进程扩容，存储和数据库都用你本来就在跑的 |

---

源码、issue 和完整文档：**https://github.com/paganini2008/greenfinger**

Apache License 2.0。
