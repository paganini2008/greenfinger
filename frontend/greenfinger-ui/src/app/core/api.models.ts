/**
 * The shapes the api returns. Hand written against the controllers; field names match the Java,
 * and enums that serialise as something other than their name are spelled out.
 */

/** Every endpoint answers in this envelope, success and failure alike. */
export interface ApiResult<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface Session {
  token: string;
  username: string;
  /** Spring's own form, so 'ROLE_ADMIN' rather than 'ADMIN'. */
  roles: string[];
  expiresInSeconds: number;
}

export type OutputType = 'file' | 'index' | 'vector';
export type ContentMode = 'text' | 'text+image';

/** One picture per output, so a card says where its pages went without a line of text. */
export const OUTPUT_MARKS: Record<OutputType, { icon: string; label: string; hint: string }> = {
  file: { icon: 'folder', label: 'Files', hint: 'Files -- pages and images kept on disk or in MinIO' },
  index: { icon: 'manage_search', label: 'Index', hint: 'Index -- searchable by words' },
  vector: { icon: 'scatter_plot', label: 'Vectors', hint: 'Vectors -- searchable by meaning' },
};

export type RunningState = 'none' | 'crawl' | 'update' | 'rebuild';
export type DeleteLayer = 'db' | 'file' | 'index' | 'vector' | 'all';

/**
 * Which counter `maxFetchSize` is compared against. It reads like "pages to keep" and only means
 * that under `Pages saved`; under `Urls seen` one listing page can spend the whole budget.
 * Serialised as the int, so the value is the wire form.
 */
export const COUNTING_TYPES = [
  {
    value: 4,
    label: 'Pages saved',
    hint: 'Pages actually stored. What most people mean by a limit, and the default.',
  },
  {
    value: 5,
    label: 'Pages indexed',
    hint: 'Pages that reached the search index. Lower than saved when indexing is off.',
  },
  {
    value: 10,
    label: 'Pages vectorised',
    hint: 'Pages that reached the vector store. Lower than saved when the vector output is off.',
  },
  {
    value: 6,
    label: 'Images saved',
    hint: 'Downloaded images. For a crawl that is really after pictures.',
  },
  {
    value: 0,
    label: 'Urls seen',
    hint: 'Links discovered, not pages kept. One listing page can blow the whole budget at once.',
  },
  {
    value: 2,
    label: 'Urls already known',
    hint: 'Links skipped because they had been queued before. Rarely a useful limit.',
  },
  {
    value: 3,
    label: 'Urls filtered out',
    hint: 'Links refused by the scope, depth or robots rules. Rarely a useful limit.',
  },
  {
    value: 1,
    label: 'Invalid urls',
    hint: 'Fetches that failed. Use it to stop a crawl that is mostly erroring.',
  },
  {
    value: 7,
    label: 'Duplicate pages',
    hint: 'Pages whose content had been seen under another url.',
  },
] as const;

export const EXTRACTORS = [
  { value: 'adaptive', label: 'Adaptive', hint: 'Plain http, and a browser only for pages that came back empty' },
  { value: 'restclient', label: 'Rest client', hint: 'Plain http, never a browser. Fastest, blind to javascript' },
  { value: 'htmlunit', label: 'HtmlUnit', hint: 'Renders javascript, nothing to install' },
  { value: 'playwright', label: 'Playwright', hint: 'Best rendering, needs playwright install' },
  { value: 'selenium', label: 'Selenium', hint: 'Best rendering, needs a browser on the machine' },
] as const;

