package indi.atlantis.framework.greenfinger;

/**
 * 
 * PathFilter
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public interface PathFilter {
	
	void update(String content);

	boolean mightExist(String content);

}
