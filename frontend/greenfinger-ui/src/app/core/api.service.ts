import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_PREFIX } from './api.config';
import {
  ApiResult,
  Catalog,
  CatalogDetails,
  CatalogSummary,
  ClusterStatus,
  CrawlReport,
  CrawlStatus,
  DeleteLayer,
  DeleteLine,
  HealthReport,
  NodeReading,
  OutputStores,
  ProxyNode,
  ResourcePage,
  RocksDbUsage,
  SearchResponse,
  ServerVersion,
  SettingsReport,
  StorageUsage,
  VectorHit,
} from './api.models';

/**
 * Every call the front end makes. Each unwraps the {@link ApiResult} envelope, and a response
 * whose {@code success} is false becomes an error rather than a result that looks empty.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /**
   * What is running. Open without signing in, so the login page can show it too: being told which
   * version refused your password is how you find out you are pointed at the wrong server.
   */
  /** Which store each output writes to, for the page that names them. */
  outputs(): Observable<OutputStores> {
    return this.unwrap(this.http.get<ApiResult<OutputStores>>(`${API_PREFIX}/outputs`));
  }

  version(): Observable<ServerVersion> {
    return this.unwrap(this.http.get<ApiResult<ServerVersion>>(`${API_PREFIX}/version`));
  }

  // ---- catalogs -------------------------------------------------------------------------

  listCatalogs(): Observable<Catalog[]> {
    return this.unwrap(this.http.get<ApiResult<Catalog[]>>(`${API_PREFIX}/catalog`));
  }

  listCategories(): Observable<string[]> {
    return this.unwrap(this.http.get<ApiResult<string[]>>(`${API_PREFIX}/catalog/cats`));
  }

  getCatalog(ref: string): Observable<Catalog> {
    return this.unwrap(this.http.get<ApiResult<Catalog>>(`${API_PREFIX}/catalog/${encodeURIComponent(ref)}`));
  }

  getCatalogDetails(ref: string): Observable<CatalogDetails> {
    return this.unwrap(
      this.http.get<ApiResult<CatalogDetails>>(`${API_PREFIX}/catalog/${encodeURIComponent(ref)}/details`),
    );
  }

  getSummary(ref: string): Observable<CatalogSummary> {
    return this.unwrap(
      this.http.get<ApiResult<CatalogSummary>>(`${API_PREFIX}/catalog/${encodeURIComponent(ref)}/summary`),
    );
  }

  saveCatalog(catalog: Catalog): Observable<Catalog> {
    return this.unwrap(this.http.post<ApiResult<Catalog>>(`${API_PREFIX}/catalog`, catalog));
  }

  /** Removes the definition only. What it crawled goes through deleteVersions. */
  deleteCatalog(ref: string): Observable<boolean> {
    return this.unwrap(this.http.delete<ApiResult<boolean>>(`${API_PREFIX}/catalog/${encodeURIComponent(ref)}`));
  }

  // ---- running a crawl ------------------------------------------------------------------

  crawl(ref: string): Observable<string> {
    return this.unwrap(this.http.post<ApiResult<string>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}`, {}));
  }

  update(ref: string, from?: string): Observable<string> {
    const params = from ? new HttpParams().set('from', from) : undefined;
    return this.unwrap(
      this.http.post<ApiResult<string>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}/update`, {}, { params }),
    );
  }

  rebuild(ref: string): Observable<string> {
    return this.unwrap(
      this.http.post<ApiResult<string>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}/rebuild`, {}),
    );
  }

  interrupt(ref: string): Observable<boolean> {
    return this.unwrap(
      this.http.post<ApiResult<boolean>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}/interrupt`, {}),
    );
  }

  /** Every catalog's progress in one call, which is what the list page polls. */
  status(): Observable<CrawlStatus[]> {
    return this.unwrap(this.http.get<ApiResult<CrawlStatus[]>>(`${API_PREFIX}/crawl/status`));
  }

  /**
   * Removes crawled versions from the stores named. {@code dryRun} defaults to true on both
   * sides: this is the one call that cannot be undone.
   */
  deleteVersions(
    ref: string,
    options: {
      version?: number;
      keepLatest?: number;
      layers?: DeleteLayer[];
      /**
       * Take the catalog itself, not only what it crawled. Meaningful only on a whole-catalog
       * delete -- one that names neither a version nor a keepLatest.
       */
      purge?: boolean;
      dryRun?: boolean;
      force?: boolean;
    },
  ): Observable<DeleteLine[]> {
    let params = new HttpParams();
    if (options.version !== undefined) params = params.set('version', options.version);
    if (options.keepLatest !== undefined) params = params.set('keepLatest', options.keepLatest);
    if (options.layers?.length) params = params.set('layers', options.layers.join(','));
    if (options.purge) params = params.set('purge', true);
    params = params.set('dryRun', options.dryRun ?? true);
    if (options.force) params = params.set('force', true);
    return this.unwrap(
      this.http.delete<ApiResult<DeleteLine[]>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}/versions`, {
        params,
      }),
    );
  }

  /** Rebuilds the index and the vectors from what is already on disk. No re-crawl. */
  replay(ref: string, layers: string[] = ['index', 'vector'], version?: number): Observable<number> {
    let params = new HttpParams().set('layers', layers.join(','));
    if (version !== undefined) params = params.set('version', version);
    return this.unwrap(
      this.http.post<ApiResult<number>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}/replay`, {}, { params }),
    );
  }

  // ---- run reports ----------------------------------------------------------------------

  /**
   * Every run of this catalog, newest first. One per node per crawl: each node writes its own
   * account and they are copied to each other, so any node can answer for the whole history.
   */
  crawlReports(ref: string): Observable<CrawlReport[]> {
    return this.unwrap(
      this.http.get<ApiResult<CrawlReport[]>>(`${API_PREFIX}/crawl/${encodeURIComponent(ref)}/reports`),
    );
  }

  // ---- the cluster ------------------------------------------------------------------------

  /**
   * This node's view of the cluster. Spring's actuator, so neither the prefix nor the envelope.
   * Every node answers for itself alone, which is the point.
   */
  clusterStatus(node?: number | null): Observable<ClusterStatus> {
    return this.http.get<ClusterStatus>('/actuator/spreader', { params: pin(node) });
  }

  /**
   * One node's heap and cpu. Under the api prefix rather than the actuator's metrics endpoint,
   * which production does not expose -- so this column used to be empty exactly where it matters.
   */
  node(node?: number | null): Observable<NodeReading> {
    return this.unwrap(
      this.http.get<ApiResult<NodeReading>>(`${API_PREFIX}/node`, { params: pin(node) }),
    );
  }


  /**
   * The nodes this front end is in front of. Served by the proxy, not the api: a node knows its
   * cluster but not which members a browser can reach. Absent, and the picker simply disappears.
   */
  proxyNodes(): Observable<ProxyNode[]> {
    return this.http.get<ProxyNode[]>('/__nodes');
  }

  /**
   * What this node is actually configured with, which is not what any one file says: four sources
   * are merged and the last one wins. Secrets are masked by the endpoint, not here.
   */
  settings(node?: number | null): Observable<SettingsReport> {
    return this.http.get<SettingsReport>('/actuator/settings', { params: pin(node) });
  }

  /**
   * Up or down, from the endpoint an orchestrator asks, with the checks behind it where
   * `show-details` is on. Flattened here rather than in the template.
   */
  health(node?: number | null): Observable<HealthReport> {
    return this.http.get<RawHealth>('/actuator/health', { params: pin(node) }).pipe(
      map((raw) => ({
        status: raw.status,
        components: Object.entries(raw.components ?? {})
          .map(([name, component]) => ({
            name,
            status: component?.status ?? 'UNKNOWN',
            details: component?.details ?? {},
          }))
          // anything not UP first: the reason somebody opened this page is at the top
          .sort((a, b) => {
            if (a.status !== b.status) {
              return a.status === 'UP' ? 1 : -1;
            }
            return a.name.localeCompare(b.name);
          }),
      })),
    );
  }

  // ---- what a crawl stored ----------------------------------------------------------------

  /**
   * Rows from the resource table, in crawl order. Not the index: this can show a version that was
   * never published, which is the one somebody looks at when a crawl came back wrong.
   */
  resources(options: {
    catalogId: string;
    version?: number | null;
    q?: string;
    from?: string | null;
    to?: string | null;
    page?: number;
    size?: number;
    sort?: 'asc' | 'desc';
  }): Observable<ResourcePage> {
    let params = new HttpParams().set('catalogId', options.catalogId);
    if (options.version !== null && options.version !== undefined) {
      params = params.set('version', options.version);
    }
    if (options.q) params = params.set('q', options.q);
    if (options.from) params = params.set('from', options.from);
    if (options.to) params = params.set('to', options.to);
    if (options.page) params = params.set('page', options.page);
    if (options.size) params = params.set('size', options.size);
    if (options.sort) params = params.set('sort', options.sort);
    return this.unwrap(
      this.http.get<ApiResult<ResourcePage>>(`${API_PREFIX}/resource`, { params }),
    );
  }

  /** The versions that actually have rows, which is what the filter offers. */
  resourceVersions(catalogId: string): Observable<number[]> {
    const params = new HttpParams().set('catalogId', catalogId);
    return this.unwrap(
      this.http.get<ApiResult<number[]>>(`${API_PREFIX}/resource/versions`, { params }),
    );
  }

  /** On demand, never polled: a directory walk on disk and a paged list on MinIO. */
  storageUsage(catalogId?: string): Observable<StorageUsage> {
    let params = new HttpParams();
    if (catalogId) params = params.set('catalogId', catalogId);
    return this.unwrap(
      this.http.get<ApiResult<StorageUsage>>(`${API_PREFIX}/storage`, { params }),
    );
  }

  /**
   * The crawl's own state: the frontier and the two dedup filters. The key counts mean opening
   * each store, which is why they are missing while that catalog is being crawled.
   */
  rocksDbUsage(catalogId: string, version?: number | null): Observable<RocksDbUsage> {
    let params = new HttpParams().set('catalogId', catalogId);
    if (version !== null && version !== undefined) {
      params = params.set('version', version);
    }
    return this.unwrap(
      this.http.get<ApiResult<RocksDbUsage>>(`${API_PREFIX}/storage/rocksdb`, { params }),
    );
  }

  // ---- search ---------------------------------------------------------------------------

  search(options: {
    q: string;
    catalog?: string;
    cat?: string;
    page?: number;
    size?: number;
    cursor?: unknown[] | null;
  }): Observable<SearchResponse> {
    let params = new HttpParams().set('q', options.q);
    if (options.catalog) params = params.set('catalog', options.catalog);
    if (options.cat) params = params.set('cat', options.cat);
    if (options.page) params = params.set('page', options.page);
    if (options.size) params = params.set('size', options.size);
    // repeated rather than joined: the cursor's parts are sort values and may contain commas
    for (const value of options.cursor ?? []) {
      params = params.append('cursor', String(value));
    }
    return this.unwrap(this.http.get<ApiResult<SearchResponse>>(`${API_PREFIX}/search`, { params }));
  }

  /**
   * The bytes of a saved picture. Fetched rather than given to `<img src>`, which sends no bearer
   * token and would draw a broken box; the caller wraps the blob in an object url.
   */
  imageBytes(imageFilePath: string): Observable<Blob> {
    const params = new HttpParams().set('path', imageFilePath);
    return this.http.get(`${API_PREFIX}/image`, { params, responseType: 'blob' });
  }

  /**
   * Vector search pages by offset, not by cursor. There is no stable sort key to carry forward --
   * the ordering is a distance to this one query -- so the second page is "skip the first n".
   */
  semanticSearch(q: string, catalog?: string, size = 10, offset = 0): Observable<VectorHit[]> {
    return this.unwrap(
      this.http.get<ApiResult<VectorHit[]>>(`${API_PREFIX}/search/semantic`, {
        params: this.vectorParams(q, catalog, size, offset),
      }),
    );
  }

  imageSearch(q: string, catalog?: string, size = 20, offset = 0): Observable<VectorHit[]> {
    return this.unwrap(
      this.http.get<ApiResult<VectorHit[]>>(`${API_PREFIX}/search/images`, {
        params: this.vectorParams(q, catalog, size, offset),
      }),
    );
  }

  private vectorParams(q: string, catalog: string | undefined, size: number, offset: number): HttpParams {
    let params = new HttpParams().set('q', q).set('size', size);
    if (catalog) params = params.set('catalog', catalog);
    // left off when zero, so the common request stays the shape it was
    if (offset > 0) params = params.set('offset', offset);
    return params;
  }

  /**
   * A 200 whose envelope says success=false is still a failure -- "nothing has finished crawling
   * yet" arrives that way -- so it is raised rather than passed off as an empty result.
   */
  private unwrap<T>(source: Observable<ApiResult<T>>): Observable<T> {
    return source.pipe(
      map((result) => {
        if (!result.success) {
          throw new Error(result.message);
        }
        return result.data;
      }),
    );
  }
}

/** The actuator's own shape, before {@link ApiService.health} flattens it. */
interface RawHealth {
  status: string;
  components?: Record<string, { status?: string; details?: Record<string, unknown> } | null>;
}

/**
 * Pins a request to one node. Read and stripped by the proxy, so it never reaches a node, and only
 * System health sends it -- every other page asks something any node can answer.
 */
function pin(node?: number | null): HttpParams {
  return node === null || node === undefined
    ? new HttpParams()
    : new HttpParams().set('__node', node);
}
