package indi.atlantis.framework.greenfinger;

/**
 * 
 * PathFilterFactory
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public interface PathFilterFactory {

	void clean(long catalogId);

	PathFilter getPathFilter(long catalogId);

}
