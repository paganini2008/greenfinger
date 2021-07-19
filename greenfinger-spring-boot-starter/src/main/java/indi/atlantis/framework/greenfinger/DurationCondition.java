/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

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

import java.util.Date;

import indi.atlantis.framework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * DurationCondition
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Slf4j
public class DurationCondition extends AbstractCondition {

	public DurationCondition(CrawlerSummary crawlerSummary, long defaultDuration) {
		super(crawlerSummary);
		this.defaultDuration = defaultDuration;
	}

	private final long defaultDuration;

	@Override
	public boolean mightComplete(long catalogId, Tuple tuple) {
		if (isCompleted(catalogId, tuple)) {
			return true;
		}
		long duration = (Long) tuple.getField("duration", defaultDuration);
		long elapsed = getCrawlerSummary().getSummary(catalogId).getElapsedTime();
		boolean completed = elapsed > duration || evaluate(catalogId, tuple);
		set(catalogId, completed);
		if (completed) {
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
