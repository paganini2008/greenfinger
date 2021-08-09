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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.pool2.PooledObject;
import org.springframework.http.HttpStatus;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.github.paganini2008.devtools.RandomUtils;
import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * HtmlUnitPageExtractor
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class HtmlUnitPageExtractor extends PageExtractorSupport<WebClient> implements PageExtractor {

	private String proxyAddress;

	public void setProxyAddress(String proxyAddress) {
		this.proxyAddress = proxyAddress;
	}

	@Override
	public WebClient createObject() throws Exception {
		WebClient webClient;
		if (StringUtils.isNotBlank(proxyAddress)) {
			String[] args = proxyAddress.split(":");
			webClient = new WebClient(BrowserVersion.BEST_SUPPORTED, args[0], Integer.parseInt(args[1]));
		} else {
			webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
		}
		Map<String, String> defaultHeaders = getDefaultHeaders();
		if (MapUtils.isNotEmpty(defaultHeaders)) {
			for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
				webClient.addRequestHeader(entry.getKey(), entry.getValue());
			}
		}
		webClient.getOptions().setThrowExceptionOnScriptError(false);
		webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		webClient.getOptions().setActiveXNative(false);
		webClient.getOptions().setCssEnabled(false);
		webClient.getOptions().setJavaScriptEnabled(false);
		webClient.getOptions().setRedirectEnabled(false);
		webClient.getOptions().setDownloadImages(false);
		webClient.getOptions().setUseInsecureSSL(true);
		webClient.getOptions().setTimeout(60 * 1000);
		webClient.setCookieManager(new CookieManager());
		webClient.setAjaxController(new NicelyResynchronizingAjaxController());
		webClient.setJavaScriptTimeout(60 * 1000);
		return webClient;
	}

	@Override
	public void destroyObject(PooledObject<WebClient> object) throws Exception {
		object.getObject().close();
	}

	@Override
	public String extractHtml(String refer, String url, Charset pageEncoding, Tuple tuple) throws Exception {
		WebClient webClient = objectPool.borrowObject();
		try {
			Page page = webClient.getPage(url);
			int responseStatusCode = page.getWebResponse().getStatusCode();
			if (responseStatusCode == HttpStatus.OK.value()) {
				if (page instanceof HtmlPage) {
					return ((HtmlPage) page).asXml();
				} else if (page instanceof TextPage) {
					return ((TextPage) page).getContent();
				}
			}
			throw new PageExtractorException(url, HttpStatus.valueOf(responseStatusCode));
		} finally {
			if (webClient != null) {
				objectPool.returnObject(webClient);
			}
		}
	}

	protected Map<String, String> getDefaultHeaders() {
		Map<String, String> headerMap = new HashMap<String, String>();
		headerMap.put("Accept", "*/*");
		headerMap.put("Accept-Language", "zh-CN,zh;q=0.8,en-US,en;q=0.2");
		headerMap.put("X-Forwarded-For", RandomIpUtils.randomIp());
		headerMap.put("User-Agent", RandomUtils.randomChoice(userAgents));
		return headerMap;
	}

	public static void main(String[] args) throws Exception {
		HtmlUnitPageExtractor pageSource = new HtmlUnitPageExtractor();
		pageSource.configure();
		// System.out.println(pageSource.getHtml("https://blog.csdn.net/u010814849/article/details/52526705"));
		System.out.println(pageSource.extractHtml("https://www.tuniu.com", "https://www.tuniu.com/g1621/tipnews-170353/",
				Charset.defaultCharset(), null));
		System.in.read();
		pageSource.destroy();
	}

}
