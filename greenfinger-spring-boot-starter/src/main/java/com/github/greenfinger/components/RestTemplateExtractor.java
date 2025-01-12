package com.github.greenfinger.components;


import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
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
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.WebCrawlerExtractorProperties;

/**
 * 
 * @Description: RestTemplateExtractor
 * @Author: Fred Feng
 * @Date: 12/01/2025
 * @Version 1.0.0
 */
public class RestTemplateExtractor extends AbstractExtractor
        implements NamedExetractor, InitializingBean {

    private RestTemplate restTemplate;
    private final WebCrawlerExtractorProperties extractorProperties;

    public RestTemplateExtractor(WebCrawlerExtractorProperties extractorProperties) {
        this(null, extractorProperties);
    }

    public RestTemplateExtractor(RestTemplate restTemplate,
            WebCrawlerExtractorProperties extractorProperties) {
        this.restTemplate = restTemplate;
        this.extractorProperties = extractorProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.restTemplate == null) {
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
            this.restTemplate = restTemplate;
        }
    }

    @Override
    public String getName() {
        return WebCrawlerConstants.EXTRACTOR_RESTTEMPLATE;
    }

    @Override
    protected String requestUrl(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception {
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
        return headers;
    }

    public static void main(String[] args) throws Exception {
        RestTemplateExtractor pageSource =
                new RestTemplateExtractor(null, new WebCrawlerExtractorProperties());
        pageSource.afterPropertiesSet();
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(
                pageSource.test("https://goldenmatrix.com/company", StandardCharsets.UTF_8));
        System.in.read();
        System.out.println("HttpClientPageExtractor.main()");
    }

}
