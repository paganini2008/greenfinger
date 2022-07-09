/**
* Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.atlantisframework.greenfinger;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.github.paganini2008.devtools.CharsetUtils;
import com.github.paganini2008.devtools.RandomUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

import io.atlantisframework.tridenter.http.CharsetDefinedRestTemplate;
import io.atlantisframework.tridenter.utils.BeanLifeCycle;
import io.atlantisframework.vortex.common.Tuple;

/**
 * 
 * HttpClientPageExtractor
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class HttpClientPageExtractor implements PageExtractor, BeanLifeCycle {

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

	private String proxyAddress;

	public void setProxyAddress(String proxyAddress) {
		this.proxyAddress = proxyAddress;
	}

	@Override
	public void configure() throws Exception {
		ClientHttpRequestFactory factory = restTemplate.getRequestFactory();
		if (factory instanceof SimpleClientHttpRequestFactory) {
			SimpleClientHttpRequestFactory simpleFactory = (SimpleClientHttpRequestFactory) factory;
			String[] args = proxyAddress.split(":");
			InetSocketAddress address = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
			Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
			simpleFactory.setProxy(proxy);
		}
	}

	public String extractHtml(String refer, String url, Charset pageEncoding, Tuple tuple) throws Exception {
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

	public RestTemplate getRestTemplate() {
		return restTemplate;
	}

	public static void main(String[] args) throws Exception {
		HttpClientPageExtractor pageSource = new HttpClientPageExtractor();
		// System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
		System.out.println(pageSource.extractHtml("https://www.tuniu.com",
				"https://www.tuniu.com/?p=1400&cmpid=mkt_06002401&utm_source=baidu&utm_medium=brand&utm_campaign=brand", CharsetUtils.UTF_8,
				null));
		System.in.read();
		System.out.println("HttpClientPageExtractor.main()");
	}

}
