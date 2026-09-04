# Greenfinger 2.0 设计文档

> 状态：**待评审**。本文是开发前的定稿依据，评审通过后再动代码。
> 参考基线：`history/greenfinger-spring-boot-starter` 与 `history/greenfinger-ui`（1.x，只读参考，不修改，后续删除）。
>
> 2026-08-31：模块 `greenfinger-spring-boot-starter` 更名为 `greenfinger-api` 并改为可独立运行的 web 应用；新增登录与前端，见第 12 章。

---

## 1. 总体架构

### 1.1 两条互不依赖的路径

2.0 最核心的架构约束是**搜索路径与管理路径彻底分离**：

| | 存放内容 | 服务对象 |
|---|---|---|
| **ES + 向量库** | 多 version 并存，metadata 自带一份（含 version、文件路径、图片信息） | **搜索的唯一数据源** |
| **数据库** | 多 version 并存，只存 metadata（不存正文、不存 html） | 爬取任务的定义与触发、`update` 的续爬定位、内部排查 |
| **文件系统 / MinIO** | 多 version 并存，html + txt + 图片字节 | 原文与图片的实际载体，搜索结果的回显目标 |

**搜索永远不查数据库。** 判据：把 `crawler_resource` 相关表整个删掉，搜索功能不受任何影响。

这条约束推导出后面几乎所有设计：ES / 向量库的 metadata 必须自足，图片与页面的关联关系必须冗余进检索层，向量点的 payload 必须携带来源页面信息。

### 1.2 模块划分

```
greenfinger-core                 引擎、六大可插拔组件、frontier、三条输出通道、两种向量库、
                                 embedding、JPA 持久化，以及 CrawlerLauncher /
                                 CatalogAdminService / DeletionService / ReplayService 这层服务
greenfinger-api                  面向 web 的服务端：REST 接口 + 统一响应体 + 异常处理 +
                                 Spring Security 登录 + 托管前端静态页
greenfinger-shell                spring-shell 命令 + 终端渲染
frontend/greenfinger-ui          Angular 21（signals）+ RxJS + Angular Material + Tailwind
deploy/                          发布物：两个启动脚本、两个 jar、config/、static/、.env
```

三个后端模块，不是五个：`greenfinger-output` 与 `greenfinger-record` 已并入 core。

**两个前端并列，都直接依赖 core，互不穿透**：

```
              ┌─ greenfinger-shell （面向命令行，无 web、无登录）
core ─────────┤
              └─ greenfinger-api   （面向 web）── frontend/greenfinger-ui
```

CLI 不经过 api —— 它不需要 servlet 容器，也不该被 web 层的依赖牵连。服务层因此住在 core 里，两个前端调的是同一份。

**两个启用注解，分层对应**：

| 注解 | 在哪 | 提供什么 |
|---|---|---|
| `@EnableGreenfingerCrawler` | core | 引擎、输出、持久化、服务层。CLI 用这个 |
| `@EnableGreenfingerServer` | api | 上面全部，**加** REST 接口、登录与静态页（仅 web 环境下装配） |

都不走 `META-INF` 自动装配 —— 光躺在 classpath 上不应该就去开 RocksDB、占爬取信号量。

### 1.3 层级概念

```
catalog   爬取任务的定义与描述，最大单位。crawl / update / rebuild / delete 的操作对象都是它
  └─ resource   一个爬下来的 URL，带 version；同一 URL 在不同 version 下是不同的 resource
       └─ image  页面中引用的图片；resource 与 image 是多对多
```

---

## 2. 数据模型

### 2.1 四张表

```sql
-- 爬取任务定义
create table crawler_catalog (
    id                      char(36)      not null,
    name                    varchar(255)  not null,
    url                     varchar(255)  not null,
    start_url               varchar(1000),
    cat                     varchar(45)   not null,   -- 用户自定义分类标签，系统不做维护
    path_pattern            varchar(2000) not null,
    excluded_path_pattern   varchar(2000),
    page_encoding           varchar(45)   default 'UTF-8',
    max_fetch_size          integer,
    depth                   integer       default -1,
    fetch_interval          bigint,
    duration                bigint,
    counting_type           integer,
    max_retry_count         integer       default 0,
    url_path_acceptor       varchar(2000),
    url_path_filter         varchar(45)   default 'rocksdb',
    extractor               varchar(45)   default 'restclient',
    output_types            varchar(100)  not null default 'file',
    image_enabled           boolean       default true,   -- 抓不抓图
    downstream_content      varchar(20)   default 'text_image',  -- index/vector 要不要图片: text | text_image
    running_state           varchar(45),
    index_version           integer       not null default 0,
    search_version          integer       not null default -1,
    max_versions            integer       not null default 10,
    last_indexed            timestamp,
    last_modified           timestamp,
    primary key (id),
    unique (name)
);

-- 一个 URL 在一个 version 下的抓取记录（只有 metadata）
create table crawler_resource (
    id                        char(36)      not null,
    catalog_id                char(36)      not null,
    version                   integer       not null,
    url                       varchar(1000) not null,
    url_hash                  char(64)      not null,
    title                     varchar(1000),
    cat                       varchar(45)   not null,
    content_hash              char(64),
    depth                     integer,
    referer                   varchar(1000),
    html_file_path            varchar(1000),
    html_content_file_path    varchar(1000),
    create_time               timestamp,
    primary key (id),
    constraint uk_resource unique (catalog_id, version, url_hash)
);

-- 图片实体，按 catalog + version 内的字节去重
create table crawler_image (
    id                 char(36)      not null,
    catalog_id         char(36)      not null,
    version            integer       not null,
    content_hash       char(64)      not null,
    first_source_url   varchar(1000),
    image_file_path    varchar(1000) not null,
    content_type       varchar(100),
    width              integer,
    height             integer,
    bytes              bigint,
    create_time        timestamp,
    primary key (id),
    constraint uk_image unique (catalog_id, version, content_hash)
);

-- 页面与图片的多对多关系；alt/title/上下文是"这个页面怎么引用这张图"的属性
create table crawler_resource_image (
    id             char(36)      not null,
    catalog_id     char(36)      not null,
    version        integer       not null,
    resource_id    char(36)      not null,
    image_id       char(36)      not null,
    source_url     varchar(1000) not null,
    alt_text       varchar(1000),
    title_text     varchar(1000),
    context_text   varchar(2000),
    create_time    timestamp,
    primary key (id),
    constraint uk_resource_image unique (resource_id, image_id)
);

create index idx_catalog_running_state  on crawler_catalog(running_state);
create index idx_catalog_cat            on crawler_catalog(cat);
create index idx_resource_catalog_ver   on crawler_resource(catalog_id, version);
create index idx_resource_create_time   on crawler_resource(catalog_id, version, create_time);
create index idx_image_catalog_ver      on crawler_image(catalog_id, version);
create index idx_ri_catalog_ver         on crawler_resource_image(catalog_id, version);
create index idx_ri_resource            on crawler_resource_image(resource_id);
create index idx_ri_image               on crawler_resource_image(image_id);
```

### 2.2 三个必须解释的设计点

**a. `url_hash` 存在的理由是 MySQL。**
1.x 的唯一约束 `unique (catalog_id, cat, version, title, url)` 直接建在 `url varchar(1000)` 上，那是因为它只跑 PostgreSQL（建表语句里的 `int4`/`int2`/`timestamp without time zone` 是 PG 语法）。InnoDB 的索引键上限是 3072 字节，utf8mb4 下单列最多 768 字符，同样的约束搬到 MySQL 会**直接建表失败**。所以增加 `url_hash char(64)`（url 的 SHA-256），唯一索引建在它上面。这一列同时也是 `resource.id` 的计算输入。

**b. 子表冗余 `catalog_id` 和 `version`。**
删除 API 要按 `(catalog_id, version)` 直接删三张表，不冗余这两列就得 join，在千万级数据上代价明显。

**c. 数据库不存正文。**
`html` 与 `content_text` 两个大字段从 1.x 移除。全网爬 10 万页，光 `html` 一列就是几十 GB，而它对数据库的两个职责（任务管理、排查）毫无用处。正文以 `.txt` 文件形式落在文件系统 / MinIO，路径记在 `html_content_file_path`。

### 2.3 跨方言注意

- 所有 id 统一 `char(36)` 文本存储（`@JdbcTypeCode(SqlTypes.CHAR)`），h2 / pgsql / mysql / sqlite 四家一致。不使用 PG 原生 `uuid` 或 MySQL `binary(16)`，代价是每行多约 20 字节，换来的是**数据库里 select 出来的 id 与 ES、向量库、文件名字面完全一致**，排查时不需要任何转换。
- 枚举（`counting_type`、`output_types`）继续以普通列存储，不用 JPA 枚举映射。原因见 `Catalog.java` 的注释：Hibernate 7 会为枚举列推断 check 约束，而它在 H2 上生成的约束会拒绝一切非 null 值。
- `interval` 是保留字，列名保持 1.x 已改过的 `fetch_interval`。

---

## 3. ID 规则

### 3.1 一个 id 走全链路

同一个值同时是：数据库主键、文件名、MinIO object key、ES 文档 `_id`、向量库 point id。全部 `char(36)` 的 UUID 文本形式。

### 3.2 生成方式

| 对象 | 版本 | 输入 |
|---|---|---|
| `catalog.id` | **v7** | 随机（时间有序） |
| `resource.id` | **v5** | ns = `catalog.id`，name = `version + "\|" + url_hash` |
| `image.id` | **v5** | ns = `catalog.id`，name = `version + "\|" + content_hash` |
| `resource_image.id` | **v5** | ns = `catalog.id`，name = `version + "\|" + resource_id + "\|" + image_id` |

**catalog 用 v7 而不是 v5**：catalog 没有真正的自然键（`name` 只是个标签）。若用 `uuid5(name)`，"删掉一个 catalog 再用同名重建"会拿到同一个 id，残留的 ES 文档和向量会被静默地重新关联上去，很难查。

**其余全部 v5（确定性）**：同样的输入永远得到同样的 id，因此整条链路天然幂等——重跑 `replay`、补写某一层、重新 embedding，都不会产生重复数据。数据库的唯一约束不是幂等机制，而是**断言**：一旦触发，说明 URL 去重层出了 bug，应当立即暴露而不是被静默容忍。

**version 必须参与计算。** 若 `resource.id = uuid5(catalog, url)` 不含 version，`rebuild` 写第二版时会直接覆盖第一版的 ES 文档与向量点，多 version 并存就不成立。

### 3.3 各层 id 形态

| 层 | id |
|---|---|
| DB `crawler_resource` | `resource.id` |
| 文件 `pages/*.html` / `*.txt` | `resource.id` |
| 文件 `images/*.jpg` | `image.id` |
| ES 文档 `_id` | `resource.id` |
| 向量库 text collection | `uuid5(catalog.id, version + "\|" + resource_id + "\|" + chunkIndex)` |
| 向量库 image collection | `resource_image.id` |

图片向量的 point id 直接复用 `resource_image.id`，因为二者表达的是同一件事：一个"页面引用图片"的关系对。

### 3.4 向量库的原生 id 与 payload

向量库的原生 point id 对业务没有语义，但它是 upsert / 精确取回的键，而我们的 UUID 本来就是合法 UUID，两边（Qdrant 接受 uint64 或 UUID，Weaviate 只接受 UUID）都原生接收。因此**既用作原生 point id，也镜像进 payload**，两者不冲突，没有取舍。

---

## 4. 存储布局

### 4.1 目录结构

```
{output.file.directory}/
  {catalog-name}/
    v{version}/
      settings.json                        这一版运行时的配置与统计
      pages/{ab}/{cd}/{resource_id}.html
      pages/{ab}/{cd}/{resource_id}.txt
      images/{ab}/{cd}/{image_id}.jpg
```

MinIO 结构完全同构，object key 即上面的相对路径。

不再按 host 分子目录：一个 catalog 下的所有站点（如 `books.toscrape.com` 与 `quotes.toscrape.com`）共用同一组 `pages/` 与 `images/`。文件名是 id，本来就不承载可读性；站点归属由数据库与检索层的 metadata 记录。

