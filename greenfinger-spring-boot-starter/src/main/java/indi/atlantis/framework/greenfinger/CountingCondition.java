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

import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * CountingCondition
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public class CountingCondition extends DurationCondition {

	public CountingCondition(CrawlerSummary crawlerSummary, long defaultDuration, int defaultMaxFetchSize) {
		super(crawlerSummary, defaultDuration);
		this.defaultMaxFetchSize = defaultMaxFetchSize;
	}

	private final int defaultMaxFetchSize;

	private ConditionalCountingType countingType = ConditionalCountingType.URL_COUNT;

	public void setCountingType(ConditionalCountingType countingType) {
		this.countingType = countingType;
	}

	@Override
	protected boolean evaluate(long catalogId, Tuple tuple) {
		long maxFetchSize = (Integer) tuple.getField("maxFetchSize", defaultMaxFetchSize);
		return countingType.evaluate(getCrawlerSummary().getSummary(catalogId), maxFetchSize);
	}

}
