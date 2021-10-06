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

import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_CAT_SELECT;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_DELETE;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INSERT;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ALL;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ONE;
import static io.atlantisframework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_UPDATE;

import java.util.List;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Arg;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Dao;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Example;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Get;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Query;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Select;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Sql;
import com.github.paganini2008.springdesert.fastjdbc.annotations.Update;

import io.atlantisframework.greenfinger.api.CatalogInfo;
import io.atlantisframework.greenfinger.model.Catalog;

/**
 * 
 * CatalogDao
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Dao
public interface CatalogDao {

	@Update(SQL_CATALOG_INSERT)
	int saveCatalog(@Example Catalog catalog);

	@Update(SQL_CATALOG_UPDATE)
	int updateCatalog(@Example Catalog catalog);

	@Update(SQL_CATALOG_DELETE)
	int deleteCatalog(@Arg("id") long id);

	@Get(SQL_CATALOG_SELECT_ONE)
	Catalog getCatalog(@Arg("id") long id);

	@Select(SQL_CATALOG_SELECT_ALL)
	ResultSetSlice<CatalogInfo> selectForCatalog(@Sql String whereClause, @Example Catalog example);

	@Query(value = SQL_CATALOG_CAT_SELECT, singleColumn = true)
	List<String> selectAllCats();

}
