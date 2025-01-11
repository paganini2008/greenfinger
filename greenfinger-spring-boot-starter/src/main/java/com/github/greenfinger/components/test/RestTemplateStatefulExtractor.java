package com.github.greenfinger.components.test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.github.doodler.common.http.StringRestTemplate;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.doodler.common.utils.RandomIpUtils;
import com.github.doodler.common.utils.RandomUtils;
import com.github.doodler.common.utils.ThreadUtils;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.components.ExtractorException;

/**
 * 
 * @Description: RestTemplateStatefulExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class RestTemplateStatefulExtractor extends StatefulExtractor<RestTemplate> {

    public RestTemplateStatefulExtractor(RestTemplate restTemplate,
            WebCrawlerExtractorProperties extractorProperties) {
        this(new DoNothingExtractorCredentialHandler<>(), restTemplate, extractorProperties);
    }

    public RestTemplateStatefulExtractor(ExtractorCredentialHandler<RestTemplate> credentialHandler,
            RestTemplate restTemplate, WebCrawlerExtractorProperties extractorProperties) {
        super(credentialHandler);
        this.restTemplate = restTemplate;
        this.extractorProperties = extractorProperties;
    }

    private final WebCrawlerExtractorProperties extractorProperties;
    private RestTemplate restTemplate;

    @Override
    public RestTemplate createNew() {
        WebCrawlerExtractorProperties.Default config = extractorProperties.getRestTemplate();
        RestTemplate restTemplate = new StringRestTemplate(StandardCharsets.UTF_8);
        SimpleClientHttpRequestFactory clientHttpRequestFactory =
                new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(config.getConnectionTime());
        clientHttpRequestFactory.setReadTimeout(config.getReadTimeout());
        if (StringUtils.isNotBlank(config.getProxyHost()) && config.getProxyPort() > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
            clientHttpRequestFactory.setProxy(proxy);
        }
        restTemplate.setRequestFactory(clientHttpRequestFactory);
        return restTemplate;
    }

    @Override
    public RestTemplate get() {
        if (restTemplate == null) {
            restTemplate = createNew();
        }
        return restTemplate;
    }

    @Override
    public String test(String url, Charset pageEncoding) throws Exception {
        RestTemplate restTemplate = createNew();
        return doRequestUrl(restTemplate, url, url, pageEncoding, new Packet());
    }

    protected synchronized String requestUrl(String referUrl, String url, Charset pageEncoding,
            Packet packet) throws Exception {
        RestTemplate restTemplate = get();
        return doRequestUrl(restTemplate, referUrl, url, pageEncoding, packet);
    }

    private String doRequestUrl(RestTemplate restTemplate, String referUrl, String url,
            Charset pageEncoding, Packet packet) {
        WebCrawlerExtractorProperties.Default config = extractorProperties.getRestTemplate();
        HttpHeaders headers = mergeHttpHeaders();
        ResponseEntity<String> responseEntity;
        responseEntity =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (config.getLoadingTimeout() > 0) {
            ThreadUtils.sleep(config.getLoadingTimeout());
        } else {
            ThreadUtils.randomSleep(1000L);
        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        }
        throw new ExtractorException(url, responseEntity.getStatusCode());
    }

    private HttpHeaders mergeHttpHeaders() {
        WebCrawlerExtractorProperties.Default config = extractorProperties.getRestTemplate();
        HttpHeaders headers = new HttpHeaders(MapUtils.toMultiValueMap(this.defaultHttpHeaders));
        headers.set("X-Forwarded-For", RandomIpUtils.randomIp());
        headers.set("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        headers.addAll(MapUtils.toMultiValueMap(config.getDefaultHttpHeaders()));
        if (CollectionUtils.isNotEmpty(this.coookies)) {
            coookies.forEach(co -> {
                headers.add(HttpHeaders.SET_COOKIE, co.toString());
            });
        }
        return headers;
    }

    @Override
    public String getName() {
        return "default";
    }

    public static void main(String[] args) throws Exception {
        RestTemplateStatefulExtractor pageSource =
                new RestTemplateStatefulExtractor(null, new WebCrawlerExtractorProperties());
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(
                pageSource.test("https://goldenmatrix.com/company", StandardCharsets.UTF_8));
        System.in.read();
        System.out.println("HttpClientPageExtractor.main()");
    }



}