`settings.json` 随版本走，删除一个版本时它的运行配置一起被清掉。

### 4.2 分片

`{ab}/{cd}` 取 id 的前两段各 2 个十六进制字符，把单目录文件数控制在可接受范围（几百万文件的扁平目录会让 `ls`、备份工具和 ext4 目录索引都很难受）。

`resource.id` 与 `image.id` 都是 UUID v5，本质是 SHA-1 哈希，**前缀天然均匀分布**，所以按前缀分片是安全的。（若将来改用 v7，前缀是时间戳、一轮爬取内高度集中，就必须改成按后缀分片。）

分片深度由 `greenfinger.output.file.shard-depth` 控制，默认 2，设为 0 关闭。

### 4.3 版本目录

version 进入路径，带来两个直接好处：删除一个版本就是删一个目录（MinIO 是删一个前缀）；不同版本的同名文件天然隔离。

---

## 5. 输出链路

### 5.1 顺序不可颠倒

```
        DB layer  ──►  文件 layer  ──►  从 DB 读回  ──►  index layer  ──►  vector layer
        (关卡)         (正文落地)                        (ES)             (Qdrant/Weaviate)
```

**数据库是关卡。** 它有唯一约束，过不了这一关就不进入后面任何一层。这保证了下游任何一层里的数据，在数据库里都有对应的 metadata。

**为什么要"从 DB 读回"而不是直接用内存里的 `CrawledPage`：** 让 index 与 vector 两层的输入完全来自**已持久化的数据**，而不是内存中的临时对象。这样 `replay` 用同样的输入能重建出字节一致的结果，三层之间不会因为内存对象与落盘内容的细微差异而产生漂移。数据库读回的是 metadata 与文件路径，正文按 `html_content_file_path` 回读 `.txt`。

### 5.2 单页处理的完整步骤

1. 计算 `url_hash` 与 `resource.id`（确定性，此时即可推导出所有文件路径）
2. **DB**：insert `crawler_resource`（路径字段一并写入）
3. **DB**：逐张图片 insert `crawler_image`（撞唯一约束说明本版本内已有同样字节，复用已有行），再 insert `crawler_resource_image`
4. **文件**：写 `.html`、`.txt`、图片字节
5. **读回**：按 `resource.id` 取回该行及其关联的 image 行，按路径读回 `.txt`
6. **index**：组装 ES 文档并写入
7. **vector**：正文切块 → embedding → text collection；图片 → embedding → image collection

### 5.3 失败处置

| 层 | 失败行为 |
|---|---|
| **DB** | 中止该页的处理，计入 error 计数；`DuplicateKeyException` 额外打 ERROR（这是去重层的 bug 信号，不是正常流程）。连续失败超过阈值则中止整轮爬取 |
| **文件** | 同上。文件是"源"，缺了它索引和向量都无法重建 |
| **index** | 只记 WARN + 计数，爬取继续。事后用 `replay --layers index` 补 |
| **vector** | 同 index |

一次 ES 抖动不应该毁掉整轮爬取，这是 index / vector 层与前两层的根本区别。

### 5.4 ES 索引与文档结构

**一个 catalog 一个索引**，名字是 `<prefix>-<catalogId>`（`greenfinger.output.index.prefix`，
默认 `greenfinger`），装这个 catalog 的所有版本。1.x 和 2.0 的第一版都是全部塞进一个索引
（`webcrawler_resource`），后来改掉了 —— 三件事变好，一件变坏：

- 删除一个 catalog 变成 drop 一个索引，而不是 `delete_by_query` 那种只做标记的删除
- 每个 catalog 可以有自己的分析器，一个站点是中文另一个不是的时候，这是刚需
- 指名了 catalog 的搜索只读那几个索引，而不是过滤整个语料

变坏的是：跨全部 catalog 的搜索现在要扇出到 n 个索引 —— 对这个项目预期的几十个 catalog 而言很便宜，
而且名字是**前缀**正是为此：`<prefix>-*` 一个请求就寻址全部。

**用 catalogId 而不是 name**：name 可以改，改完索引就成了孤儿，且没有任何东西说得清它原来属于谁。

版本隔离仍然靠 `catalogVersion` 这个 keyword 字段（值形如 `"<catalogId>:<version>"`）：

- 搜索：`terms: { catalogVersion: ["A:6", "B:5"] }` —— 一次表达多个 catalog 各自不同的当前版本，比嵌套的 `(catalogId=A AND version=6) OR (...)` 高效
- 删除一个版本：`delete_by_query { term: { catalogVersion: "A:6" } }`
- 同一个复合字段也用在向量库的 payload 上（见 5.5），两边写法对称

```json
{
  "_id": "<resource.id>",
  "catalogId": "...", "catalog": "toscrape", "cat": "default",
  "version": 3, "catalogVersion": "<catalogId>:3",
  "url": "...", "title": "...", "content": "...",
  "contentHash": "...", "depth": 2, "createTime": 1756512000000,
  "htmlFilePath": "toscrape/v3/pages/ab/cd/xxx.html",
  "htmlContentFilePath": "toscrape/v3/pages/ab/cd/xxx.txt",
  "images": [
    { "imageId": "...", "imageFilePath": "...", "alt": "...", "title": "...",
      "width": 480, "height": 320, "contentType": "image/jpeg" }
  ]
}
```

`images` 是 nested 字段。搜索命中页面，图片直接跟着结果返回——这就是 "搜文字找图片" 的主流做法（图片靠它周围的文字被检索到），不需要图像向量参与。

> 删除**一个版本**仍然只能走 `delete_by_query`（一个索引装着这个 catalog 的全部版本），那是标记
> 删除，磁盘空间要等段合并才真正回收；可选地触发一次 `_forcemerge?only_expunge_deletes=true`，
> 由 `--forcemerge` 开关控制，默认关闭（大索引上这个操作很重）。删除**整个 catalog** 不受这个
> 限制：直接 drop 索引，立即、彻底、没有要合并的东西。

### 5.5 向量库结构

两个 collection，因为文本模型与图像模型的向量**不在同一个空间**（multilingual-e5-small 输出 384 维，SigLIP 2 输出 768 维，二者不可比较）：

| collection | point id | payload |
|---|---|---|
| `greenfinger_text` | `uuid5(catalog, version\|resource_id\|chunkIndex)` | catalogId, catalog, version, resourceId, url, title, chunkIndex, chunkText, htmlFilePath |
| `greenfinger_image` | `resource_image.id` | catalogId, catalog, version, imageId, imageFilePath, alt, title, width, height, contentType, **resourceId, 来源页面的 url 与 title** |

图片向量按**"页面-图片"引用对**存点（方案 B）：同一张图被 N 个页面引用就是 N 个点，但**向量只计算一次、缓存复用**，多花的是存储不是算力。这样做的理由是自足性——搜到一张图立刻知道它出自哪个页面，永远不需要回查数据库，也永远不需要在后续页面引用同一张图时去修改已有点的 payload（那会违反"只 insert 和 delete，不 upsert"的原则）。

**用文字搜图片（向量路）时，查询必须用 SigLIP 2 的文本塔编码，不能用 e5** —— 否则算出来的相似度是噪声。接口上由 `queryToImageVector(String)` 承担。

### 5.6 图片的两个开关

图片是副产品，**不为它单独设计计数类型**——`INDEXED_RESOURCE_COUNT` 是 URL 维度的，`maxFetchSize` 也按 URL 维度封顶，图片不参与限流。取而代之的是两个开关：

| 字段 | 取值 | 作用 |
|---|---|---|
| `crawler_catalog.image_enabled` | `true` / `false`，默认 `true` | **抓不抓图**。关闭即纯文本爬取，file 层没有 `images/` 目录，`crawler_image` / `crawler_resource_image` 两张表为空 |
| `crawler_catalog.downstream_content` | `text_image` / `text`，默认 `text_image` | **index 与 vector 要不要图片**。`text` 表示图片照抓照存文件照入库，但 ES 文档不带 `images` 字段、向量库不写 image collection |

CLI 对应 `--images true|false` 与 `--content text+image|text`（取值就是这两个字面量）。

两个开关分开的理由：抓图很便宜（一次 HTTP），而图片进下游很贵——ES 的 nested 字段会放大文档数，图片向量更是每个"页面-图片"引用对一条。想留着图片以后再决定要不要索引，就用 `--content text`；将来改主意，`replay --layers index,vector` 就能补上，不用重爬。

两个 collection 的 payload 都额外携带 `catalogVersion`（`"<catalogId>:<version>"`），与 ES 的写法对称：搜索用 any-of 匹配一次表达多个 catalog 的当前版本，删除一个版本用等值过滤。

Qdrant / Weaviate 都需要在 `catalogVersion` 上建 payload index，否则删除与过滤会退化成全量扫描。

---

## 6. version 机制与三个语义

### 6.1 version 的两个作用（对齐 1.x）

**作用一：URL 去重过滤器的命名空间。** 1.x 的 `RedisBasedUrlPathFilter.java:31` 与 `RocksDbUrlPathFilter.java:57` 都是 `f(catalogId, version)`。version+1 等于换了一个空的过滤器，因此从 `start_url` 重爬时全部 URL 都是"没见过"的——这正是 `rebuild` 能够重新爬到相同 URL 的机制。

**作用二：检索层的代次标记。** ES 和向量库都用 `catalogVersion` 过滤，不切换容器。

### 6.2 `index_version` 与 `search_version`

1.x 有一个未暴露的缺陷：`rebuild` 一上来就 version+1，而搜索取 `max version`（`ElasticsearchResourceIndexManager.java:212-213`），于是**从 rebuild 开始到爬完之前，搜索查不到任何东西**。

2.0 拆成两个字段修掉它：

| 场景 | index_version | search_version | 搜索看到 |
|---|---|---|---|
| 稳态 | 5 | 5 | v5 |
| rebuild 开始 | **6** | 5 | 仍是 v5，无空窗 |
| v6 完成 | 6 | **6** | 切到 v6 |
| v6 中断且不 resume | 6 | 5 | 仍是 v5 |

一个 catalog 的索引装着它的全部版本，向量 collection 也是（按 embedding 宽度分，不按版本），
因此"切换"不是切容器，而是**搜索时用的过滤值从 `"<catalogId>:5"` 变成 `"<catalogId>:6"`**。`search_version` 就是这个值的来源，一次数据库更新即完成切换。

### 6.3 三个语义（对象都是 catalog，不针对单个 URL）

| 语义 | index_version | 起点 | URL 过滤器 | frontier | 数据 |
|---|---|---|---|---|---|
| **crawl** | 不变 | `start_url`（无则 `url`） | 沿用当前命名空间 | 沿用 | 追加 |
| **update** / **resume** | 不变 | 见下方起点解析顺序 | 沿用（因此只捡新 URL） | 沿用 | 追加 |
| **update --refresh**（merge） | **不变** | 同上 | **绕过**（因此重访已知 URL） | 沿用 | **按内容指纹合并** |
| **rebuild** | **+1** | `start_url` | **新命名空间（空）** | **新的** | **旧版本原样保留** |

**`resume` 与 `update` 是同一个语义**，只是两个入口名字。共同点是三条：同一个 version、URL 过滤器不清空、数据追加而非覆盖。因为过滤器还留着上一轮见过的全部 URL，所以两者都只会捡到**新出现的** URL——任务被 kill 之后重新拉起，本质上就是这件事。

**起点解析顺序**（自动判断，用户不需要关心用的是哪一种）：

1. **持久化 frontier 里还有剩余任务** → 从 frontier 继续。这是最精确的一种：被 kill 时正在处理和排队中的 URL 都还在里面，一条不丢
2. **frontier 为空**（比如被 `--fresh` 清过，或进程在 frontier 落盘前就没了）→ 退回 1.x 的做法，从 `getLatestReferencePath()`（该 catalog 该 version 下最近保存的一条 URL）继续
3. **两者都没有** → 从 `start_url`（无则 `url`）开始。此时过滤器仍然生效，所以走一遍已爬过的路径不会重复保存，只是多花一点网络往返

