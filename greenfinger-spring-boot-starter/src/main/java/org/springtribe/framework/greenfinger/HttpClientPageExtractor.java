package org.springtribe.framework.greenfinger;

import java.nio.charset.Charset;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springtribe.framework.cluster.http.CharsetDefinedRestTemplate;

import com.github.paganini2008.devtools.CharsetUtils;
import com.github.paganini2008.devtools.RandomUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

/**
 * 
 * HttpClientPageExtractor
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public class HttpClientPageExtractor implements PageExtractor {

	private final RestTemplate restTemplate;

	public HttpClientPageExtractor() {
		this(CharsetUtils.UTF_8);
	}

	public HttpClientPageExtractor(Charset defaultPageEncoding) {
		this.restTemplate = new CharsetDefinedRestTemplate(defaultPageEncoding);
	}

	public HttpClientPageExtractor(ClientHttpRequestFactory clientHttpRequestFactory, Charset defaultPageEncoding) {
		this.restTemplate = new CharsetDefinedRestTemplate(clientHttpRequestFactory, defaultPageEncoding);
	}

	public String extractHtml(String refer, String url, Charset pageEncoding) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		MultiValueMap<String, String> defaultHeaders = getDefaultHeaders();
		if (MapUtils.isNotEmpty(defaultHeaders)) {
			headers.addAll(defaultHeaders);
		}
		ResponseEntity<String> responseEntity;
		responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
		if (responseEntity.getStatusCode() == HttpStatus.OK) {
			return responseEntity.getBody();
		}
		throw new PageExtractorException(url, responseEntity.getStatusCode());
	}

	protected MultiValueMap<String, String> getDefaultHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Accept", "*/*");
		headers.add("User-Agent", RandomUtils.randomChoice(userAgents));
		headers.add("X-Forwarded-For", RandomIpUtils.randomIp());
		headers.add("Accept-Language", "zh-CN,zh;q=0.8,en-US,en;q=0.2");
		return headers;
	}

	public static void main(String[] args) throws Exception {
		HttpClientPageExtractor pageSource = new HttpClientPageExtractor();
		// System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
		System.out.println(pageSource.extractHtml("https://www.tuniu.com",
				"http://caipu.haochi123.com/Recipe/2015/9612659687.html?#Flag_Photo", null));
		System.in.read();
		System.out.println("HttpClientPageExtractor.main()");
	}

}
