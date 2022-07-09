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
package io.atlantisframework.greenfinger.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.atlantisframework.chaconne.JobKey;
import io.atlantisframework.chaconne.JobManager;
import io.atlantisframework.chaconne.utils.GenericJobDefinition;
import io.atlantisframework.chaconne.utils.GenericTrigger;

/**
 * 
 * CatalogIndexJobService
 *
 * @author Fred Feng
 *
 * @since 2.0.2
 */
public class CatalogIndexJobService {

	@Value("${spring.application.cluster.name}")
	private String clusterName;

	@Value("${spring.application.name}")
	private String applicationName;

	@Autowired
	private JobManager jobManager;

	public int createIndexJob(CatalogIndexJobInfo jobInfo) throws Exception {
		final JobKey jobKey = JobKey.by(clusterName, applicationName, "CATALOG_INDEX_JOB_" + jobInfo.getCatalogId(),
				CatalogIndexJob.class.getName());
		GenericJobDefinition.Builder builder = GenericJobDefinition.newJob(jobKey).setDescription(jobInfo.getDescription())
				.setEmail(jobInfo.getEmail());
		GenericTrigger.Builder triggerBuilder = GenericTrigger.Builder.newTrigger(jobInfo.getCronExpression());
		builder.setTrigger(triggerBuilder.build());
		GenericJobDefinition jobDefinition = builder.build();
		return jobManager.persistJob(jobDefinition, String.valueOf(jobInfo.getCatalogId()));
	}

	public int createIndexUpgradeJob(CatalogIndexJobInfo jobInfo) throws Exception {
		final JobKey jobKey = JobKey.by(clusterName, applicationName, "CATALOG_INDEX_UPGRADE_JOB_" + jobInfo.getCatalogId(),
				CatalogIndexUpgradeJob.class.getName());
		GenericJobDefinition.Builder builder = GenericJobDefinition.newJob(jobKey).setDescription(jobInfo.getDescription())
				.setEmail(jobInfo.getEmail());
		GenericTrigger.Builder triggerBuilder = GenericTrigger.Builder.newTrigger(jobInfo.getCronExpression());
		builder.setTrigger(triggerBuilder.build());
		GenericJobDefinition jobDefinition = builder.build();
		return jobManager.persistJob(jobDefinition, String.valueOf(jobInfo.getCatalogId()));
	}

}
