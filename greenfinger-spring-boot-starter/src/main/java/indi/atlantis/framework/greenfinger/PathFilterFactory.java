package indi.atlantis.framework.greenfinger;

/**
 * 
 * PathFilterFactory
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public interface PathFilterFactory {

	void clean(long catalogId);

	PathFilter getPathFilter(long catalogId);

}
