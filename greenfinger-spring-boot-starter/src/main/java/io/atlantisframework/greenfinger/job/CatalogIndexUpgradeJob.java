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
package io.atlantisframework.greenfinger.job;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import io.atlantisframework.chaconne.JobKey;
import io.atlantisframework.chaconne.NotManagedJob;
import io.atlantisframework.greenfinger.CrawlerStatistics;
import io.atlantisframework.greenfinger.es.ResourceIndexService;

/**
 * 
 * CatalogIndexUpgradeJob
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
public class CatalogIndexUpgradeJob implements NotManagedJob {

	@Autowired
	private ResourceIndexService resourceIndexService;

	@Autowired
	private CrawlerStatistics crawlerStatistics;

	@Override
	public Object execute(JobKey jobKey, Object attachment, Logger log) throws Exception {
		final Long catalogId = Long.valueOf(attachment.toString());
		if (!crawlerStatistics.getSummary(catalogId).isCompleted()) {
			log.warn("Crawler '{}' is running now.", catalogId);
			return null;
		}
		log.info("Start index catalog: ", catalogId);
		resourceIndexService.upgradeCatalogIndex(catalogId);
		log.info("Upgrade catalog '{}' successfully.", catalogId);
		return null;
	}

}
