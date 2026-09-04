/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.jdbc;

import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_CAT_SELECT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_DELETE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INSERT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ALL;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ONE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_RUNNING;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SET_RUNNING_STATE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_UPDATE;
import java.util.List;
import com.github.doodler.common.jdbc.annotations.Arg;
import com.github.doodler.common.jdbc.annotations.Dao;
import com.github.doodler.common.jdbc.annotations.Example;
import com.github.doodler.common.jdbc.annotations.Get;
import com.github.doodler.common.jdbc.annotations.PageQuery;
import com.github.doodler.common.jdbc.annotations.Query;
import com.github.doodler.common.jdbc.annotations.Sql;
import com.github.doodler.common.jdbc.annotations.Update;
import com.github.doodler.common.page.PageReader;
import com.github.doodler.common.page.PageVo;
import com.github.greenfinger.api.pojo.CatalogInfo;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CatalogDao
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
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

    @PageQuery(SQL_CATALOG_SELECT_ALL)
    PageVo<CatalogInfo> pageForCatalog(@Sql String whereClause, @Example Catalog example,
            @Arg("page") int page, @Arg("pageSize") int pageSize);

    @PageQuery(SQL_CATALOG_SELECT_ALL)
    PageReader<CatalogInfo> pageForCatalog(@Sql String whereClause, @Example Catalog example);

    @Query(value = SQL_CATALOG_CAT_SELECT, singleColumn = true)
    List<String> selectAllCats();

    @Update(SQL_CATALOG_SET_RUNNING_STATE)
    int setRunningState(@Arg("catalogId") long catalogId, @Arg("runningState") String runningState);

    @Query(value = SQL_CATALOG_SELECT_RUNNING)
    List<Catalog> selectRunningCatalogs();

}
