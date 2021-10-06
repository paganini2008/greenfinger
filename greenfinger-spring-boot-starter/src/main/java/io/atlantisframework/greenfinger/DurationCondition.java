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

import java.util.Date;

import io.atlantisframework.greenfinger.CrawlerStatistics.Summary;
import io.atlantisframework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * DurationCondition
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Slf4j
public class DurationCondition extends AbstractCondition {

	public DurationCondition(CrawlerStatistics crawlerStatistics, long defaultDuration) {
		super(crawlerStatistics);
		this.defaultDuration = defaultDuration;
	}

	private final long defaultDuration;

	@Override
	public boolean mightComplete(long catalogId, Tuple tuple) {
		if (isCompleted(catalogId, tuple)) {
			return true;
		}
		long duration = (Long) tuple.getField("duration", defaultDuration);
		Summary summary = getCrawlerStatistics().getSummary(catalogId);
		long elapsed = summary.getElapsedTime();
		set(catalogId, elapsed > duration || evaluate(catalogId, tuple));
		if (summary.isCompleted()) {
			log.info("Finish crawling work on deadline: {}", new Date());
			afterCompletion(catalogId, tuple);
		}
		return isCompleted(catalogId, tuple);
	}

	protected boolean evaluate(long catalogId, Tuple tuple) {
		return false;
	}

	protected void afterCompletion(long catalogId, Tuple tuple) {
	}

}
