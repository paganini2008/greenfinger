package com.github.greenfinger.jdbc;

import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_DELETE_ALL;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_INSERT;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_LATEST_PATH;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_FOR_INDEX;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_ONE;
import static com.github.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_VERSION_UPDATE;
import com.github.doodler.common.jdbc.annotations.Arg;
import com.github.doodler.common.jdbc.annotations.Dao;
import com.github.doodler.common.jdbc.annotations.Example;
import com.github.doodler.common.jdbc.annotations.Get;
import com.github.doodler.common.jdbc.annotations.PageQuery;
import com.github.doodler.common.jdbc.annotations.Update;
import com.github.doodler.common.jdbc.page.PageReader;
import com.github.greenfinger.model.Resource;

/**
 * 
 * @Description: ResourceDao
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Dao
public interface ResourceDao {

    @Update(SQL_RESOURCE_INSERT)
    int saveResource(@Example Resource resource);

    @Get(SQL_RESOURCE_SELECT_ONE)
    Resource getResource(@Arg("id") long id);

    @PageQuery(SQL_RESOURCE_SELECT_FOR_INDEX)
    PageReader<Resource> pageForResourceForIndex(@Arg("catalogId") long catalogId);

    @Update(SQL_RESOURCE_VERSION_UPDATE)
    int updateResourceVersion(@Arg("catalogId") long catalogId, @Arg("version") int version);

    @Update(SQL_RESOURCE_DELETE_ALL)
    int deleteResourceByCatalogId(@Arg("catalogId") long catalogId);

    @Get(value = SQL_RESOURCE_LATEST_PATH, javaType = true)
    String getLatestPath(@Arg("catalogId") long catalogId);

}
