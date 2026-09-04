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
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ExtractorException(url, response.getStatusCode());
            }
            HttpHeaders responseHeaders = response.getHeaders();
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
