package indi.atlantis.framework.greenfinger;

import java.util.Date;

import indi.atlantis.framework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * DurationCondition
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Slf4j
public class DurationCondition extends AbstractCondition {

	public DurationCondition(CrawlerSummary crawlerSummary, long defaultDuration) {
		super(crawlerSummary);
		this.defaultDuration = defaultDuration;
	}

	private final long defaultDuration;

	@Override
	public boolean mightComplete(long catalogId, Tuple tuple) {
		if (isCompleted(catalogId, tuple)) {
			return true;
		}
		long duration = (Long) tuple.getField("duration", defaultDuration);
		long elapsed = getCrawlerSummary().getSummary(catalogId).getElapsedTime();
		boolean completed = elapsed > duration || evaluate(catalogId, tuple);
		set(catalogId, completed);
		if (completed) {
			log.info("Finish crawling work on deadline: {}", new Date());
			afterCompletion(catalogId, tuple);
		}
		return isCompleted(catalogId, tuple);
	}

	protected boolean evaluate(long catalogId, Tuple tuple) {
		return false;
	}

	protected void afterCompletion(long catalogId, Tuple tuple) {
	}

}
