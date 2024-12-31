package com.github.greenfinger.utils;

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
import com.github.greenfinger.WebCrawlerConstants;

/**
 * 
 * @Description: RestTemplatePageSourceExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class RestTemplatePageSourceExtractor implements PageSourceExtractor, InitializingBean {

    public RestTemplatePageSourceExtractor() {}

    public RestTemplatePageSourceExtractor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private RestTemplate restTemplate;
    private String proxyHost;
    private int proxyPort;
    private int connectionTimeout = 10000;
    private int readTimeout = 60000;
    private HttpHeaders defaultHeaders = new HttpHeaders() {

        private static final long serialVersionUID = 1L;

        {
            set("Accept", "*/*");
            set("X-Forwarded-For", RandomIpUtils.randomIp());
            set("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        }
    };

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public HttpHeaders getDefaultHeaders() {
        return defaultHeaders;
    }

    public void setDefaultHeaders(HttpHeaders defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        RestTemplate restTemplate = new StringRestTemplate(StandardCharsets.UTF_8);
        SimpleClientHttpRequestFactory clientHttpRequestFactory =
                new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(connectionTimeout);
        clientHttpRequestFactory.setReadTimeout(readTimeout);
        if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            clientHttpRequestFactory.setProxy(proxy);
        }
        restTemplate.setRequestFactory(clientHttpRequestFactory);
        this.restTemplate = restTemplate;
    }

    public String extractHtml(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            headers.addAll(defaultHeaders);
        }
        ResponseEntity<String> responseEntity;
        responseEntity =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        }
        throw new PageSourceExtractorException(url, responseEntity.getStatusCode());
    }


    public static void main(String[] args) throws Exception {
        RestTemplatePageSourceExtractor pageSource = new RestTemplatePageSourceExtractor();
        pageSource.afterPropertiesSet();
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(pageSource.extractHtml("https://www.tuniu.com",
                "https://blog.csdn.net/fengxiaotao_cool/article/details/144000859",
                StandardCharsets.UTF_8, null));
        System.in.read();
        System.out.println("HttpClientPageExtractor.main()");
    }

}
