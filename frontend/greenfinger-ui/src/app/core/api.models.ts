/**
 * The shapes the api actually returns.
 *
 * Written by hand against the controllers rather than generated, because there are twenty of them
 * and a generator would drag a build step into a project that does not otherwise need one. The
 * field names match the Java exactly; where the backend serialises an enum as something other than
 * its name -- countingType is an int, outputTypes are lower case strings -- that is spelled out
 * here so a form never has to guess.
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
export type RunningState = 'none' | 'crawl' | 'update' | 'rebuild';
export type DeleteLayer = 'db' | 'file' | 'index' | 'vector' | 'all';

/** What a crawl counts towards maxFetchSize. Serialised as the int, so the value is the wire form. */
export const COUNTING_TYPES = [
  { value: 0, label: 'Urls seen' },
  { value: 1, label: 'Invalid urls' },
  { value: 2, label: 'Urls already known' },
  { value: 3, label: 'Urls filtered out' },
  { value: 4, label: 'Pages saved' },
  { value: 5, label: 'Pages indexed' },
  { value: 6, label: 'Images saved' },
  { value: 7, label: 'Duplicate pages' },
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
  lastModified?: string | null;
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
  savedResourceCount: number;
  indexedResourceCount: number;
  savedImageCount: number;
  duplicatedContentCount: number;
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
export interface ServerVersion {
  name: string;
  version: string;
  builtAt?: string;
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
 * One crawl, as it was accounted for afterwards.
 *
 * Written by every node that took part, and every copy carries the whole picture -- the totals
 * for the crawl and, in `byNode`, what each node did towards them. `node` says which node wrote
 * this copy.
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
  produced: { savedResourceCount: number; savedImageCount: number; indexedResourceCount: number };
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

/** What `/actuator/spreader` reports about this node and the traffic it is carrying. */
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
  summary: {
    totalTps: number;
    errorRate: number;
    channelCount: number;
    hasDroppedMessages: boolean;
  };
  channels: Record<string, ClusterChannel>;
}

export interface ClusterChannel {
  channel: string;
  systemChannel: boolean;
  throughput: { tps: number; sentTps: number; receivedTps: number; peakTps: number };
  counters: {
    sent: number;
    sendFailures: number;
    retries: number;
    received: number;
    receiveFailures: number;
    duplicates: number;
  };
  buffer?: { pending: number; capacity: number; dropped: number; handled: number };
}
