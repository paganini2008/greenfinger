package indi.atlantis.framework.greenfinger;

import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * PathAcceptor
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public interface PathAcceptor {

	boolean accept(long catalogId, String refer, String path, Tuple tuple);

}
