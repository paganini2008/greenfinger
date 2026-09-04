import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_PREFIX } from './api.config';
import {
  ApiResult,
  Catalog,
  ClusterStatus,
  CrawlReport,
  CatalogDetails,
  CatalogSummary,
  CrawlStatus,
  DeleteLayer,
  DeleteLine,
  SearchResponse,
  ServerVersion,
  VectorHit,
} from './api.models';

/**
 * Every call the front end makes, in one place.
 *
 * Each method unwraps the {@link ApiResult} envelope, so a component works with the payload and
 * never with success/message/data. A response whose {@code success} is false is turned into an
 * error here rather than handed on looking like a result -- a search that found nothing and a
 * search that could not run are different things, and only one of them belongs in a result list.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /**
   * What is running. Open without signing in, so the login page can show it too: being told which
   * version refused your password is how you find out you are pointed at the wrong server.
   */
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
   * Removes crawled versions from whichever stores are named.
   *
   * {@code dryRun} defaults to true on the server for a reason, and the page keeps it that way:
   * this is the one call that cannot be undone, so the operator sees the report before it happens.
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
   * This node's view of the cluster.
   *
   * Not under the api prefix and not in the envelope: it is Spring's actuator, which has its own
   * shape. Every node answers for itself alone, which is the point -- a page that showed one
   * node's numbers as the cluster's would hide exactly the node that had stopped working.
   */
  clusterStatus(): Observable<ClusterStatus> {
    return this.http.get<ClusterStatus>('/actuator/spreader');
  }

  /** Up or down, from the same endpoint a container orchestrator asks. */
  health(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>('/actuator/health');
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
   * The bytes of a picture the crawl saved.
   *
   * The vector store carries a blob store path, which no browser can fetch, so the server turns
   * the path back into bytes. Fetched rather than pointed at with an `<img src>`: the endpoint
   * needs a bearer token and an `<img>` sends none, so the tag would get a 401 and draw a broken
   * box. Going through HttpClient puts the request through the same interceptor as every other
   * call, and the caller wraps the blob in an object url for the tag.
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
