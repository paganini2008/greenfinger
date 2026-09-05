import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

/**
 * The api client. Two things are worth pinning down: the envelope never leaks past this class, and
 * an envelope that says success=false is a failure however successful the http call was.
 */
describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('hands the caller the payload, not the envelope', () => {
    let received: unknown;
    api.listCatalogs().subscribe((catalogs) => (received = catalogs));

    http.expectOne('/v2/catalog').flush({
      success: true,
      message: 'ok',
      data: [{ id: '1', name: 'books', url: 'https://books.toscrape.com' }],
    });

    expect(received).toEqual([{ id: '1', name: 'books', url: 'https://books.toscrape.com' }]);
  });

  it('raises an envelope that failed, rather than passing it off as an empty result', () => {
    let message = '';
    api.search({ q: 'anything' }).subscribe({ error: (failure) => (message = failure.message) });

    http.expectOne((request) => request.url === '/v2/search').flush({
      success: false,
      message: 'Nothing has finished crawling yet',
      data: null,
    });

    expect(message).toBe('Nothing has finished crawling yet');
  });

  it('sends the cursor as repeated parameters, because its parts may contain commas', () => {
    api.search({ q: 'book', cursor: ['12.5', 'a,b'] }).subscribe();

    const request = http.expectOne((one) => one.url === '/v2/search');
    expect(request.request.params.getAll('cursor')).toEqual(['12.5', 'a,b']);
    request.flush({ success: true, message: 'ok', data: { results: [] } });
  });

  it('defaults a version delete to a dry run', () => {
    api.deleteVersions('books', { keepLatest: 3, layers: ['index', 'vector'] }).subscribe();

    const request = http.expectOne((one) => one.url === '/v2/crawl/books/versions');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.params.get('dryRun')).toBe('true');
    expect(request.request.params.get('keepLatest')).toBe('3');
    expect(request.request.params.get('layers')).toBe('index,vector');
    request.flush({ success: true, message: 'ok', data: [] });
  });

  it('carries purge only when it was asked for, because emptying and dropping are different', () => {
    api.deleteVersions('books', { keepLatest: 0, purge: true }).subscribe();
    const purging = http.expectOne((one) => one.url === '/v2/crawl/books/versions');
    expect(purging.request.params.get('purge')).toBe('true');
    purging.flush({ success: true, data: [] });

    api.deleteVersions('books', { keepLatest: 0 }).subscribe();
    const emptying = http.expectOne((one) => one.url === '/v2/crawl/books/versions');
    // absent rather than false: the server's default is false and a parameter it did not need
    // is a parameter that can be got wrong
    expect(emptying.request.params.has('purge')).toBe(false);
    emptying.flush({ success: true, data: [] });
  });

  it('fetches a picture as bytes, so the token travels with the request', () => {
    // an <img src> sends no Authorization header and would get a 401, so the tag never makes the
    // request itself: this call does, through the interceptor, and the blob becomes an object url
    let received: Blob | undefined;
    api.imageBytes('books/v0/images/ab/cd/x.jpg').subscribe((blob) => (received = blob));

    const request = http.expectOne((one) => one.url === '/v2/image');
    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.get('path')).toBe('books/v0/images/ab/cd/x.jpg');
    request.flush(new Blob(['bytes'], { type: 'image/jpeg' }));

    expect(received?.type).toBe('image/jpeg');
  });

  it('replays the index and the vectors by default', () => {
    api.replay('books').subscribe();

    const request = http.expectOne((one) => one.url === '/v2/crawl/books/replay');
    expect(request.request.params.get('layers')).toBe('index,vector');
    request.flush({ success: true, message: 'ok', data: 4 });
  });

  it('asks for the file layer only when files are what is being restored', () => {
    // the file layer means "fetch every page again", so it travels only when it was chosen
    api.replay('books', ['file']).subscribe();

    const request = http.expectOne((one) => one.url === '/v2/crawl/books/replay');
    expect(request.request.params.get('layers')).toBe('file');
    request.flush({ success: true, message: 'ok', data: 4 });
  });

  it('leaves the offset off the first vector page, so the common request keeps its shape', () => {
    api.semanticSearch('rain').subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/v2/search/semantic');
    expect(request.request.params.has('offset')).toBe(false);
    request.flush({ success: true, message: 'ok', data: [] });
  });

  it('asks for a later vector page by offset, since there is no cursor to carry', () => {
    api.imageSearch('a red cover', 'books2', 24, 48).subscribe();

    const request = http.expectOne((candidate) => candidate.url === '/v2/search/images');
    expect(request.request.params.get('offset')).toBe('48');
    expect(request.request.params.get('size')).toBe('24');
    expect(request.request.params.get('catalog')).toBe('books2');
    request.flush({ success: true, message: 'ok', data: [] });
  });
});
