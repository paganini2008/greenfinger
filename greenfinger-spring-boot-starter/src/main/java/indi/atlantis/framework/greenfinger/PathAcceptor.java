package indi.atlantis.framework.greenfinger;

import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * PathAcceptor
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public interface PathAcceptor {

	boolean accept(long catalogId, String refer, String path, Tuple tuple);

}
