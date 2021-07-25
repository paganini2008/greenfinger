/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

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
package indi.atlantis.framework.greenfinger;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.client.ClientHttpRequestFactory;

import com.github.paganini2008.devtools.collection.MapUtils;

/**
 * 
 * MultiCharsetHttpClientPageExtractor
 *
 * @author Fred Feng
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
