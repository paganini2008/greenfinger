package indi.atlantis.framework.greenfinger;

import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * Condition
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public interface Condition {

	void reset(long catalogId);

	boolean mightComplete(long catalogId, Tuple tuple);

	boolean isCompleted(long catalogId, Tuple tuple);

}
