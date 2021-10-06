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
package io.atlantisframework.greenfinger.jdbc;

import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_DELETE;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_INSERT;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_MAX_VERSION;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_SELECT_ONE;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_UPDATE;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_INCREMENT;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_SELECT;

import java.util.Date;

import com.github.paganini2008.springdesert.fastjdbc.annotations.Arg;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Dao;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Example;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Get;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Update;

import io.atlantisframework.greenfinger.model.CatalogIndex;

/**
 * 
 * CatalogIndexDao
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Dao
public interface CatalogIndexDao {

	@Update(SQL_CATALOG_INDEX_INSERT)
	int saveCatalogIndex(@Example CatalogIndex catalogIndex);

	@Update(SQL_CATALOG_INDEX_UPDATE)
	int updateCatalogIndex(@Example CatalogIndex catalogIndex);

	@Update(SQL_CATALOG_INDEX_DELETE)
	int deleteCatalogIndex(@Arg("catalogId") long catalogId);

	@Get(SQL_CATALOG_INDEX_SELECT_ONE)
	CatalogIndex getCatalogIndex(@Arg("catalogId") long catalogId);

	@Get(value = SQL_CATALOG_INDEX_VERSION_SELECT, javaType = true)
	int getCatalogIndexVersion(@Arg("catalogId") long catalogId);

	@Update(SQL_CATALOG_INDEX_VERSION_INCREMENT)
	int incrementCatalogIndexVersion(@Arg("lastModified") Date lastModified, @Arg("catalogId") long catalogId);

	@Get(value = SQL_CATALOG_INDEX_MAX_VERSION, javaType = true)
	int maximunVersionOfCatalogIndex(@Arg("cat") String cat);

}