用 `--from <url>` 可以显式指定起点，覆盖上面的自动判断。

`rebuild` 与 1.x 唯一的、有意的偏离：**不再调用 `cleanCatalog`**。1.x 的 `WebCrawlerService.rebuild()` 会先把该 catalog 的全部 resource 与 ES 文档删光，因此 1.x 实际上从不累积多版本。2.0 保留旧版本，删除交给独立的删除 API。

爬取成功完成时：`search_version = index_version`（搜索过滤值随之切到新版本）→ 触发 prune（见 8.4）。

### 6.3.0 `update --refresh`：合并，不是重爬

普通 `update` 只发现新 URL，看不见已有页面的内容变化。`--refresh` 把它补齐，做的是**合并**：

1. **绕过持久化 URL 过滤器**，重访已知 URL（改用一个 run 内的访问集合，保证本轮每个 URL 只抓一次）
2. 抓回来算内容指纹，与该 URL **已存的** `content_hash` 比对
   - 相同 → 计入 `duplicatedContentCount`，**四层一个字节都不写**
   - 不同 → 四层**覆盖写**
   - 没有记录 → 新页面，正常插入

**能成为覆盖而不是新增，全靠 id 用了 UUID v5**：`resource_id = uuid5(catalog, version|url_hash)`，同一 URL 在同一 version 下恒定，于是数据库主键、文件路径、ES `_id`、向量 point id 全都不变，写第二遍就是覆盖第一遍。换成 v7 这条路根本不成立。

比对的是**该 URL 自己的**历史指纹，不是全局内容去重库——后者回答的是"这些字我见过没有"，refresh 时对每一页都为真，会把整站丢光。

#### 限流保护

一次被 `maxFetchSize` 或 `duration` 截断的 merge，比报错更糟：走到的页面更新了，没走到的悄悄留着旧内容，而且没有任何记录说明哪些是哪些。所以两道闸门：

- **开始前**：已存记录数 ≥ `maxFetchSize` → 直接拒绝，并告诉你该调到多少
- **结束后**：因限流或超时停止 → 抛异常，说明还剩多少 URL 没处理

```
A merge would have to revisit 1195 page(s) but maxFetchSize is 300, so it would
stop part way and leave the rest silently stale. Raise it above 1195 and run the
merge again.
```

普通 `update` 不受这两道闸门约束——它从没承诺过要重访任何东西。

#### 代价

每个已知页面一次 HTTP 请求。真正省下的是解析、写库、写文件、建索引和 embedding——对大多数站点，绝大部分页面是没变的。

**条件请求（2026-09-01 已实现）**：把上次的 `ETag` / `Last-Modified` 递回去，让站点用 304 回答，
连这次请求的响应体也省掉。

- `Extractor.fetch(..., ConditionalGet)` 是 default 方法，默认退化为普通 GET 并报告"没有 validator"
- 只有 `RestClientExtractor` 真正实现 —— 浏览器引擎驱动的是页面加载，看不到响应，
  假装支持等于虚报一个不存在的节省
- `RetryableExtractor` / `ThreadWaitExtractor` / `AdaptiveExtractor` 必须显式转发：
  包装器落回 default 会把条件请求悄悄变回普通请求，而且看起来一切正常
- validator 存在 `resource.etag` / `resource.last_modified`；`GF_CONDITIONAL_GET=false` 可关
  （给"页面变了 ETag 不变"的站点）

**连带修正**：304 没有 body，也就没有链接可跟随。这暴露出 merge 本来就有的一个洞 ——
它是靠从入口重新爬链接来找到已知页面的，一个从导航里被拿掉、但仍在站上、仍在索引里的页面，
以前就已经永远不会再被 merge。现在 refresh 开始时直接把上次爬到的每个 url 入队
（`seedFromLastCrawl`，分批 500 条），新页面仍由链接发现，两者并存。

### 6.3.1 `url` 与 `start_url` 的语义

| 字段 | 含义 |
|---|---|
| `url` | 站点入口，同时是 referer 的来源 |
| `start_url` | **前缀约束**：设了它之后，所有被接受的 URL 都必须以它开头，同时它也是本次爬取的种子 |

例：`start_url = https://example.com/a` → `/a/b`、`/a/c` 都在范围内，`/x` 被拒。

实现上由一个专门的 `UrlPathAcceptor` 承担（`start_url` 非空时自动挂上），与 `path_pattern` 是「与」的关系——`path_pattern` 在此基础上进一步收窄。`start_url` 为空时退回 `url`，行为与 1.x 一致。

### 6.4 未完成的版本（已确认）

被中断且不再 resume 的版本是一份不完整的数据，但仍占用一个版本名额。处理方式：

- 版本状态记录在 `crawler_catalog` 的 `running_state` 与运行记录中，`status` 命令显式列出未完成版本
- prune 时**优先删除未完成的版本**，然后才按由老到新删除已完成版本
- 永远跳过 `index_version` 与 `search_version` 这两版

---

## 7. 搜索

搜索**只查 ES 与向量库**，不碰数据库。

| 命令 | 走哪层 | 说明 |
|---|---|---|
| `search --q <keyword>` | ES | 全文检索，返回页面 + nested 图片 |
| `search --q <keyword> --semantic` | 向量库 text collection | 语义检索 |
| `search --q <keyword> --images` | 向量库 image collection | 跨模态：用 SigLIP 2 文本塔编码查询，检索图片 |

结果自足：URL、标题、摘要、文件路径、图片列表全部来自检索层的 metadata。

### 7.1 `cat` 只是用户自定义的分类标签

`crawler_catalog.cat` 是给用户自己归类用的，**系统不基于它做任何维护逻辑**，仅作为搜索时的可选过滤条件。

这是与 1.x 的一处重要偏离。1.x 的 `maximumVersionOfCatalogIndex(cat)` 是**按 cat 取最大版本**（`ElasticsearchResourceIndexManager.java:213`），导致同一个 cat 下的多个 catalog 共享一个版本号视图——catalog A rebuild 到 v6 之后，同 cat 下还停在 v5 的 catalog B 的数据会被搜索完全忽略。这是 1.x 的缺陷。

2.0 的 `search_version` **严格 per-catalog**，各个 catalog 的版本互不影响。

### 7.2 跨 catalog 搜索的版本过滤

既然版本是 per-catalog 的，一次跨 catalog 的搜索就面对若干个各不相同的当前版本，过滤条件不能是 `version = N` 这样一个值。

两边用同一个办法：一个 `catalogVersion` 复合字段（`"<catalogId>:<version>"`）。

- **ES**：`terms: { catalogVersion: ["A:6", "B:5", ...] }`
- **向量库**：payload 上的 any-of 匹配，`should: ["A:6", "B:5", ...]`

单字段、一次匹配，比嵌套的 `(catalogId=A AND version=6) OR (...)` 高效得多，而且 ES 与向量库
两侧写法完全对称。

### 7.3 replay：用数据库恢复 index 与 vector

数据库保有全部版本的 metadata 与文件路径，因此 **ES 或向量库被误删、写坏、或中途失败，都可以从数据库整体重建**。

```bash
replay --catalog <name> --version 6 --layers index,vector
replay --catalog <name> --version 6 --layers vector      # 只重建向量
```

- 输入与正常写入链路完全一致：从数据库读回 metadata，按 `html_content_file_path` 回读 `.txt`，因此结果必然一致
- **replay 是覆盖写**。这与爬取链路"只 insert 不 upsert"的原则并不冲突：所有 id 都是 UUID v5，由自然键确定性生成，同样的输入必然得到同样的 id，写第二遍就是覆盖第一遍，天然幂等
- **数据库是唯一的元数据源**。曾经有过一个 `manifest.jsonl`（每页一行 json，写在版本目录下）
  想作灾难恢复用，2026-09-02 删掉了：它的每一个字段
  （url / title / cat / contentHash / depth / referer / 文件路径 / 图片的 id、尺寸、alt、sourceUrl）
  在 `crawler_resource`、`crawler_image`、`crawler_resource_image` 里都有，而且数据库还多出
  `url_hash`、`etag`、`last_modified`、`link_count` 等字段。它是个字段更少的反规范化副本，
  而"用它重建数据库"这个能力从来没有实现过——没有任何代码读它。
  分布式之后数据库在每个节点上都有一份副本，这条理由更站不住。
  顺带解决两个 bug：本地文件的跨进程 append 只有 JVM 内锁（同机多进程会写出半行），
  以及 MinIO 没有 append、`appendLine` 是读整个对象再写回（并发时静默丢行，而且第 n 页
  要重写前 n-1 行，O(n²)）。`BlobStore.appendLine` 整个方法一并移除。

---

## 8. 删除 API

### 8.1 命令形态

```bash
delete --catalog <name> --version 3                      # 删第 3 版，四层全删
delete --catalog <name> --version 3 --layers file,vector # 只删文件与向量，DB / index 保留
delete --catalog <name> --before 5                       # 删第 5 版之前的所有版本
delete --catalog <name> --keep-latest 2                  # 只保留最近两个版本
delete --catalog <name> --all                            # 整个 catalog 的所有版本
delete --catalog <name> --version 3 --dry-run            # 只报告，不动手
```

`--layers` 取值：`db` / `file` / `index` / `vector` / `all`，默认 `all`。"保留哪些层"通过只列举要删的层来表达，不另设 `--retain`。

### 8.2 删除顺序 = 写入顺序的逆序

```
vector  ──►  index  ──►  file  ──►  db
```

**数据库必须最后删**，因为它是"要删哪些东西"的清单来源。先删 DB 就找不到文件路径了。（若 `--layers` 不含 `db`，顺序无所谓，但默认全删的场景下这是硬要求。）

### 8.3 各层实现

| 层 | 做法 | 注意 |
|---|---|---|
| **vector** | Qdrant `delete by filter (catalogId, version)`；Weaviate batch delete by where | 需要 payload index；Qdrant 是软删，空间等 optimizer 回收 |
| **index** | 删版本：`delete_by_query { term: { catalogVersion: "<catalogId>:<version>" } }`；删整个 catalog：drop 索引 | 一个索引装这个 catalog 的全部版本，所以按版本删只能按查询删，是标记删除，空间等段合并才回收（可选 `--forcemerge`，默认关闭）。整个 catalog 则是 drop，立即生效 |
| **file** | 删 `{catalog}/v{version}/` 整个目录 | version 在路径里，这一步最干净 |
| **MinIO** | 按前缀列举 + 分批删除 | S3 协议**没有原生前缀删除**，每批最多 1000 个 object |
| **db** | 按 `(catalog_id, version)` 删三张表 | 子表已冗余这两列，无需 join |

### 8.4 自动清理（prune）

`crawler_catalog.max_versions` 默认 **10**，**默认开启**。

- 触发时机：一次爬取**成功完成**、`search_version` 推进之后。绝不在爬取过程中动手
- 行为：等价于对超出的版本执行 `delete --layers all`
- 永远跳过 `index_version` 与 `search_version`
- 优先删除未完成的版本
- 结果计入运行摘要并打 INFO 日志

> **容量提示**：四层都是多版本，`max_versions = 10` 意味着最多保留 10 份完整副本——10 倍磁盘 + 10 倍 ES 索引 + 10 倍向量。全网爬虫下这不是小数目。调整只需改这一个配置。

### 8.5 安全措施

- `--dry-run` 输出每层将要删除的数量与体积
- **拒绝删除正在爬取的版本**（检查 `running_state` 与 `WebCrawlerSemaphore`）
- **默认拒绝删除 `search_version`**，需 `--force`
- **幂等**：删除已删除的版本是 no-op。四层删除不可能原子，中途失败必须能直接重跑
- **逐层汇报**：某层失败不影响其他层继续，最后输出一张表说明每层的成功/失败

---

## 9. Embedding 与本地模型

### 9.1 接口

