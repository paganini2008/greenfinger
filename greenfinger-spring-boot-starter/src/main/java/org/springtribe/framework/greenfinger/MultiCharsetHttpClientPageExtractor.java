package org.springtribe.framework.greenfinger;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.client.ClientHttpRequestFactory;

import com.github.paganini2008.devtools.collection.MapUtils;

/**
 * 
 * MultiCharsetHttpClientPageExtractor
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public class MultiCharsetHttpClientPageExtractor implements PageExtractor {

	private final ClientHttpRequestFactory clientHttpRequestFactory;

	public MultiCharsetHttpClientPageExtractor(ClientHttpRequestFactory clientHttpRequestFactory) {
		this.clientHttpRequestFactory = clientHttpRequestFactory;
	}

	private final Map<Charset, HttpClientPageExtractor> cache = new ConcurrentHashMap<Charset, HttpClientPageExtractor>();

	@Override
	public String extractHtml(String refer, String url, Charset pageEncoding) throws Exception {
		return MapUtils.get(cache, pageEncoding, () -> {
			return new HttpClientPageExtractor(clientHttpRequestFactory, pageEncoding);
		}).extractHtml(refer, url, pageEncoding);
	}

}
