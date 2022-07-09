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
package io.atlantisframework.greenfinger.jdbc;

import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_DELETE_ALL;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_INSERT;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_LATEST_PATH;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_FOR_INDEX;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_ONE;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_VERSION_UPDATE;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Arg;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Dao;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Example;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Get;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Select;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Update;

import io.atlantisframework.greenfinger.model.Resource;

/**
 * 
 * ResourceDao
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Dao
public interface ResourceDao {

	@Update(SQL_RESOURCE_INSERT)
	int saveResource(@Example Resource resource);

	@Get(SQL_RESOURCE_SELECT_ONE)
	Resource getResource(@Arg("id") long id);

	@Select(SQL_RESOURCE_SELECT_FOR_INDEX)
	ResultSetSlice<Resource> queryForResourceForIndex(@Arg("catalogId") long catalogId);

	@Update(SQL_RESOURCE_VERSION_UPDATE)
	int updateResourceVersion(@Arg("catalogId") long catalogId, @Arg("version") int version);

	@Update(SQL_RESOURCE_DELETE_ALL)
	int deleteResourceByCatalogId(@Arg("catalogId") long catalogId);

	@Get(value = SQL_RESOURCE_LATEST_PATH, javaType = true)
	String getLatestPath(@Arg("catalogId") long catalogId);

}