```java
public interface EmbeddingClient {

    String getName();

    /** 文本向量。任何实现都必须提供。 */
    float[] textToVector(String text);
    int textDimensions();

    /** 以下三个为可选能力。只扩展文本向量的实现无需理会。 */
    default float[] imageToVector(byte[] image, String contentType) {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }
    default int imageDimensions() {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }
    /** 与图向量同空间的文本编码，跨模态检索用 */
    default float[] queryToImageVector(String text) {
        throw new UnsupportedOperationException(getName() + " does not embed images");
    }
}
```

流水线在启动时探测一次能力：不支持图像的实现，图片向量整条跳过，只记一条 INFO，**不报错、不中断爬取**（图片照样入库、入文件、入 ES）。

### 9.2 三个内置 provider

| provider | textToVector | imageToVector |
|---|---|---|
| **local（默认）** | `multilingual-e5-small`，384 维，ONNX | `SigLIP 2 base`，768 维，ONNX |
| `ollama` | 默认 `qwen3-embedding:4b`（端点 `http://localhost:11434`） | 不支持（抛 UnsupportedOperationException） |
| `openai` | `text-embedding-3-small`，1536 维 | 不支持（OpenAI 无公开图片 embedding 接口） |

许可说明：默认这套 e5（MIT）+ SigLIP 2（Apache-2.0）均可商用。是否商用完全由用户选择哪个 provider 决定，Greenfinger 不给用户挖坑。

### 9.3 零配置怎么落地

"零配置"指**用户不开账号、不充值、不装服务**，而不是零下载——模型权重打不进 jar。

- 默认输出只有 `file`，**不触发任何模型下载**，`./greenfinger-cli.sh` 依然秒开
- 只有显式启用 `vector` 输出时才拉取模型，届时给明确的下载进度
- 缓存目录 `~/.greenfinger/models/`，由 `greenfinger.embedding.model-dir` 覆盖
- `models pull` 预热命令与 `--offline` 开关（2026-09-01 已实现）。文件清单由 `ModelFile`
  统一持有，加载路径和 pull 用同一批常量，不会各自维护一份；`--offline` 在 Spring 启动前
  翻译成 `greenfinger.embedding.offline` 属性，因为 model store 在构造时就要读它
- 体积：`multilingual-e5-small` ONNX int8 约 120 MB，`SigLIP 2 base` ONNX 量化后约 400 MB

### 9.4 技术栈与风险

DJL + ONNX Runtime + `ai.djl.huggingface:tokenizers`。两个已知风险：

1. SigLIP 2 的图像预处理（224 resize、mean/std 0.5 归一化）需要在 Java 中手写
2. ONNX 导出件需要先实际跑通再锁版本

另外 onnxruntime 的 native 库会让最终 jar 增大约 50–100 MB。

### 9.5 维度与 collection 的绑定

**向量库的 collection 在创建时就锁定了维度，换 provider 会让已有 collection 不可用。** 三个 provider 的维度各不相同：

| provider | 文本维度 |
|---|---|
| local（multilingual-e5-small） | 384 |
| ollama（qwen3-embedding:4b） | 由模型决定，**启动时探测** |
| openai（text-embedding-3-small） | 1536 |

处理方式：

1. **不硬编码维度**，启动时对 provider 做一次探测（embed 一个空串，量出返回向量的长度），以探测值为准。这样换模型、换 tag 都不需要改配置
2. **collection 名带维度后缀**，例如 `greenfinger_text_384`、`greenfinger_text_2560`。不同模型的数据自然分开存放，互不污染
3. 打开一个已存在的 collection 时**校验维度**，不一致就用明确的错误信息拒绝启动（说清楚"collection 是 N 维、当前模型是 M 维"），而不是让写入在运行中途报一堆看不懂的错

同一条规则适用于图像 collection。

---

## 10. 爬取边界（硬约束）

一次爬取绝不能从 `www.a.com` 跑到 `www.b.com`。这条由两个**不可配置、不可移除**的 `UrlPathAcceptor` 保证，排在所有其他 acceptor 之前：

| acceptor | order | 规则 |
|---|---|---|
| `DomainScopeUrlPathAcceptor` | `Integer.MIN_VALUE` | URL 的**可注册域**必须与 `catalog.url` 相同。兄弟子域算同一站点（`books.toscrape.com` 与 `quotes.toscrape.com` 互通） |
| `StartUrlPrefixUrlPathAcceptor` | `MIN_VALUE + 1` | URL 必须以 `start_url` 为前缀（`start_url` 为空时回退 `url`）。比较时忽略 scheme，且要求前缀后是 `/`、`?`、`#` 或结尾，避免 `/ab` 误收 `/about` |

`path_pattern` / `excluded_path_pattern` 以及自定义 acceptor 在这两道闸门**之内**进一步收窄，永远不能放宽。页面上一个广告链接、一个"powered by"页脚，就足以让没有边界的爬虫跑到互联网的其他地方再也回不来——所以这道线不交给配置。

> 注意：`start_url` 默认等于 `url`，因此默认行为是**单站点**。若要一个 catalog 覆盖多个兄弟子域，把 `url` 设成共同的上级域名（如 `https://toscrape.com`）并显式放宽 `path_pattern`。

### RocksDB 命名空间（已经是对的，勿动）

`DefaultWebCrawlerComponentFactory.scoped()` 已经把 URL 过滤器、内容去重库和 frontier 都放在 `{base}/{catalogId}/v{version}/` 下，与 1.x 的 `f(catalogId, version)` 一致：

```java
private String scoped(String baseDirectory, CatalogDetails catalogDetails) {
    return new File(baseDirectory,
            catalogDetails.getId() + File.separator + "v" + catalogDetails.getVersion()).getPath();
}
```

这是 `rebuild`（version+1 换一个空过滤器）能重新爬到相同 URL 的前提，也是两个 catalog 互不干扰的前提。改动这三个组件的构造方式时不要绕过它。

---

## 11. 配置

配置全部外置、集中在一处：`deploy/config/`，不打进 jar。密钥走 `.env`（不入库）。

**三个文件，CLI 与 web 共用同一份** —— 同一个配置项在两张面孔下含义必须一致，所以不给 web 单独复制一套：

| 文件 | 装什么 |
|---|---|
| `application.yml` | 公共部分：爬虫、输出、embedding，以及只有服务端会用到的 `server.*` 与 `greenfinger.security.*`（CLI 读不到也不需要，Spring 直接忽略） |
| `application-dev.yml` | 零配置，默认 profile：H2 文件库、本地磁盘、`types: file`，什么都不用装 |
| `application-prod.yml` | 真实部署：自带数据库（`GF_DB_*` 必填，故意没有兜底），三条输出全开 |

`GF_PROFILE=dev|prod` 切换。原先按数据库拆的 `application-mysql/pgsql/sqlite.yml` 已删除，四种库的连接串写在 `application-prod.yml` 的注释里 —— 它们的差别只有 driver 和 url，唯独 SQLite 需要显式指定 `GF_DB_DIALECT`，因为 Hibernate 本体不带它的方言。

**SQLite 的两处特殊处理（2026-09-01）**。实测发现按注释配好之后，12 页的爬取只存下 1 页、
72 次 `SQLITE_BUSY`。根因不是并发 —— 单线程一样出错 —— 而是 `ImageWriter` 的
`PROPAGATION_REQUIRES_NEW`：同一个线程内，内层事务写入让外层事务的读快照过期，
撞上 `SQLITE_BUSY_SNAPSHOT`。SQLite 锁的是整个文件，且每个连接有自己的快照。

- `ImageWriter` 在 SQLite 上改用 `PROPAGATION_REQUIRED`，图片行加入页面的事务。
  分开事务的理由（PostgreSQL 在约束冲突后中止整个事务）在 SQLite 上不成立
- `SqliteConnectionPoolCustomizer` 自动给 url 补 `journal_mode=WAL` 与 `busy_timeout=30000`
- 判断从 `spring.datasource.url` 读，不开数据库连接；其余三种库的行为一个字节都没变
- 修完 16 线程 13/12 页、4 线程 6 次冲突。`GF_WORK_THREADS=4` 是给 SQLite 的建议值

```yaml
spring:
  datasource:                       # 默认 h2 文件库，零安装
    url: jdbc:h2:file:./data/.state/greenfinger;AUTO_SERVER=TRUE

greenfinger:
  work-threads: 16
  dedup:
    url:      { directory: ./data/.state/url }      # 运行时再拼 /{catalogId}/{version}
    content:  { enabled: true, type: sha256 }
  output:
    types: file                     # file | index | vector，可叠加，file 恒为必选
    file:
      target: local                 # local | minio （二选一）
      directory: ./data
      shard-depth: 2
      minio: { endpoint: ..., bucket: greenfinger }
    index:
      uris: http://localhost:9200
      prefix: greenfinger           # 索引名 <prefix>-<catalogId>，一个 catalog 一个
      analyzer: standard            # ik_max_word 需要装 IK 插件
    vector:
      store: qdrant                 # qdrant | weaviate （二选一）
      text-collection: greenfinger_text
      image-collection: greenfinger_image
  embedding:
    provider: local                 # local | ollama | openai
    model-dir: ~/.greenfinger/models
    offline: false
```

**`file` 输出恒为必选**：即使只选了 `index` 或 `vector`，也会自动补上 `file`。数据库 metadata + 文本输出是一切下游的前提。

---

## 12. 删除的代码

### 12.1 爬虫端登录（不做）

指的是**被爬网站需要登录**这件事，与第 16 章的管理台登录无关，两者不要混。

`CatalogCredential`、`ExtractorCredentialHandler`、`DoNothingExtractorCredentialHandler`、`StatefulExtractor`、`WebClientHolder`、`ExtractorLifeCycle`、`util/Cookie`；`CatalogDetails` 去掉内嵌 `CatalogCredentials` 与 `getCredentialHandler()`，`Catalog` 去掉 `credential_handler` 列。

### 12.2 死代码与失效配置

| 对象 | 原因 |
|---|---|
| `FileCatalogStore`、`InMemoryCatalogStore` | 数据库转必选后不再需要 |
| `IdGenerator`、`SnowflakeIdGenerator` 及其测试 | 改用 UUID |
| `HashingEmbeddingClient` | 假向量兜底，本地模型上线后无意义 |
| `ProgressBarSupplier` | 主代码零引用 |
| `WebCrawlerProperties.catalogStore` | 三档 store 已归一 |
| `greenfinger.record.enabled`、`Catalog.recordEnabled`、`--record` | 数据库必开 |
| `OutputProperties.local.saveText / saveMetadata` | txt 必写，metadata 进库 |

保留 `QuickStartMain`（IDE 调试入口）。

### 12.3 包结构调整

`com.github.greenfinger.core.util` → `com.github.greenfinger.core.utils`，并把 `com.github.greenfinger.core.BeanLifeCycleUtils` 移入。调整后该包含：`BeanLifeCycleUtils`、`CharsetUtils`、`HashUtils`、`ThreadUtils`、`UrlUtils`、`UrlPathPatterns`、`UuidUtils`（新增）。

---

## 13. CatalogDetailsService 上位

1.x 有 `CatalogDetailsService`，2.0 目前是**死代码**——`CrawlerLauncher.toCatalogDetails()` 现场捏了一个临时 `Catalog`，id 用 `Math.abs(url.hashCode())`（会碰撞），从头到尾没碰过数据库。

改成单一入口，CLI 与将来的 Web 页面共用：

```
CLI / HTTP 参数
      ↓
Catalog 实体  ──►  CatalogAdminService.save()  落库，分配 UUID v7
      ↓
CatalogDetailsService.loadCatalogDetails(id)     默认实现走数据库，接口留给 openspreader / 配置中心
      ↓
CatalogDetails（运行态只读视图）
      ↓
CrawlerLauncher.launch(catalogId)
```

`loadRunningCatalogDetails()` 接 `running_state` 与 `CrawlRegistry`，`status` 命令因此能拿到真实运行状态。

`running_state` 沿用 1.x 的四个取值：`crawl` / `update` / `rebuild` / `none`（见 `WebCrawlerJobService.java:74,97,99` 与 `WebCrawlerEventManager.java:65`）。`resume` 归入 `update`。

