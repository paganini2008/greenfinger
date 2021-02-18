package indi.atlantis.framework.greenfinger.jdbc;

import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_DELETE_ALL;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_INSERT;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_LATEST_PATH;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_FOR_INDEX;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_SELECT_ONE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_RESOURCE_VERSION_UPDATE;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;

import indi.atlantis.framework.greenfinger.model.Resource;
import indi.atlantis.framework.jdbc.annotations.Arg;
import indi.atlantis.framework.jdbc.annotations.Dao;
import indi.atlantis.framework.jdbc.annotations.Example;
import indi.atlantis.framework.jdbc.annotations.Get;
import indi.atlantis.framework.jdbc.annotations.Query;
import indi.atlantis.framework.jdbc.annotations.Update;

/**
 * 
 * ResourceDao
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Dao
public interface ResourceDao {

	@Update(SQL_RESOURCE_INSERT)
	int saveResource(@Example Resource resource);

	@Get(SQL_RESOURCE_SELECT_ONE)
	Resource getResource(@Arg("id") long id);

	@Query(SQL_RESOURCE_SELECT_FOR_INDEX)
	ResultSetSlice<Resource> queryForResourceForIndex(@Arg("catalogId") long catalogId);

	@Update(SQL_RESOURCE_VERSION_UPDATE)
	int updateResourceVersion(@Arg("catalogId") long catalogId, @Arg("version") int version);

	@Update(SQL_RESOURCE_DELETE_ALL)
	int deleteResourceByCatalogId(@Arg("catalogId") long catalogId);

	@Get(value = SQL_RESOURCE_LATEST_PATH, javaType = true)
	String getLatestPath(@Arg("catalogId") long catalogId);

}
