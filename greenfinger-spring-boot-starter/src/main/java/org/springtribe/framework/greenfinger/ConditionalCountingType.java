package org.springtribe.framework.greenfinger;

import org.springtribe.framework.greenfinger.CrawlerSummary.Summary;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.paganini2008.devtools.enums.EnumConstant;
import com.github.paganini2008.devtools.enums.EnumUtils;

/**
 * 
 * ConditionalCountingType
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public enum ConditionalCountingType implements EnumConstant {

	URL_COUNT(0, "url_count") {

		@Override
		public boolean evaluate(Summary summary, long maxFetchSize) {
			return summary.getUrlCount() > maxFetchSize;
		}

	},

	INVALID_URL_COUNT(1, "invalid_url_count") {
		@Override
		public boolean evaluate(Summary summary, long maxFetchSize) {
			return summary.getInvalidUrlCount() > maxFetchSize;
		}
	},

	EXISTED_URL_COUNT(2, "existed_url_count") {
		@Override
		public boolean evaluate(Summary summary, long maxFetchSize) {
			return summary.getExistedUrlCount() > maxFetchSize;
		}
	},

	FILTERED_URL_COUNT(3, "filtered_url_count") {
		@Override
		public boolean evaluate(Summary summary, long maxFetchSize) {
			return summary.getFilteredUrlCount() > maxFetchSize;
		}
	},

	SAVED_COUNT(4, "saved_count") {
		@Override
		public boolean evaluate(Summary summary, long maxFetchSize) {
			return summary.getSavedCount() > maxFetchSize;
		}
	},

	INDEXED_COUNT(5, "indexed_count") {
		@Override
		public boolean evaluate(Summary summary, long maxFetchSize) {
			return summary.getIndexedCount() > maxFetchSize;
		}
	};

	private ConditionalCountingType(int value, String repr) {
		this.value = value;
		this.repr = repr;
	}

	private final int value;
	private final String repr;

	@JsonValue
	public int getValue() {
		return value;
	}

	public String getRepr() {
		return repr;
	}

	@JsonCreator
	public static ConditionalCountingType valueOf(int value) {
		return EnumUtils.valueOf(ConditionalCountingType.class, value);
	}

	public abstract boolean evaluate(Summary summary, long maxFetchSize);

}