/** A crawl task, exactly as the crawler_catalog row is serialised. */
export interface Catalog {
  id?: string;
  name?: string;
  url: string;
  startUrl?: string | null;
  sitemapUrl?: string | null;
  cat?: string;
  pathPattern?: string;
  excludedPathPattern?: string | null;
  pageEncoding?: string;
  maxFetchSize?: number;
  depth?: number;
  fetchInterval?: number;
  duration?: number;
  countingType?: number;
  maxRetryCount?: number;
  urlPathAcceptor?: string | null;
  urlPathFilter?: string;
  extractor?: string;
  runningState?: RunningState;
  outputTypes?: OutputType[];
  imageEnabled?: boolean;
  contentMode?: ContentMode;
  indexVersion?: number;
  searchVersion?: number;
  maxVersions?: number;
  lastIndexed?: string | null;
  /**
   * `updatedAt` is stamped by whichever store performed the write, including a copy arriving from
   * another node -- which is what lets the catalog list wait for a node to catch up with an edit.
   */
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Everything the runtime will use, with the defaults already applied. Read only. */
export interface CatalogDetails {
  id: string;
  name: string;
  url: string;
  startUrl: string;
  category: string;
  catalogVersion: string;
  version: number;
  searchVersion: number;
  pathPatterns: string[];
  excludedPathPatterns: string[];
  extractor: string;
  maxFetchSize: number;
  maxFetchDepth: number;
  fetchInterval: number;
  fetchDuration: number;
  maxRetryCount: number;
  maxVersions: number;
  countingType: number;
  contentMode: ContentMode;
  imageEnabled: boolean;
  outputTypes: OutputType[];
  catalog: Catalog;
}

/** The counters the Monitor page draws: live while a crawl runs, the last run's otherwise. */
export interface CatalogSummary {
  live: boolean;
  catalogId: string;
  catalogName: string;
  version: number;
  searchVersion: number;
  startTime: number;
  endTime: number;
  completed: boolean;
  totalUrlCount: number;