### 13.1 并发约束

**全进程同时只允许一个 catalog 在爬**，与 1.x 对齐。`WebCrawlerSemaphore` 保持 `Semaphore(1)`，第二个请求直接被拒绝并提示当前占用者。理由是爬取受带宽约束，并行不带来真实吞吐收益，反而让两个任务瓜分带宽、内存与 RocksDB 实例数翻倍。

---

## 14. 评审结论

**本文已定稿，无遗留待确认项。** 下面记录评审过程中逐条敲定的结果，作为开发依据。

### `context_text` 是什么（保留）

`crawler_resource_image.context_text` 是**图片在页面上的周边文字**——`<img>` 标签最近的块级父节点里的文本，截断到 500 字符。

它的用途只有一个：**让图片能被文字搜到**。一张图片本身不含任何可检索的文字，`alt` 又经常是空的或者只写着 "image"。主流图片搜索（包括 Google 图片）靠的就是图片周围的文字来判断这张图讲的是什么。所以在 ES 的 nested `images` 字段里带上 `context_text`，用户搜"泰坦尼克号"才可能命中一张没有 alt 的剧照。

抽取成本很低（页面已经用 Jsoup 解析过了），存储成本是每个引用关系 500 字符。**结论：保留。**

### 逐条确认结果

- **`update` 语义保留**，且 **`resume` 与它是同一个语义**：同 version、URL 过滤器不清空、只捡新出现的 URL；起点按 frontier → 最近爬取位置 → `start_url` 的顺序自动解析。见 6.3 节。
- **命名对齐**：输出类型 `file / index / vector`，删除层 `db / file / index / vector / all`。
- **Ollama 默认模型**：`qwen3-embedding:4b`，维度启动时探测，见 9.5 节。
- ~~**ES 单索引** `webcrawler_resource`，对齐 1.x~~ —— **后来推翻**：改为一个 catalog 一个索引
  `<prefix>-<catalogId>`，理由见 5.4 节。版本隔离仍靠 `catalogVersion` 复合字段。
- **计数类型不扩充**，`INDEXED_RESOURCE_COUNT` 的 URL 维度已经够用；图片由两个开关控制。见 5.6 节。
- **未完成版本**：`status` 显式标出，prune 优先删。见 6.4 节。
- **单并发**：全进程同时只允许一个 catalog 在爬，对齐 1.x。见 13.1 节。
- **目录结构**：`{catalog}/v{version}/{settings.json, pages/, images/}`，不再按 host 分子目录。见 4.1 节。
- **`cat`** 是用户自定义分类标签，系统不做维护。见 7.1 节。
- **replay** 从数据库覆盖重建 index / vector。见 7.3 节。
- **`context_text` 保留**，用于让图片被文字检索到。
- **目录不再按 host 分层**，一个 catalog 下各站点共用 `pages/` 与 `images/`。见 4.1 节。
- **CLI 开关**：`--images true|false`（抓不抓图）与 `--content text+image|text`（index/vector 要不要图片）。见 5.6 节。

---

## 14.1 正文抽取与 sitemap 种子

### 正文抽取（`ContentExtractor`）

整个 `<body>` 进索引，等于把导航、侧栏、cookie 横幅、页脚一起喂给检索和 embedding —— 这是两者噪声的最大来源：一次搜索命中的词可能只出现在菜单里，一个页面的向量描述的是站点框架而不是页面内容。

方法沿用 Boilerpipe（WSDM 2010）与 Readability 的思路，**不用模型、不用词典**，因此天然跨语言：

1. 先剥掉 `script/style/nav/header/footer/aside/form` 等永远不是正文的标签
2. 有 `<article>` / `<main>` / `[role=main]` 就直接用
3. 否则给每个块级容器打分：`文本长度 × (1 − 链接密度) × 段落数加成 × 类名提示`
4. 链接密度 > 0.5 直接判为菜单，得 0 分
5. 类名/id 命中 `nav|sidebar|comment|advert|...` 降权到 0.2，命中 `article|content|post|...` 提到 1.5

**兜底**：抽出来太短就退回整个 body。列表页本来就没有正文，索引多了好过索引空了。

> **CJK 长度加权**：阈值按字符数算就是按英文算——200 个英文字符是两句话，200 个中文字符是好几段。实测中这条让所有中文正文都被判为"太短"而退回整页。现在 CJK 字符按 4 倍计权，一个阈值在两种文字下含义一致。

配置：`greenfinger.content.extract-article`（默认 true）、`min-block-length`、`min-content-length`。

### sitemap 种子（`SitemapSeeder`）

从首页一层层爬到深层页面要很久，sitemap 一次给几千个。发现路径按标准来：

1. robots.txt 里的 `Sitemap:` 指令（声明位置，优先）
2. 约定位置 `/sitemap.xml`
3. `--sitemap-url` 显式指定（站点放在非常规位置时）

用 crawler-commons 的 `SiteMapParser`（robots.txt 本来就依赖它），支持 sitemap index 递归与 gzip。

**这些只是候选**：每个 URL 照样过 frontier 去重和全部 acceptor，域名边界、`start_url` 前缀、path pattern 一律生效。站点没有 sitemap 的代价是一次 404。

配置：`greenfinger.sitemap.enabled`（默认 true）、`max-urls`（默认 50000，是上限不是目标）、`max-index-depth`。

### URL 归一化改为两层

实测发现自己那套漏了三条 RFC 3986 的语法归一，而 crawler-commons 的 `BasicURLNormalizer` 又不做去重需要的语义归一。现在两层叠加：

| 用例 | 谁处理 |
|---|---|
| `/a/./b/../c` → `/a/c` | crawler-commons |
| `/%7Euser/` → `/~user/` | crawler-commons |
| `//double//slash` → `/double/slash` | crawler-commons |
| 尾斜杠、跟踪参数、参数排序、fragment | 我们 |

---

## 15. 实测记录（2026-08-30）

全部在本机真实环境跑通，非 mock：

| 项目 | 结果 |
|---|---|
| 真实站点爬取 | books.toscrape.com，1001 页 / 6827 图 |
| 中文站点 | ruanyifeng.com，编码正常 |
| **ES** | 单索引 `webcrawler_resource`，1001 文档，检索命中（当时的布局；后来改为一个 catalog 一个索引，见 5.4） |
| **Qdrant** | `greenfinger_text_384` / `greenfinger_image_768` / `_1536` / `_2560` |
| **Weaviate** | `Greenfinger_text_384`，语义检索命中 |
| **MinIO** | 对象布局与本地一致 |
| **PostgreSQL** | 独立 schema 跑通 |
| **MySQL** | crawl → update → rebuild 全流程，v0=44 / v1=21 |
| **embedding: local** | e5 384 维 + SigLIP 2 768 维，**文字搜图片跑通** |
| **embedding: ollama** | qwen3-embedding:4b，实测 2560 维 |
| **embedding: openai** | text-embedding-3-small，1536 维 |
| 单元测试 | 322 个，全绿；core 82.6% / starter 85.3% 行覆盖 |

### 实测中修掉的四个真问题

1. **版本发布语义错了**：因 `maxFetchSize` 正常停止的爬取被当成"未完成"，`search_version` 不推进，搜索查不到任何东西 —— 正是要修掉的 1.x 缺陷。改为：配置的限流是"正常结束"，只有外部中断（Ctrl+C / `interrupt`，二者都不设 `interruptionReason`）才不发布。
2. **`linkCount` 恒为 0**：引擎只是"跟随"链接，没把它记到 `CrawledPage` 上，排序信号是废的。
3. **图片并发主键冲突**：16 个线程抓到同一张图，算出同一个 v5 id，同时 insert。H2 只是 warn，**PostgreSQL 会让整个事务进入 aborted 状态**，同事务内回读也失败。改为 `TransactionTemplate` + `PROPAGATION_REQUIRES_NEW`，冲突后**另开一个事务**回读。
4. **Weaviate schema 缺字段**：payload 里有 `linkTextLength`，class 里没有，GraphQL 会**整条查询报错**而不是忽略该字段，检索返回空。改为 schema 与 payload 从同一处生成，并对已存在的 class 增量补属性。

### 搜索排序：详情页优先

列表页和它链接到的详情页对同一个词的匹配度相当，但用户要的几乎总是后者。用的是 **Boilerpipe（Kohlschütter et al., WSDM 2010）的链接密度**：锚文本长度 / 总文本长度，列表页接近 1，正文页接近 0，且不受页面长短影响（原始链接数会）。

- **ES**：`function_score` + `script_score`，`1.5 - density`，只调整已命中文档之间的次序
- **向量库**：没有 function_score，所以 `VectorSearcher` 过量取回（×4）后客户端重排，并按 resourceId 去重（一篇长文的 20 个 chunk 对读者是一条结果）

`SearchRequest.preferDetailPages` 可关闭。

### ES 深分页

`from + size` 超过 10000 会被 ES 拒绝，调大 `max_result_window` 是拿集群内存换分页。改用 **`search_after` 游标**：排序固定为 `[_score desc, id asc]`（`id` 是 `_id` 的普通字段镜像，因为直接排 `_id` 需要 fielddata），每页返回 `nextCursor`，下一页从它续。代价与深度无关。

---

## 附：与 1.x 的功能对照

| 1.x | 2.0 |
|---|---|
| 六大可插拔组件 | 全部保留 |
| 四种 Extractor（RestClient / HtmlUnit / Playwright / Selenium） | 全部保留 |
| Redis / Redisson BloomFilter 去重 | RocksDB（URL + 内容双重去重） |
| 从"最近一条 URL"续爬 | 持久化 frontier（`resume`）+ 保留 `update` 语义 |
| 只索引到 ES | file / index / vector 三条输出，可叠加 |
| 只爬文本 | 文本 + 图片 |
| `crawler_catalog_index` 独立表 | 合并进 `crawler_catalog` |
| DB 存 html 全文 | DB 只存 metadata，正文落文件 |
| rebuild 先删后爬 | rebuild 只 version+1，删除由独立 API 承担 |
| 搜索期间 rebuild 有空窗 | `search_version` 消除空窗 |
| ES 单索引 `webcrawler_resource_0` | 一个 catalog 一个索引 `<prefix>-<catalogId>`，版本靠 `catalogVersion` 字段隔离 |
| 版本按 `cat` 取最大值（缺陷） | 版本严格 per-catalog |
| 自动任务内置 | 外置，不移植 |
| 网页登录态 Extractor | 本期不做，相关代码删除 |
| `InterruptionChecker` 及两个实现 | 更名 `CompletionChecker`，语义不变 |
| `WebCrawlerHandler` / `WebCrawlerService` | `CrawlerEngine` / `CrawlerLauncher` |
| `ResourceManager`（一个接口管全部） | `CatalogStore` + `ResourceRecordStore` |
| `WebCrawlerExecutionContextUtils`（静态 Map） | `CrawlRegistry`（注入的 bean） |
| `WebCrawlerEventManager` + 三个事件 | 收尾在 `CrawlerLauncher` 的 finally（顺序确定，不靠异步）；`WebCrawlerCompletionEvent` 走集群广播回归，见 17.4.2 |
| `WebCrawling` + `WebCrawlingChecker`（AOP 切面） | `WebCrawlerSemaphore` + 显式检查 |
| `ProgressBarSupplier` | face 端 ANSI 双进度条 + 前端 `mat-progress-bar` |
| `POST /index/sync`（从库重建索引） | `replay`，且可分层（index / vector / file） |
| `PUT /index/upgrade` | 不做，与 `replay` 重合 |
| 只支持 MySQL | H2 / SQLite / MySQL / PostgreSQL / SQL Server / Oracle，六种都跑通端到端回归 |
| jdbc DAO 手写 SQL | Spring Data JPA；建表脚本从实体生成，见 `docs/sql/` |

2026-09-04 逐类核对了 1.x 的 111 个类：除了上面两条明确不做的（自动任务、登录态爬取），
其余全部有对应实现，没有第三个缺口。


---

## 16. 服务端与前端（2026-08-31）

