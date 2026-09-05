/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.core.component.extractor;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.ThreadUtils;

/**
 * The default engine: a plain http fetch, for the great majority of pages whose content is in the
 * response rather than assembled by script.
 *
 * <p>
 * Built on {@code RestClient} over Apache HttpClient 5. 1.x used {@code RestTemplate} with
 * {@code SimpleClientHttpRequestFactory}, which opens a fresh TCP connection per request and offers
 * no cookie store; a pooled client reuses connections across the thousands of requests a crawl
 * makes against one host.
 *
 * <p>
 * The body is read as bytes and decoded deliberately -- by the response's own charset when it
 * declares one, otherwise by the catalog's configured page encoding. 1.x forced UTF-8 on every
 * response, which turned any GBK or Shift-JIS page into mojibake.
 * 
 * @Description: RestClientExtractor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class RestClientExtractor extends AbstractExtractor
        implements NamedExtractor, ManagedBeanLifeCycle {

    private final WebCrawlerExtractorProperties extractorProperties;
    private PoolingHttpClientConnectionManager connectionManager;
    private RestClient restClient;

    public RestClientExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this.extractorProperties = extractorProperties;
    }

    public RestClientExtractor(RestClient restClient,
            WebCrawlerExtractorProperties extractorProperties) {
        this.restClient = restClient;
        this.extractorProperties = extractorProperties;
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_RESTCLIENT;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (restClient != null) {
            return;
        }
        WebCrawlerExtractorProperties.RestClient config = extractorProperties.getRestClient();

        connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(config.getMaxConnectionTotal())
                .setMaxConnPerRoute(config.getMaxConnectionPerRoute())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(config.getConnectTimeout()))
                        .setSocketTimeout(Timeout.ofMilliseconds(config.getReadTimeout())).build())
                .build();

        RequestConfig.Builder requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(
                        Timeout.ofMilliseconds(config.getConnectionRequestTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(config.getReadTimeout()))
                .setRedirectsEnabled(config.isFollowRedirects());

        var httpClientBuilder = HttpClients.custom().setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig.build());
        if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
            httpClientBuilder.setProxy(new HttpHost(config.getProxyHost(), config.getProxyPort()));
        }
        HttpClient httpClient = httpClientBuilder.build();

        ClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * What counts as a page. XHTML and XML are here because a sitemap or an atom feed is markup
     * worth reading; plain text because some sites serve html as text/plain and the parser copes.
     */
    private static final List<MediaType> MARKUP = List.of(MediaType.TEXT_HTML,
            MediaType.APPLICATION_XHTML_XML, MediaType.APPLICATION_XML, MediaType.TEXT_XML,
            MediaType.TEXT_PLAIN);

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        return exchange(referUrl, url, pageEncoding, ConditionalGet.NONE).html();
    }

    /**
     * The conditional form. This engine is the only one that implements it, because it is the only
     * one holding the response rather than driving a page load.
     */
    @Override
    public FetchedPage fetch(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, ConditionalGet conditions) throws Exception {
        FetchedPage fetched = exchange(referUrl, url, pageEncoding, conditions);
        if (fetched.notModified()) {
            return fetched;
        }
        return new FetchedPage(
                rewriteContent(catalogDetails, referUrl, url, pageEncoding, fetched.html()), false,
                fetched.etag(), fetched.lastModified());
    }

    private FetchedPage exchange(String referUrl, String url, Charset pageEncoding,
            ConditionalGet conditions) {
        WebCrawlerExtractorProperties.RestClient config = extractorProperties.getRestClient();
        Charset fallback = pageEncoding != null ? pageEncoding : StandardCharsets.UTF_8;

        FetchedPage fetched = restClient.get().uri(URI.create(url)).headers(headers -> {
            defaultHttpHeaders.forEach(headers::set);
            config.getDefaultHttpHeaders().forEach(headers::set);
            headers.set(HttpHeaders.USER_AGENT, randomUserAgent());
            if (StringUtils.isNotBlank(referUrl)) {
                headers.set(HttpHeaders.REFERER, referUrl);
            }
            if (StringUtils.isNotBlank(conditions.etag())) {
                headers.set(HttpHeaders.IF_NONE_MATCH, conditions.etag());
            }
            if (StringUtils.isNotBlank(conditions.lastModified())) {
                headers.set(HttpHeaders.IF_MODIFIED_SINCE, conditions.lastModified());
            }
        }).exchange((request, response) -> {
            if (response.getStatusCode().value() == HttpStatus.NOT_MODIFIED.value()) {
                // the site kept its word; carry the same validators forward
                return FetchedPage.notModified(conditions);
            }
            // Success is the 2xx range, and anything outside it is an invalid url.
            //
            // The status is the one at the end of the chain, not the first on it. Redirects are
            // followed by the http client (followRedirects, on by default), so a page behind a
            // 301 or a 302 arrives here as the 200 it finally answered with and is kept. A 3xx
            // only reaches this line when redirects are off or the chain ran out, and then it
            // really is a url that served nothing.
            //
            // 304 is handled above and is not a failure: it is the site answering the
            // conditional request an update sent, which means the page has not changed.
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ExtractorException(url, response.getStatusCode());
            }
            HttpHeaders responseHeaders = response.getHeaders();
            // A 200 is not the same as a page. Sites link straight at their own pictures and
            // their own pdfs -- apod.nasa.gov links every day's photograph that way -- and those
            // links are in scope, pass every filter and answer 200. Decoded as text, a jpeg
            // becomes a "page" with no title and half a megabyte of mojibake, which is then
            // written to disk, counted against maxFetchSize and put into the search index.
            //
            // Images are fetched by the image pipeline, from the <img> tags that point at them,
            // and this is not that. So anything that is not markup is refused here, and refused
            // as an invalid url, because for a crawler of pages that is exactly what it is.
            requireMarkup(url, responseHeaders.getContentType());
            Charset charset = charsetOf(responseHeaders, fallback);
            try (InputStream in = response.getBody()) {
                return new FetchedPage(new String(in.readAllBytes(), charset), false,
                        responseHeaders.getFirst(HttpHeaders.ETAG),
                        responseHeaders.getFirst(HttpHeaders.LAST_MODIFIED));
            }
        });

        if (config.getLoadingTimeout() > 0) {
            ThreadUtils.sleep(config.getLoadingTimeout());
        }
        return fetched;
    }

    /**
     * Refuses a body that is not markup.
     *
     * <p>
     * A missing Content-Type is allowed through: it is rare, it is usually html, and refusing it
     * would drop pages from servers that are merely old. Everything that does declare itself has
     * to declare itself as html, xhtml, xml or plain text.
     */
    private void requireMarkup(String url, MediaType contentType) {
        if (contentType == null) {
            return;
        }
        for (MediaType allowed : MARKUP) {
            if (allowed.includes(contentType)) {
                return;
            }
        }
        throw new ExtractorException(url,
                "the server answered with " + contentType + ", which is not a page");
    }

    private Charset charsetOf(HttpHeaders headers, Charset fallback) {
        MediaType contentType = headers.getContentType();
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return fallback;
    }

    private String randomUserAgent() {
        var agents = WebCrawlerConstants.USER_AGENTS;
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }

    @Override
    public void destroy() throws Exception {
        if (connectionManager != null) {
            connectionManager.close();
            connectionManager = null;
        }
    }

}