  /** Urls finished with. Below totalUrlCount by exactly what is still queued or in flight. */
  handledUrlCount: number;
  existingUrlCount: number;
  filteredUrlCount: number;
  invalidUrlCount: number;
  /**
   * Failed fetches in a row, reset by the first page that arrives: twenty in a row is a closed
   * door, where a hundred spread over a long crawl is ordinary.
   */
  consecutiveFailures: number;
  lastFailure: string;
  savedResourceCount: number;
  indexedResourceCount: number;
  /** Pages handed to the vector store. Counted apart from the index: either output can be off. */
  vectoredResourceCount: number;
  savedImageCount: number;
  duplicatedContentCount: number;
  /** Fetched, then dropped because a limit fired before they could be written. */
  abandonedUrlCount: number;
  remainingUrlCount: number;
  elapsedMillis: number;
  elapsedTime: string;
  progress: number;
  /** The pages limit and the time limit, separately: either one can be what ends the crawl. */
  sizeProgress: number;
  timeProgress: number;
  completionReason: string | null;
  /** True when the run was cut short rather than reaching a limit of its own. */
  interrupted: boolean;
}

/** One row of /crawl/status: the whole list in one call, which is what the list page polls. */
export interface CrawlStatus {
  id: string;
  name: string;
  running: boolean;
  runningState: RunningState;
  indexVersion: number;
  searchVersion: number;
  savedResourceCount?: number;
  savedImageCount?: number;
  totalUrlCount?: number;
  handledUrlCount?: number;
}

/** One row of what a crawl stored. Metadata only: carrying the text would read a file per row. */
export interface ResourceRow {
  id: string;
  catalogId: string;
  version: number;
  url: string;
  title: string | null;
  cat: string;
  depth: number | null;
  linkCount: number | null;
  textLength: number | null;
  referer: string | null;
  contentHash: string | null;
  /**
   * Where the page was written in the blob store, relative to its root. Null on a row whose files
   * were deleted while the row was kept -- a state the delete panel can produce on purpose.
   */
  htmlFilePath: string | null;
  htmlContentFilePath: string | null;
  /** What the server said about the page, kept so a re-crawl can ask "has this changed". */
  etag: string | null;
  httpLastModified: string | null;
  urlHash: string | null;
  createdAt: string;
  updatedAt: string | null;
  /** Carried with the row so the list can show a count without opening every one. */
  images: ResourceImageView[];
}

/**
 * One picture on one page. `sourceUrl` is what this page pointed at, `firstSourceUrl` where the
 * bytes were first found: an image is stored once per version however many pages carry it.
 */
export interface ResourceImageView {
  imageId: string;
  sourceUrl: string;
  firstSourceUrl: string | null;
  /** Where it lives in the blob store. Feed it to the image endpoint to see it. */
  filePath: string;
  contentType: string | null;
  width: number | null;
  height: number | null;
  bytes: number | null;
  altText: string | null;
}

export interface ResourcePage {
  results: ResourceRow[];
  total: number;
  totalPages: number;
  page: number;
  pageSize: number;
}

/** What one catalog is occupying in the blob store, across every version it still has. */
export interface CatalogUsage {
  catalogId: string;
  catalogName: string;
  pageCount: number;
  imageCount: number;
  bytes: number;
}

/** The blob store as a whole. `target` is what it calls itself: `local` or `minio`. */
export interface StorageUsage {
  target: string;
  pageCount: number;
  imageCount: number;
  bytes: number;
  catalogs: CatalogUsage[];
}

/** One of a catalog's three RocksDB stores: the frontier, and the two dedup filters. */
export interface RocksDbStoreUsage {
  name: string;
  path: string;
  exists: boolean;
  bytes: number;
  /** -1 when it could not be read, which is not the same as a store holding nothing. */
  keyCount: number;
}

export interface RocksDbUsage {
  catalogId: string;
  /** True when the key counts are missing because the crawl has the stores open. */
  crawlRunning: boolean;
  bytes: number;
  keyCount: number;
  stores: RocksDbStoreUsage[];
}

/** One of the things /actuator/health checks, flattened for a table. */
export interface HealthComponent {
  name: string;
  status: string;
  details: Record<string, unknown>;
}

export interface HealthReport {
  status: string;
  components: HealthComponent[];
}

export interface SearchResult {
  id: string;
  title: string;
  url: string;
  cat: string;
  catalog: string;
  version: number;
  createTime: string;
  score: number;
  highlights: string[];
}

export interface SearchResponse {
  results: SearchResult[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  elapsedMillis: number;
  /** Feed back as the next request's cursor to read past Elasticsearch's ten thousandth result. */
  nextCursor: unknown[] | null;
}

/** A hit from the vector store. Its fields live in a free-form payload, hence the index signature. */
export interface VectorHit {
  id: string;
  score: number;
  payload: Record<string, unknown>;
}

/** What the running server says it is. Read from the build, never from a constant in this app. */
/**
 * Which store is behind each output. Provider names -- `local`/`minio`, `lucene`/`elasticsearch`,
 * `qdrant`/`weaviate` -- which the Outputs cards turn into words rather than stating themselves.
 */
/**
 * One node's heap and cpu, read from the jvm rather than the actuator's metrics endpoint, which
 * production does not expose. Both are -1 where the platform will not say.
 */
export interface NodeReading {
  heapUsed: number;
  heapMax: number;
  cpu: number;
}

export interface OutputStores {
  file: string;
  index: string;
  vector: string;
}

export interface ServerVersion {
  name: string;
  version: string;
  builtAt?: string;
  /** The Spring profiles in force, so the page can say which installation this is. */
  profiles?: string[];
}

/** One line of a delete report: what would go on a dry run, or what did go on a real one. */
export interface DeleteLine {
  version: number;
  layer: DeleteLayer;
  count: number;
  bytes: number;
  error: string | null;
}

/**
 * One crawl, accounted for afterwards. Every node that took part writes a copy, and each carries
 * the whole picture: the totals, and what each node did towards them in `byNode`.
 */
export interface CrawlReport {
  path: string;
  node?: string;
  catalog: string;
  version: number;
  action: string;
  refresh: boolean;
  startTime: number;
  endTime: number;
  elapsedMillis: number;
  produced: {
    savedResourceCount: number;
    savedImageCount: number;
    indexedResourceCount: number;
    vectoredResourceCount?: number;
  };
  urls: {
    dispatched: number;
    handled: number;
    outstanding: number;
    alreadySeen: number;
    filtered: number;
    unreachable: number;
    duplicateContent: number;
    failures: number;
  };
  ending: {
    reason: string;
    fullyCrawled: boolean;
    published: boolean;
    remainingOnThisNode: number;
    note?: string;
  };
  nodes: string[];
  byNode?: Record<string, Record<string, number>>;
}

/**
 * One `@ConfigurationProperties` class of ours, as it ended up after the yaml, `.env` and the
 * command line had their say. Leaf keys, `minio.endpoint` rather than a tree, because that is how
 * the yaml names them and how somebody would search for one.
 */
export interface SettingsGroup {
  prefix: string;
  type: string;
  properties: Record<string, unknown>;
}

/** What `/actuator/settings` reports: greenfinger's own settings, secrets already masked. */
export interface SettingsReport {
  groups: SettingsGroup[];
}

/**
 * What `/actuator/spreader` reports. Written against the endpoint rather than against what one
 * page draws: a field left out here is a field nobody can find out is there.
 */
export interface ClusterStatus {
  node: {
    clusterName: string;
    nodeId: string;
    nodeName: string;
    address: string;
    leader: boolean;
    leaderAddress: string;
    onBreak: boolean;
    memberCount: number;
    uptimeMillis: number;
  };
  cluster?: {
    running: boolean;
    metricsEnabled: boolean;
    /**
     * Two halves of one cluster, each electing its own leader. `healthy` is the answer; the rest
     * is how it was arrived at, and `everSplit` matters after the fact -- a split that healed
     * still explains rows that disagree.
     */
    splitBrain?: {
      healthy: boolean;
      splitting: boolean;
      occurrences: number;
      everSplit: boolean;
      leader: string;
      holders: string[];
      lastDetectedAt: number;
      splittingDurationMillis: number;
    };
  };
  summary: {
    totalTps: number;
    errorRate: number;
    channelCount: number;
    businessChannelCount?: number;
    systemChannelCount?: number;
    hasDroppedMessages: boolean;
    hottestBuffer?: { name: string; usage: number };
  };
  channels: Record<string, ClusterChannel>;
  /** The queues in front of the handlers. Reported per node, not per channel. */
  buffers?: ClusterBuffer[];
  /** One entry per replicated store: the cache, the record log, the rocksdb mirrors. */
  components?: Record<string, Record<string, unknown>>;
  timestamp?: number;
}

export interface ClusterChannel {
  channel: string;
  systemChannel: boolean;
  throughput: {
    tps: number;
    sentTps: number;
    receivedTps: number;
    peakTps: number;
    peakSentTps?: number;
    peakReceivedTps?: number;
  };
  counters: {
    sent: number;
    sendFailures: number;
    retries: number;
    received: number;
    receiveFailures: number;
    duplicates: number;
  };
  concurrency?: { current: number; peak: number };
  rates?: {
    errorRate: number;
    sendErrorRate: number;
    receiveErrorRate: number;
    retryRate: number;
  };
  /** Milliseconds out and back. The percentiles are the useful half; an average hides the outlier. */
  latencyMillis?: {
    outbound?: ChannelLatency;
    inbound?: ChannelLatency;
  };
}

export interface ChannelLatency {
  count: number;
  min: number;
  avg: number;
  max: number;
  p50: number;
  p95: number;
  p99: number;
}

export interface ClusterBuffer {
  name: string;
  size: number;
  capacity: number;
  remaining: number;
  /** 0..1, already computed by the endpoint. */
  usage: number;
  handled: number;
  dropped: number;
  dropRate: number;
  hasDropped: boolean;
}

/** One of the nodes the front end's proxy is in front of, as a browser can reach it. */
export interface ProxyNode {
  index: number;
  address: string;
}