### 16.1 模块更名

`greenfinger-spring-boot-starter` → `greenfinger-api`，包名 `com.github.greenfinger.starter` → `com.github.greenfinger.api`。同时从"只能被嵌入的 starter"变成**可独立运行的 web 应用**：多了 `GreenfingerApiMain`、`spring-boot-maven-plugin` 打包、`deploy/greenfinger-api.sh`。

`@EnableGreenfingerServer` 保留且仍是**显式启用**，不走 `META-INF` 自动装配 —— 别人把这个 jar 嵌进自己的应用时，接口何时出现由他决定。

### 16.2 登录：为什么是不透明 token 而不是别的

要求是"security 版的 login / logout，用户名预分配、写死配置"。三种做法：

| 方案 | 问题 |
|---|---|
| HTTP Basic（1.x 的做法） | 没有真正的 logout。1.x 的 `/logout` 是个空方法 |
| Session + Cookie | 单页应用要处理 CSRF token、跨域带 cookie，为一个内网管理台引入两类容易配错的东西 |
| **签发不透明 token，服务端内存持有** | logout 就是从 map 里删掉，立即失效；无 cookie 即无 CSRF |

选第三种，但 token 是**签名的、无状态的**，不是服务端内存里的一张表。

token 里带着"是谁、能做什么、什么时候过期"，然后用 `GF_TOKEN_SECRET` 签名；任何一个节点重算一遍签名就能验，不需要它签发过这个 token。这是前面放 nginx / kong 把请求分给多个节点的前提 —— 最初的实现是每节点一个 `ConcurrentHashMap`，于是登录之后的下一个请求被分到第二个节点，回来的是 `Sign in first`，而这个问题在代理上无解，因为状态放错了地方。

没配 `GF_TOKEN_SECRET` 时每个进程自己生成一个并在日志里说明：单节点照常用，重启后失效，别的节点也不认。

**一次登录一个 token**（不是一个账号一个），所以在 A 浏览器登出不会把 B 浏览器一起踢掉。签名的 token 没法"撤销"，所以登出记在本节点的一个小集合里直到它自然过期 —— 这一节点内有效，对等节点上不生效；正确的解法是 token 有效期短，而不是再搞一张共享黑名单，那等于把状态又放回去了。

### 16.3 两个角色

| 角色 | 账号 | 能做什么 |
|---|---|---|
| `ADMIN` | admin / admin123 | 建 catalog，跑 crawl / update / rebuild / replay / delete |
| `SUPPORT` | tester / tester123 | 只读，改不了任何东西 |

规则只有一条：**GET 放行给两者，其余需要 ADMIN**。写在 `SecurityFilterChain` 里，不散落在注解上。前端也按 `isAdmin()` 藏掉按钮，但那只是体面 —— 真正拦住的是服务端。

账号来自 `.env` 的 `GF_USERS`，格式 `username:password:role`，逗号分隔。之所以是一行字符串而不是 yaml 列表：环境变量表达不了列表，而本项目所有配置都要能从 `.env` 来。密码 `{noop}` 存放 —— 它本来就是明文来的，在这里再哈希一遍只是掩耳盗铃，真正该保密的是那个文件，所以它在 `.env` 里、不进仓库。

`GET /v2/version` 不需要登录：登录页要显示它，而"是哪个版本拒绝了你的密码"正是发现自己连错服务器的方式。

### 16.4 静态页托管与深链接

`SinglePageAppConfiguration` 把不是文件、也不是 api 的路径都回落到 `index.html`。这不是可选的：`/catalogs` 是浏览器路由而不是磁盘上的文件，没有回落的话刷新一次就 404。

反过来同样重要：**api 前缀下不存在的路径必须仍然是 404**，不能回落成页面 —— 否则一个拼错的 url 会返回一段调用方解析不了的 html。为此 `NoResourceFoundException` 单独处理，它不继承 `ErrorResponseException`，会掉进兜底 handler 变成 500。

静态目录同时找 `classpath:/static/` 和 `file:./static/`，所以换前端不用重新打 jar —— 和配置放在 `deploy/config` 是同一个理由。

### 16.4.1 图片接口

图片是爬下来存好的，但 `imageFilePath` 是 blob store 路径而不是 URL，浏览器取不到 ——
这就是图片搜索一直没东西可显示的原因。

`GET /v2/image?path=` 从 BlobStore 读字节吐出去。**展示归档的那份而不是链回原始地址**，
因为归档本来就是为这个：站点改版、删图、防盗链、https 页面里的 http 图片，都影响不到它，
也不会把看结果的人暴露给每一个被爬过的站点。原始地址另外带在 payload 的 `imageUrl` 里，
做"看原图"的链接（新字段，旧数据需 `replay --layers vector`）。

两个安全要点：路径由调用方给，所以**按 layout 的形状白名单校验**（含扩展名），不是扫 `..`；
字节来自别人的网站却从我们的 origin 提供，所以固定加 `nosniff` 与
`Content-Security-Policy: default-src 'none'; sandbox`。

### 16.5 前端

Angular 21 + signals + RxJS + Angular Material + Tailwind，绿白为主。

- **登录页在前端**，服务端只出 `/login`、`/logout`、`/me` 三个接口。
- **token 存 localStorage**，但**每次启动都拿它去问 `/me`**，由服务端说了算 —— token 可能已被别处登出或服务重启作废，信 localStorage 会渲染出一个"看起来已登录、但每个请求都失败"的壳。
- **401 的处理只有一处**（拦截器）：清 session、跳登录页。**403 不动 session** —— 那是"你只能读"这个答案，不是坏掉的会话。
- **列表页轮询 `/crawl/status`**，且只在真的有任务在跑时轮询；停了就停。爬取以秒为单位报数、常跑几小时，三秒轮询一个小接口比 socket 省掉一整类重连处理。
- **删除版本只在 Monitor 页**，且强制"先 dry run 出报告、再确认"。列表页是快速操作的地方，不可逆的操作要放在得专门点进去的页面上。
- **搜索三种模式**（词 / 语义 / 图片）是三个不同存储上的不同查询，做成一个搜索框加模式切换，让它成为操作者能做的选择，而不是藏在后端的决定。ES 的分页走 cursor，绕开一万条上限。
- **版本号问服务端要**（`/v2/version`），不写死在页面里，所以徽章不会比它标注的构建活得更久。
- **logo**：给的是白底无透明通道的 3:1 字标，`tools/make-icons.mjs` 把近白像素抠成透明（带软边保留字形抗锯齿），并按"第一个空列"自动切出左侧那枚带叶子的 G 作为 favicon —— 按比例猜会把叶子切掉或把 g 带进来。
- **暗色主题**（2026-09-01）：`color-scheme: light dark` 加 `html[data-theme]` 两条覆盖。`mat.theme()` 生成的 token 本来就是 `light-dark()` 对，所以整个切换就是一个属性，`ThemeService` 里没有任何颜色值。三档 system / light / dark，默认 system —— 跟随操作系统是大多数人已经做过的选择。选 system 时**要删掉属性而不是设成某个值**，设成任何值都会让浏览器不再跟随系统。存 localStorage 不存账号：主题是屏幕的属性，不是人的属性。
- **向量检索翻页用 offset**（2026-09-01）：ES 有稳定排序键所以能用 cursor，向量检索的顺序是"到这条查询的距离"，只对这一次查询存在，没有可携带的东西。因此也设上限（服务端 1000）—— offset 越深向量库走得越久。文本检索的 offset **不下推**到向量库，因为重排和"一页一条"发生在应答之后，下推会跳过本来就要被去掉的行。
- **e2e**（2026-09-01）：Playwright 6 个用例，登录 → 建 catalog → Crawl → Monitor → 删除这条主链路真跑。配置里故意没有 `webServer` —— 从测试配置启动 jar，等于让它也管数据库、爬取目录和端口，那三样出问题都会被报告成"测试失败"。

### 16.6 以文搜图的能力边界（2026-09-01 实测）

用 books.toscrape 的 548 张封面做了可判定的评测。**用书名搜自己的封面**：recall@1 = 4%、
MRR 0.084，而且总返回同几张图。

第一反应是两个塔没对齐，于是加了 `CrossModalAlignmentIT`（纯红图 / 纯蓝图 + "a solid red square"）：
**对齐是好的**，红查询选红图、蓝查询选蓝图、同图两次编码余弦 1.0。

所以低 recall 不是 bug，是任务不成立：封面是 103×155 的缩略图，缩到 224×224 之后上面的字不可读；
而所有封面在模型眼里都是"一本书的封面"，彼此差异极小，分数全挤在 0.13–0.15。

换成**描述画面**就完全不同（用 `sips` 把返回图缩成 1×1 读平均色，客观判定）：

| 查询 | top-5 命中 |
|---|---|
| a red book cover | 4/5 偏红 |
| a green book cover | 5/5 偏绿 |
| a blue book cover | 4/5 偏蓝 |
| a black and white photograph on the cover | 5/5 灰度 |

**结论**：这个功能是"按画面内容找图"，不是"按图上的文字找图"。后者需要 OCR 能力，
而爬下来的缩略图分辨率不够。README 和搜索页的提示语按这个写。

---

## 17. 分布式（2026-09-02）

### 17.1 模型：fork 而不 join

一次爬取本质上是个递归函数：

```
handle(url) {
    html = fetch(url)
    save(html)
    for (link : links(html)) handle(link)     ← 这一行跨进程，就成了分布式
}
```

2.0 单机版把这个递归展开成了一个循环（`CrawlerEngine.run` 里 `frontier.poll()`），
分布式要做的只是把递归调用那一跳送出去。**没有 join**：父页面不关心子页面抓到了什么，
所以不需要等待、不需要合并、也没有返回值。

这一点和 openspreader 的 `RecursiveTask` / `ProcessingPool` 是有意区分的。那套是请求-应答：
`fork()` 发出去、`join()` 阻塞等结果。对爬虫有四个硬冲突——深度无界（`maxDepth` 默认 3）、
`join()` 每层占一个线程且不触发工作窃取、远端失败会**退回本地重算**（而爬一个页面是写库写文件写索引，
重算就是重复写）、以及根本不需要返回值。所以主链路用 `unicast` 一发了之。

请求-应答真正合适的地方是 **replay**：有界、一次性 fan-out、幂等、需要知道全部完成。
见 17.10。

### 17.2 一个进程就是一个成员数为 1 的集群

**没有单机形态**。`CrawlCoordinator` 是 core 里的接缝，只有两个方法——
"下一跳去哪"和"爬完了没有"——`LocalCrawlCoordinator` 把它们答成"写本地 frontier"和"是"，
而那正是集群成员只有自己时退化成的样子。所以代码只有一份，单进程行为和加集群之前一模一样。

`unicast(..., includeSelf=true)` 让本节点也参与轮询，被选中时 spreader 走本地派发，
不序列化、不走网络。

### 17.3 五个通道，各自的形状不同

| 通道 | 方式 | 缓冲 | 消费线程 |
|---|---|---|---|
| `greenfinger.crawl` | unicast **按 URL 一致性哈希** | 有 | 多条（URL 之间无序） |
| `greenfinger.record` | multicast 排除自己 | 有 | **1 条**（同一行的两次更新有先后） |
| `greenfinger.rocksdb` | multicast 排除自己 | 有 | 1 条 |
| `greenfinger.blob` | multicast 排除自己 | 有 | 1 条 |
| `greenfinger.control` | multicast 含自己 | **无**（`shouldBuffer=false`） | — |

控制通道不缓冲是有意的：stop 就是要立刻生效，把它排在正在制造这波流量的一万个 URL 后面，
是缓冲唯一得不偿失的地方。

所有监听器都实现 `SelfRegisteringListener`。不实现的话，自动注册会把它们**同时挂到默认通道**上，
于是每条消息投递两次——表现不是报错，而是每个页面被抓两遍。

### 17.4 完成判定：每个节点自己判，共享状态保证只生效一次（2026-09-04 重写）

