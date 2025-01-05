package com.github.greenfinger.jdbc;

import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_CAT_SELECT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_DELETE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INSERT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ALL;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ONE;
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
import com.github.greenfinger.api.CatalogInfo;
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
    PageReader<CatalogInfo> pageForCatalog(@Sql String whereClause, @Example Catalog example);

    @Query(value = SQL_CATALOG_CAT_SELECT, singleColumn = true)
    List<String> selectAllCats();

}
