package org.springtribe.framework.greenfinger;

import org.springtribe.framework.gearless.common.Tuple;

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