**只有两个 CompletionChecker**：`maxFetchSize` 和 `fetchDuration`。它们决定
`GlobalStateManager.isCompleted()`，而 `isCompleted()` 决定要不要 `publishSearchVersion`。
没有第三种判定。

checker 是被动的：给它 dashboard，它回答一个关于 dashboard 的问题。dashboard 在集群里就是共享
缓存，所以**判定在哪个节点上发生完全无所谓** —— 谁先撞到谁写，其余节点下一跳读到同一个答案。
这是 1.x 的做法（那边是 Redis），2.0 一度把它收成"leader 判、然后广播回来"，那是个回归：

- leader 只要不在这次爬取里（join 失败、在爬别的 catalog、或中途换了 leader），
  全集群就没人宣告结束，引擎一直等。
- 引擎为了区分"我自己走到头"和"别人让我停"发明了本地的 `endedItself`，
  而共享的 completed 标志会被最先退出的那个节点翻上去，其余节点于是误判成非正常结束
  —— `rebuild` 不推进 `search_version` 就是这么来的。

两者在 2026-09-04 一起拆掉：`CrawlCluster.supervise()`、控制通道的 `STOP` / `FINISHED`、
`CrawlCoordinator.isCrawlFinished()` 全部删除。

**两个 checker 一个在运行时问、一个由时钟问。** `maxFetchSize` 数的是页面，页面只会因为抓了一页
而变多，所以顺路问一句就能在到达限额的那一页刹住；`fetchDuration` 数的是时间，时间不管有没有在
抓都在走，而"所有线程都卡在一个不响应的站点上"恰恰是这条限额存在的理由 —— 那种时候顺路的机会
不会再来。时钟 5 秒一跳（`greenfinger.completion-check-interval`），和 1.x 的
`SerializableTaskTimer(5, 5, SECONDS)` 一样。

**代价**：比 `maxFetchSize` 小的站点，抓完之后要挂满 `idle-timeout` 才结束 —— 别的没东西能结束它。
引擎不再"自己的 frontier 空了就退出"，也不该退：一个节点的 frontier 空了，说明不了另外三个节点的事。
两个超时都是 `Duration`，可以往两边调；默认 2 分钟（1.x 是 5 分钟）。

**看门狗：安静有两种意思。** 计数器（`isTimeout`，`greenfinger.idle-timeout`，默认 2 分钟没动）
停住时看两个 URL 计数器：

| 情况 | 含义 | 结果 |
|---|---|---|
| `handled >= dispatched` | 没有任何 URL 还欠着，站点抓完了 | **正常完成**，不等 duration 到点，发布 |
| `handled < dispatched` | 有节点拿着 URL 不响应了，那些页永远不会来 | **非法中断**，不发布，frontier 留着等 resume |
| `dispatched == 0` | 入口都没派发出去 | **非法中断**，不发布 |

不是相等而是"没欠着"（2026-09-04 修正）：投递是至少一次，同一条消息到达两次就会被结算两次，
handled 因此可能略大于 dispatched。要求严格相等的话，一次抓得干干净净的爬取会永远差那么一两个，
最后被判成 stalled 而不发布索引。handled 只会多不会少，所以"没欠着"才是要问的问题。

这不是第三个 checker —— 它不写 completed 的判定条件，只是在超时发生时用来区分这两种安静。

**为什么派发计数必须在父页面 handled 之前加**：否则观察者会看到"页面处理完了、它发现的 URL 还没
被计入"的瞬间，看门狗就会把一次正常的爬取当成抓完了。

**reason 是共享的。** `setCompleted(completed, reason, interrupted)`，先写者赢：一次撞到
`maxFetchSize` 之后又被收尾的爬取，报告里写的是限额而不是收尾。三份节点报告因此说同一句话。

**每次任务开始重置。** `ClusterDashboard` 的键在共享缓存里活得比进程久（TTL 一天），所以同一个
catalog 同一个 version 再爬一次会继承上次的计数和 `completed=1` —— 那次爬取会在开始之前就结束。
发起节点在 `afterPropertiesSet` 里清空全部计数、flag 和 reason；join 的节点不清，否则会把正在
累加的数字清零。1.x 的 `RedisDashboard.afterPropertiesSet()` 就是这么做的，`initialized` 标志
区分发起方和加入方，2.0 一开始漏了这一半。

### 17.4.1 同一个 URL 只抓一次（2026-09-04）

一次爬完的报告写着"6 dispatched, 10 handled"：十次抓取里有四次是同一批页面的第二遍，抓了、
解析了、算了指纹，最后在写库时被唯一约束挡下来。投递是至少一次，这是消息系统的正常保证，
不是故障；缺的是针对它的应对。三处改动，缺一不可：

**一、按 URL 路由。** `unicastOn` 的第三个参数是路由键、第六个是负载均衡器，两个都传了 null，
于是走集群默认的轮询 —— 同一个 URL 每次派发落到计数器当时指向的任何一个节点。1.x 不是这样的：
它发出的每个 packet 都带 `partitioner=hash`，键是 `catalogId,refer,path,version`。现在键是
`catalogId|version|url`（**故意不含 refer**：同一个 URL 从两个不同页面被发现，正是要合并到一处的
那种情况），均衡器用 `consistentHash` 而不是 1.x 的朴素取模 —— 中途加一个节点只搬动约 1/N 的
URL，而不是几乎全部。

**二、frontier 一个 URL 只入队一次。** `RocksDbCrawlFrontier` 在队列的 `f:` 之外多了一个 `u:`
键空间，`put` 拒绝已经排过队的 URL。放在 frontier 而不是通道里，因为所有入队路径都在这里交汇：
对端发来的、派发器没送出去而本地留下的、sitemap 播的种、上次运行恢复的。store 本来就按 catalog
和 version 分目录，所以代价是每个 URL 一次本地写、内存零开销。

`u:` 记的是"**这次运行**排过队没有"，不是"这个版本见过没有" —— 每次启动清空，再用队列里恢复出来
的任务重建。否则 `refresh` 会被全部拒绝：它写的是同一个 version、开的是同一个 store，而它要重抓的
正是上一轮抓过的那些页。

（现有的 `ExistingUrlPathFilter` 回答不了这个问题，跟它存在磁盘还是内存无关：它是在**派发之前**
置位的，等重复消息到达时，它对重复消息和对原消息给的是同一个答案。）

**三、每次派发恰好结算一次。** 这是前两条会打破的东西。两个节点可能在复制窗口内同时发现同一个
链接，各自派发一次；路由到一起之后第二份被 frontier 拒绝，而**没有任何人会报告它 handled** ——
于是 handled 永远比 dispatched 少一个，最后被判 stalled、什么都不发布。所以 `CrawlFrontier.put`
现在返回是否入队，三个入队点（本地协调器、集群协调器、消息通道）在被拒时替它结算。

三节点、61 页站点、共享一个 PostgreSQL 的实测：

```
  dispatched : 61                   handled : 66      saved : 61
  node-1: 18 抓取   node-2: 18   node-3: 25            -- 61，每页恰好一次
```

同样的东西早一次跑出过 161 dispatched —— 三个节点在各自的 dedup 复制到达之前都发现了同一个链接，
各派发一次。这跟时序有关，会变；不变的是 `saved`，两次都是 61。那多出来的一百次派发是被 frontier
拦在抓取之前的，也就是一百次没有发生的抓取。


### 17.4.2 爬完之后广播一次（2026-09-04）

1.x 有 `WebCrawlerCompletionEvent`，2.0 一开始没做，理由写在 `ControlMessage` 的注释里：完成
标志和 reason 已经在共享缓存里，任何节点随时读得到，再广播一遍是同一个事实的第二份副本。

那回答的是另一个问题。共享计数器持有的是**状态**，现在依然是，这里不复制它。它给不了的是一个
**时刻**：应用想在爬完的那一刻做点什么，只能去轮询一个标志 —— 迟到最多一个间隔，而且没有地方
挂钩子。

于是 `COMPLETED` 跟着 `STARTED` 走同一条控制通道，形状也跟着它：

- 收尾的那个节点**广播一次**。广播会回到发送者自己，所以每个节点各广播一次的话，每个节点会收到
  N 份。
- 每个节点 —— 含发送者，含完全没参与这次爬取的 —— 本地发一个 `WebCrawlerCompletionEvent`，
  **每节点恰好一次**，这才让 `@EventListener` 的含义和读者预期一致。
- 单进程没有集群可广播，`announceCompleted` 返回 false，由 launcher 自己发。一个节点不是退化的
  集群，是"没有东西要发、也没有对象可发"。

它是通知不是决策：发送时机在版本发布、存储关闭、许可释放之后，爬虫不等任何监听器，监听器抛异常
只记日志 —— 别人的通知失败不等于爬取失败。事件带 catalogId、version、reason（与 dashboard 同一
句话）、interrupted。


### 17.5 复制：每个节点一份全量

`StoreType(name, replicated)` 从 `spring.datasource.url` 和 blob target 判定：

```
SQLITE(true)  H2(true)  H2_SERVER(false)  MYSQL(false)
POSTGRESQL(false)  SQLSERVER(false)  ORACLE(false)  OTHER(false)
LOCAL_FILE(true)  MINIO(false)  ROCKSDB(true)
```

H2 是两种都可能的：`jdbc:h2:file:` 要复制，`jdbc:h2:tcp://` 不要，两者差四个字符——
所以判定读 URL 而不是读产品名。

复制的是**操作**不是快照，逐条多播、排除自己、攒批成帧。**投递是至少一次**，
所以每种应用都必须幂等：blob 先 `exists` 再写（路径由内容派生，同路径必定同字节）、
image 行按 id 存在与否、resource 行比较内容（refresh 会真的改一行，"存在就跳过"会让别的节点停在旧内容上）。

`catalog` 表也必须复制，而且比 resource 更要紧：**没听说过某个 catalog 的节点无法打开它那一半爬取**，
分发过去的 URL 就无处可去。这是三节点第一次跑起来时唯一的故障，而且它不报错——
计数器只是停在"派发 1、处理 0"，你愿意看多久它就停多久。

frontier **不复制**：它是本节点的工作队列，复制了就变成每个节点都爬全站。

### 17.6 序列化：为什么是 JSON 而不是 Kryo

openspreader 自带 Kryo 编解码（`spring.spreader.multiprocessing.serialization=KRYO`），
实测比 JDK 序列化快 2.2 倍。爬虫这边没有用它，理由是量出来的：

```
CrawlTask: json 327 字节，编码+解码 1394 ns（JDK 序列化要 427 字节）
```

一个 URL 三百来字节。即便到每秒一千个 URL——没有哪个讲礼貌的爬虫能到——
这也只是每秒 1.4 毫秒的 CPU，0.14%。快一倍等于省下这个数的一半。
而爬取的瓶颈从来是抓页面，不是编码它的地址。JSON 的字节数本来也比 JDK 序列化小。

**真正决定的是另一件事**：这是节点之间的线格式，而节点是一台一台升级的。
升级窗口里，跑新版本的节点会给跑旧版本的节点发任务。JSON 忽略不认识的字段，
新加一个字段不影响老节点；按字段位置编码的格式做不到，而接收侧对读不出来的报文是**丢弃**的——
症状会是升级期间页面悄悄变少，别处什么都看不到。`CrawlTask` 上的
`@JsonIgnoreProperties(ignoreUnknown = true)` 就是为这个加的（2026-09-02，之前没有）。

Kryo 唯一真正划算的场景是"缓存里放大对象"，而这里放的只有计数器。图片字节走的是
`ReplicationBatch` 的二进制帧，本来就不经过任何对象编解码——JSON 会让每张图涨三分之一。

### 17.7 计数器：读免费，写要走 leader

openspreader 的 cache 是"每个进程一份全量副本"：**读不出进程**（实测千万级 QPS），
写是到 leader 的一次往返（低千级 QPS，还是全组件共享的）。

所以 dashboard 的读随便读，写必须攒批：本地累加，每 500ms 一次 `incr(key, delta)`。
不管爬多快，每节点每秒就那么几次写。代价是 dashboard 落后半秒——它是个 dashboard。

