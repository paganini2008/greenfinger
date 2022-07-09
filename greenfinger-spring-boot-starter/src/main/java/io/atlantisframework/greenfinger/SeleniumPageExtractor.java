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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.commons.pool2.PooledObject;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.github.paganini2008.devtools.RandomUtils;
import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

import io.atlantisframework.vortex.common.Tuple;

/**
 * 
 * SeleniumPageExtractor
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class SeleniumPageExtractor extends PageExtractorSupport<WebDriver> implements PageExtractor {

	public SeleniumPageExtractor(String webdriverExecutionPath) {
		System.setProperty("webdriver.chrome.driver", webdriverExecutionPath);
		System.setProperty("webdriver.chrome.args", "--disable-logging");
		System.setProperty("webdriver.chrome.silentOutput", "true");
	}

	private String proxyAddress;

	public void setProxyAddress(String proxyAddress) {
		this.proxyAddress = proxyAddress;
	}

	public String extractHtml(String refer, String url, Charset pageEncoding, Tuple tuple) throws Exception {
		WebDriver webdriver = objectPool.borrowObject();
		try {
			webdriver.get(url);
			return webdriver.getPageSource();
		} finally {
			if (webdriver != null) {
				objectPool.returnObject(webdriver);
			}
		}
	}

	public WebDriver createObject() throws Exception {
		ChromeOptions options = new ChromeOptions();
		if (StringUtils.isNotBlank(proxyAddress)) {
			Proxy proxy = new Proxy();
			proxy.setHttpProxy(proxyAddress);
			options.setProxy(proxy);
		}
		options.addArguments("lang=en_US.UTF-8");
		options.addArguments("user-agent=" + RandomUtils.randomChoice(userAgents));
		options.addArguments("--test-type", "--ignore-certificate-errors", "--start-maximized", "no-default-browser-check");
		options.addArguments("--silent", "--headless", "--disable-gpu");
		setDefaultHeaders(options);
		ChromeDriver driver = new ChromeDriver(options);
		driver.setLogLevel(Level.ALL);
		driver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS).setScriptTimeout(60, TimeUnit.SECONDS).implicitlyWait(5,
				TimeUnit.SECONDS);
		return driver;
	}

	private void setDefaultHeaders(ChromeOptions options) {
		Map<String, String> defaultHeaders = getDefaultHeaders();
		if (MapUtils.isNotEmpty(defaultHeaders)) {
			List<String> arguments = new ArrayList<String>();
			for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
				arguments.add(entry.toString());
			}
			options.addArguments(arguments);
		}
	}

	protected Map<String, String> getDefaultHeaders() {
		return null;
	}

	public void destroyObject(PooledObject<WebDriver> object) throws Exception {
		object.getObject().quit();
	}

	public static void main(String[] args) throws Exception {
		String webdriverExecutionPath = "D:\\software\\chromedriver_win32\\chromedriver.exe";
		SeleniumPageExtractor pageExtractor = new SeleniumPageExtractor(webdriverExecutionPath);
		pageExtractor.configure();
		RetryablePageExtractor retryablePageExtractor = new RetryablePageExtractor(pageExtractor);
		System.out
				.println(retryablePageExtractor.extractHtml("http://www.ttmeishi.com", "http://www.ttmeishi.com/CaiXi/tese/", null, null));
		System.in.read();
		pageExtractor.destroy();
	}

}
