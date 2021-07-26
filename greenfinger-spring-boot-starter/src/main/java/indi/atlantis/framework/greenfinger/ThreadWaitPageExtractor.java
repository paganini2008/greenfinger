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

import com.github.paganini2008.devtools.RandomUtils;
import com.github.paganini2008.devtools.multithreads.ThreadUtils;

/**
 * 
 * ThreadWaitPageExtractor
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class ThreadWaitPageExtractor implements PageExtractor {

	private final PageExtractor pageExtractor;
	private final long delay;
	
	public ThreadWaitPageExtractor(PageExtractor pageExtractor) {
		this(pageExtractor, 3000);
	}

	public ThreadWaitPageExtractor(PageExtractor pageExtractor, long delay) {
		this.pageExtractor = pageExtractor;
		this.delay = delay;
	}

	@Override
	public String extractHtml(String refer, String url, Charset pageEncoding) throws Exception {
		ThreadUtils.sleep(delay + RandomUtils.randomLong(100, 1000));
		return pageExtractor.extractHtml(refer, url, pageEncoding);
	}

}
