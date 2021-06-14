package indi.atlantis.framework.greenfinger.jdbc;

import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_DELETE_ALL;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_INSERT;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_LATEST_PATH;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_FOR_INDEX;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_ONE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_VERSION_UPDATE;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.springdesert.jdbc.annotations.Arg;
import com.github.paganini2008.springdesert.jdbc.annotations.Dao;
import com.github.paganini2008.springdesert.jdbc.annotations.Example;
import com.github.paganini2008.springdesert.jdbc.annotations.Get;
import com.github.paganini2008.springdesert.jdbc.annotations.Select;
import com.github.paganini2008.springdesert.jdbc.annotations.Update;

import indi.atlantis.framework.greenfinger.model.Resource;

/**
 * 
 * ResourceDao
 *
 * @author Fred Feng
 * 
 * @since 1.0
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
