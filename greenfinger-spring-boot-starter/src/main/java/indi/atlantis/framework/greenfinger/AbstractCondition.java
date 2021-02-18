package indi.atlantis.framework.greenfinger;

import indi.atlantis.framework.vortex.common.Tuple;

/**
 * 
 * AbstractCondition
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public abstract class AbstractCondition implements Condition {

	private final CrawlerSummary crawlerSummary;

	protected AbstractCondition(CrawlerSummary crawlerSummary) {
		this.crawlerSummary = crawlerSummary;
	}

	@Override
	public void reset(long catalogId) {
		crawlerSummary.reset(catalogId);
	}

	protected void set(long catalogId, boolean completed) {
		crawlerSummary.getSummary(catalogId).setCompleted(completed);
	}

	@Override
	public boolean isCompleted(long catalogId, Tuple tuple) {
		return crawlerSummary.getSummary(catalogId).isCompleted();
	}

	public CrawlerSummary getCrawlerSummary() {
		return crawlerSummary;
	}

}
