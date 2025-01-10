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
import com.github.greenfinger.WebCrawlerConstants;

/**
 * 
 * @Description: RestTemplateExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class RestTemplateExtractor extends AbstractExtractor implements InitializingBean {

    public RestTemplateExtractor() {}

    public RestTemplateExtractor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private RestTemplate restTemplate;

    private HttpHeaders defaultHeaders = new HttpHeaders() {

        private static final long serialVersionUID = 1L;

        {
            set("Accept", "*/*");
            set("Accept-Language", "en-US,en;q=0.9");
        }
    };

    private String proxyHost;
    private int proxyPort;

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

    public void setDefaultHeaders(HttpHeaders defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (restTemplate != null) {
            return;
        }
        RestTemplate restTemplate = new StringRestTemplate(StandardCharsets.UTF_8);
        SimpleClientHttpRequestFactory clientHttpRequestFactory =
                new SimpleClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(10 * 1000);
        clientHttpRequestFactory.setReadTimeout(60 * 10000);
        if (StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            clientHttpRequestFactory.setProxy(proxy);
        }
        restTemplate.setRequestFactory(clientHttpRequestFactory);
        this.restTemplate = restTemplate;
    }

    protected String requestUrl(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", RandomIpUtils.randomIp());
        headers.set("User-Agent", RandomUtils.randomChoice(WebCrawlerConstants.userAgents));
        if (MapUtils.isNotEmpty(defaultHeaders)) {
            headers.addAll(defaultHeaders);
        }
        ResponseEntity<String> responseEntity;
        responseEntity =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        }
        throw new ExtractorException(url, responseEntity.getStatusCode());
    }


    public static void main(String[] args) throws Exception {
        RestTemplateExtractor pageSource = new RestTemplateExtractor();
        pageSource.afterPropertiesSet();
        // System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
        System.out.println(pageSource.extractHtml("https://www.tuniu.com",
                "https://goldenmatrix.com/company", StandardCharsets.UTF_8, null));
        System.in.read();
        System.out.println("HttpClientPageExtractor.main()");
    }

}