`getHandledUrlCount()` 是 2.0 新加的计数类型。同时按节点也记一份
（`hincrby(key + ":by-node", nodeId, delta)`），因为"这次爬取存了 44 页"事后能从数据库数出来，
而"其中一个节点存了 40 页因为另外两个一直连不上站点"只有这里说得出来。

### 17.8 一次一个爬取

`WebCrawlerSemaphore` 同时问两个问题：进程内的许可（"这个进程在爬吗"）和 catalog 表
（"别的节点在爬别的吗"）。表是全集群本来就同意的地方——每个节点都写自己的 running state，
每个节点都能读——所以不需要锁服务、不需要 leader、没有东西要保活。
**同一个 catalog 在跑不算拒绝**，那是节点在加入它。

这是检查不是锁：两个节点同一瞬间发命令可能都通过。代价是两个不同 catalog 分掉带宽，
而这正是这条规则想劝阻而不是想杜绝的事。

`POST /v2/crawl/{ref}` 是异步的，所以同一条规则在 controller 里再问一次（不取许可），
否则调用方会拿到 "started" 然后在后台被拒、什么也不知道。

**删除目录必须先停掉它的爬取**，这一条是踩出来的。爬取从 frontier 取下一个 url，不看 catalog
表，所以把行删掉不会让它停：它继续抓一个已经不存在的目录，同时从 running 列表里消失了（列它的
那行没了），而它占着这个进程唯一的许可**永远不还**——之后这个节点上的任何爬取，都会被一个谁也
看不见、叫不出名字的爬取拒绝。现场证据是一个跑了一个半小时、37% CPU、在抓一个删掉的目录的进程。
`CatalogAdminService.delete` 现在先 `crawlRegistry.interrupt(id)` 再删行。

### 17.9 集群名就是隔离边界

一次集群 = "同名 + 同端口的所有人"，`spring.spreader.name` 和 `spring.spreader.port`
就是全部的成员规则。**一台机器上跑两套 greenfinger，默认配置下会合并成一个集群**，
而它们发现这件事的方式是互相复制对方的写入——包括对方的删除。

2026-09-02 的回归里真的发生了：`run-local.sh` 的三个节点、上一轮遗留还在跑的 docker 容器、
以及 8088 上的单节点合并了，e2e 清理时的删除跨环境传过去，所有 catalog 在所有节点上消失。

所以 `run-local.sh`（`greenfinger-local`）、`run-docker.sh`（`greenfinger-docker`）
和 plain 启动器（默认）各用各的名字，真实部署也应当自己命名。

### 17.10 replay：唯一的 scatter-gather

爬取是 fork 无 join，replay 反过来——它是这个系统里唯一需要"分头做完再合并"的操作，所以它走
`ProcessingPool` 而不是 gossip 通道。

`ReplayService` 拆出 `replaySlice(catalogId, version, layers, offset, limit)`（单机就是
offset 0、limit 无穷），`ClusterReplayService` 覆盖 `replay`：切片、`pool.submit` 出去、
逐个 join。跨网络的只有 catalog id、version、层名、offset、limit 五个值——按 bean 名和方法名
调用，接收节点解析自己的 bean，一个对象都不传。

**分片大小卡在两个边界之间**，两个边界针对的是两个不同的失败：

```
size = clamp(总数 / (节点数 × 3), 50, 200)
```

上界（200）是**超时**：一片是一次方法调用，池等待的时间有界，所以一片必须能在这个等待里跑完，
**不管目录有多大**。按节点数切正好相反——目录越大每片越大，10 万页会切出 1.1 万页一片，每片都超时。
下界（50）是**值不值一个来回**。中间按节点数铺开，否则 176 页只会切出一片发给一个节点，
正确但另外两个节点闲着。

**超时设成 120 秒**（`pooling.request-timeout-ms`，默认 10 秒）。默认值是给快速返回的调用用的，
一片 200 页的 embedding 是几十秒。而且递归任务超时会退回本地重算，**方法调用超时是直接抛异常**，
所以这个值太小不是"慢一点"，是"整个 replay 失败"。

**丢片就在本地做**。哪一片没回来不构成失败，原地重做同一个区间即可——这条安全的前提正是 replay
幂等：下游每个 id 都是自然键的 name-based UUID，远端真做完了的话本地这遍是盖上去而不是并排新增。

**收益在多机上**。本机三节点跑 176 页，64 秒，和单节点的 60 秒几乎一样——embedding 是 CPU 密集的，
同一台机器上分片再多也是在抢同一批核。

### 17.11 文件层：不是从数据库重建，是按 URL 重取

其他层从数据库重建，因为数据库里有它们要的一切。文件层不行——数据库里有元数据和文件路径，
没有网页正文和图片字节。但它有 `resource.url` 和 `resource_image.source_url`，**这就够了**：
文件不是无从还原，是要重新去取。

`FileRestorer` 是**窄义上的爬取、广义上不是**：会发请求，但不发现任何东西——不跟链接、不写行、
不访问任何不在表里的 url。URL 集合在第一个请求之前就固定了，这才让它是修复而不是再爬一遍。

- **顺序是硬的：文件在前，index/vector 在后**。后两者的正文从 blob store 读回来，文件没回来
  就重建索引，会静默索引出一堆只有标题的空页（缺文件读作空，不读作错）。
- **文件齐全的页直接跳过，一个请求不发**，所以可以放心跑第二遍。
- 礼貌间隔白拿：用的是 catalog 自己的 extractor，`fetchInterval` 照常生效。
- 图片按**行里记的路径**写回，不是按取回的字节重算路径——那个路径是别的层引用它的方式。
- 取回来的是**今天的站点**：页面变了就跟旧索引对不上，页面下线了就恢复不了。两者都计数上报
  （`unreachable` / `changed`），静默留洞比失败更糟。

**这一层不能切片分发**，和 17.10 正相反。文件是每个节点一份全量，"缺什么"在每个节点上是不同的
答案。一个拿到分片的节点检查的是**自己**的文件（完好），于是报告无事可做，而真正丢了文件的节点
根本没被问到。所以走 control 通道多播 `RESTORE_FILES`，**每个节点修自己那份**；发起的节点已经
先修过自己，靠消息里带的 origin 跳过回声。完好的节点一个请求都不发，这才让"问所有人"是便宜的。

实测（本机 3 节点）：删掉 **node-2** 的 40 个 html + 40 个 txt + 100 张图，把请求发给
**node-1**（完好的那个）。node-1 检查 176 页全在，0 请求，立即返回；node-2 后台自修，150 秒后
180 个文件全部回来，与 node-1 的副本逐字节一致，它自己的报告是
`40 page(s) and 100 image(s) written, 94 already there, 0 unreachable, 0 changed`。

**一个必须一起改的坑**：`OutputType.parse` 会把 FILE 无条件加进结果（爬取时"文件层不可关闭"
是对的）。照旧用它，`replay --layers index` 会连带重新抓全站。所以新增 `parseExact`，replay
路径改用它——文件层只有被显式点名才会动。

### 17.12 已知行为

- **一个 URL 可能被投递两次**（至少一次投递）。代价是一次多余的抓取：下游所有 id 都由 URL 派生，
  第二遍原样盖住第一遍。防不了——最直白的防法是在接收侧查 URL 去重库，
  但 refresh 本来就要绕开那个库，那样会让 refresh 什么都抓不到。
- **集群刚成型的几秒内可能有两个节点都认为自己是 leader**，于是版本被发布两次。
  两次写的是同一个版本号，prune 也幂等，所以无害。等选举收敛后不会再发生 ——
  发布放在 leader 上本来就不是为了正确性，只是为了不让三个节点做同一件事三遍。
- **每个节点写自己的报告**，内容是全量的（含按节点分解），但快照时刻不同，
  所以同一次爬取的三份报告里 `savedImageCount` 这类还在变化的数字可能相差几个。
  `savedResourceCount` 这种已经停下的数字是一致的。


---

## 18. 发布前的一轮（2026-09-04）

### 18.1 端到端回归第一次跑通，代价是十二个 bug

在此之前从没有一次"起节点 → 建 catalog → 走 api 爬 → 自行结束 → 搜索 → 清空"完整跑通过。
让它跑通的过程里发现的（按严重程度）：

1. **自动生成的 pathPattern 丢了端口**，于是任何不在 80/443 上的站点，入口页发现的链接全被拒绝，
   爬一页就停 —— 而拒绝只计数不打日志，现场什么都看不到。
2. **`ReadonlyDashboard` 没复制 completionReason 和 interrupted**（完成判定新加的两个字段），
   于是爬完之后 Monitor 页永远显示"无原因"。
3. **summary 的版本号取自爬取开始时的 catalog 快照**，`searchVersion` 永远落后一轮，前端轮询
   "已发布"会一直轮下去。
4. **完成判定要求 `dispatched == handled`**，而至少一次投递让 handled 会多 —— 一次干净跑完的
   爬取被判 stalled，索引不发布。
5. **重复投递撞唯一约束被当成失败**，满屏堆栈，failures 虚高。
6. **清空 catalog 被"不能删正在服务的版本"挡住** —— 那道守卫对"按版本删"是对的，对"清空整个
   catalog"是错的：全删正是请求本身。
7. **前端两个删除选项都发错参数**：都发 `keepLatest: 0`（api 读作"逐个点名所有版本"，撞上第 6 条），
   而 `purge` 由"保留几个"而不是由按下哪个按钮决定。
8. **服务端日志默认 WARN**，一个没人看着的进程什么都不留。
9. **SQLite 写锁冲突丢页**：`CannotAcquireLockException` 被当成失败，四线程六页丢两页。
   Spring 管这类异常叫 transient，该做的是重试。
10. **ES 向量索引的 mapping 少了 `properties` 层**，整个索引从没建成过；失败被优雅吞掉，所以看不见。
11. **爬取宣告结束早于收尾**：许可先释放、版本后发布，中间那段时间"删除"会被拒为"正在爬取"。
12. **`run-docker.sh start` 从来跑不通**：`IMAGE` / `NETWORK` / `MEMORY` 三个变量只用不赋值，
    而脚本跑在 `set -u` 下，第一句实际工作就死。

另外两个不算 bug 但同样只有真跑才会发现：容器每次启动都从 huggingface 重下 embedding 模型
（`GF_MODEL_DIR` 不在 volume 里）；Weaviate 的 `baseUrl` 已含 `/v1`，有一处又加了一次。

### 18.2 回归矩阵分两级

**主路径**（产品自己的路）：本地 fs / MinIO × H2 / SQLite / MySQL / PostgreSQL × Lucene / ES。
七档，每个选择至少出现两次、每次搭不同的邻居 —— 只跟一个邻居配过的存储，等于只测了一半。

**次要路径**（部署方可能有、多数不会有）：SQL Server、Oracle、Qdrant、Weaviate。服务没起时
**跳过并报成 skip**，不是 pass。

外加三节点共享一个 PostgreSQL、三容器跨网络、以及"彻底删掉"（catalog 行也没了）三个单独的跑法。

一个教训值得记：`GF_PROFILE=prod` 的默认是 MinIO + ES，所以只设了数据库的那几档，实际测的是
它们不打算测的组合，而表格上写着别的。每档现在显式写死三个存储选择。

### 18.3 BloomUrlPathFilter 写了又删

写完、测完，同日按要求删除：URL 去重只保留 RocksDB 那个。代码没问题，取舍有问题 —— 布隆过滤器
回答"见过"是概率而不是事实，大约每一百页跳过一页，静悄悄地，没有任何地方说得出是哪些页。
对一亿 URL 的爬取这个价钱合理，对一个要给人搜的 catalog 不合理。

**扩展点保留**：`ExistingUrlPathFilter` 仍是接口，catalog 仍然记着自己用哪个过滤器，
`WebCrawlerComponentFactory` 仍是 `@ConditionalOnMissingBean` —— 想换一种去重的应用换掉工厂即可。
face 端的那一问因此不再拿内置名单校验答案：名字合不合法只有被问到的工厂知道，它会在爬取开始时拒绝。

