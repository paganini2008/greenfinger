package com.github.greenfinger.jdbc;

import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_DELETE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_INSERT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_MAX_VERSION;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_SELECT_ONE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_UPDATE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_INCREMENT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_SELECT;
import java.util.Date;
import com.github.doodler.common.jdbc.annotations.Arg;
import com.github.doodler.common.jdbc.annotations.Dao;
import com.github.doodler.common.jdbc.annotations.Example;
import com.github.doodler.common.jdbc.annotations.Get;
import com.github.doodler.common.jdbc.annotations.Update;
import com.github.greenfinger.model.CatalogIndex;

/**
 * 
 * @Description: CatalogIndexDao
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
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
    int incrementCatalogIndexVersion(@Arg("lastModified") Date lastModified,
            @Arg("catalogId") long catalogId);

    @Get(value = SQL_CATALOG_INDEX_MAX_VERSION, javaType = true)
    int maximunVersionOfCatalogIndex(@Arg("cat") String cat);

}
