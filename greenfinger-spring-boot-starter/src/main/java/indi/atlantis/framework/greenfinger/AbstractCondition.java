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
 * AbstractCondition
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public abstract class AbstractCondition implements Condition {

	private final CrawlerSummary crawlerSummary;

	protected AbstractCondition(CrawlerSummary crawlerSummary) {
		this.crawlerSummary = crawlerSummary;
	}

	@Override
	public void reset(long catalogId) {
		crawlerSummary.reset(catalogId);
	}

	protected void set(long catalogId, boolean completed) {
		crawlerSummary.getSummary(catalogId).setCompleted(completed);
	}

	@Override
	public boolean isCompleted(long catalogId, Tuple tuple) {
		return crawlerSummary.getSummary(catalogId).isCompleted();
	}

	public CrawlerSummary getCrawlerSummary() {
		return crawlerSummary;
	}

}
