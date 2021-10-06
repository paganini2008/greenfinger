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
package io.atlantisframework.greenfinger;

import io.atlantisframework.vortex.common.Tuple;

/**
 * 
 * CounterCondition
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class CounterCondition extends DurationCondition {

	public CounterCondition(CrawlerStatistics crawlerStatistics, int defaultMaxFetchSize) {
		this(crawlerStatistics, 24 * 60 * 60 * 1000L, defaultMaxFetchSize);
	}

	public CounterCondition(CrawlerStatistics crawlerStatistics, long defaultDuration, int defaultMaxFetchSize) {
		super(crawlerStatistics, defaultDuration);
		this.defaultMaxFetchSize = defaultMaxFetchSize;
	}

	private final int defaultMaxFetchSize;

	private ConditionalCountingType countingType = ConditionalCountingType.URL_COUNT;

	public void setCountingType(ConditionalCountingType countingType) {
		this.countingType = countingType;
	}

	@Override
	protected boolean evaluate(long catalogId, Tuple tuple) {
		int maxFetchSize = (Integer) tuple.getField("maxFetchSize", defaultMaxFetchSize);
		return countingType.evaluate(getCrawlerStatistics().getSummary(catalogId), maxFetchSize);
	}

}
