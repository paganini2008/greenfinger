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
package io.atlantisframework.greenfinger.api;

import java.util.Date;

import com.github.paganini2008.devtools.time.Duration;

import io.atlantisframework.greenfinger.CrawlerStatistics.Summary;
import io.atlantisframework.greenfinger.model.Catalog;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * CatalogSummary
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
@Getter
@Setter
public class CatalogSummary {

	private final Catalog catalog;

	public CatalogSummary(Catalog catalog, Summary summary) {
		this.catalog = catalog;
		this.startTime = new Date(summary.getStartTime());
		this.completed = summary.isCompleted();
		this.urlCount = summary.getUrlCount();
		this.existedUrlCount = summary.getExistedUrlCount();
		this.filteredUrlCount = summary.getFilteredUrlCount();
		this.invalidUrlCount = summary.getInvalidUrlCount();
		this.savedCount = summary.getSavedCount();
		this.indexedCount = summary.getIndexedCount();
		this.elapsedTime = completed ? "-" : Duration.HOUR.format(summary.getElapsedTime());
	}

	private Date startTime;
	private boolean completed;
	private long urlCount;
	private long existedUrlCount;
	private long filteredUrlCount;
	private long invalidUrlCount;
	private long savedCount;
	private long indexedCount;
	private String elapsedTime;

}
