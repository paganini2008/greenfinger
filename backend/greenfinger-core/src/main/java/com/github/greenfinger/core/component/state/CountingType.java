/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.core.component.state;

import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which counter {@code maxFetchSize} is measured against. Carried over unchanged from the legacy
 * edition, including the stored ordinal values, so existing catalog rows keep their meaning.
 * 
 * @Description: CountingType
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public enum CountingType {

    URL_TOTAL_COUNT(0, "urlTotalCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getTotalUrlCount();
        }
    },

    INVALID_URL_COUNT(1, "invalidUrlCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getInvalidUrlCount();
        }
    },

    EXISTING_URL_COUNT(2, "existingUrlCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getExistingUrlCount();
        }
    },

    FILTERED_URL_COUNT(3, "filteredUrlCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getFilteredUrlCount();
        }
    },

    SAVED_RESOURCE_COUNT(4, "savedResourceCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getSavedResourceCount();
        }
    },

    INDEXED_RESOURCE_COUNT(5, "indexedResourceCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getIndexedResourceCount();
        }
    },

    /**
     * New in 2.0: images pulled down and handed to the blob store.
     */
    SAVED_IMAGE_COUNT(6, "savedImageCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getSavedImageCount();
        }
    },

    /**
     * New in 2.0: pages dropped because their content hash was already seen.
     */
    DUPLICATED_CONTENT_COUNT(7, "duplicatedContentCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getDuplicatedContentCount();
        }
    },

    /**
     * Urls that were taken off a frontier and carried to a conclusion, whatever that conclusion
     * was -- saved, unchanged, unreachable, unparseable. Together with
     * {@link #URL_TOTAL_COUNT}, which counts urls the moment they are dispatched, it is what
     * makes the end of a distributed crawl decidable: every url that exists has been counted
     * once on dispatch, and once more when somebody finished with it, so the two being equal
     * means nothing is left anywhere. A url still queued or still being fetched sits in the
     * difference.
     */
    HANDLED_URL_COUNT(8, "handledUrlCount") {

        @Override
        public long getValue(Dashboard data) {
            return data.getHandledUrlCount();
        }
    };

    private final int value;
    private final String repr;

    CountingType(int value, String repr) {
        this.value = value;
        this.repr = repr;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    public String getRepr() {
        return repr;
    }

    public abstract long getValue(Dashboard data);

    public boolean compare(Dashboard data, long maxFetchSize) {
        return maxFetchSize > 0 && getValue(data) > maxFetchSize;
    }

    @JsonCreator
    public static CountingType valueOf(int value) {
        return Arrays.stream(values()).filter(t -> t.value == value).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown CountingType: " + value));
    }

}
