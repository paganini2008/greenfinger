/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package indi.atlantis.framework.greenfinger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.paganini2008.devtools.enums.EnumConstant;
import com.github.paganini2008.devtools.enums.EnumUtils;

import indi.atlantis.framework.greenfinger.CrawlerStatistics.Summary;

/**
 * 
 * ConditionalCountingType
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
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
