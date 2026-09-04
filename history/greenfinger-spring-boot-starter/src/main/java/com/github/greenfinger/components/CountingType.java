/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.components;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.doodler.common.enums.EnumConstant;
import com.github.doodler.common.enums.EnumUtils;

/**
 * 
 * @Description: CountingType
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public enum CountingType implements EnumConstant {

    URL_TOTAL_COUNT(0, "urlTotalCount") {

        @Override
        public boolean compare(Dashboard data, long maxFetchSize) {
            return data.getTotalUrlCount() > maxFetchSize;
        }

        @Override
        public long getValue(Dashboard data) {
            return data.getTotalUrlCount();
        }

    },

    INVALID_URL_COUNT(1, "invalidUrlCount") {


        @Override
        public boolean compare(Dashboard data, long maxFetchSize) {
            return data.getInvalidUrlCount() > maxFetchSize;
        }

        @Override
        public long getValue(Dashboard data) {
            return data.getInvalidUrlCount();
        }
    },

    EXISTING_URL_COUNT(2, "existingUrlCount") {


        @Override
        public boolean compare(Dashboard data, long maxFetchSize) {
            return data.getExistingUrlCount() > maxFetchSize;
        }

        @Override
        public long getValue(Dashboard data) {
            return data.getExistingUrlCount();
        }
    },

    FILTERED_URL_COUNT(3, "filteredUrlCount") {


        @Override
        public boolean compare(Dashboard data, long maxFetchSize) {
            return data.getFilteredUrlCount() > maxFetchSize;
        }

        @Override
        public long getValue(Dashboard data) {
            return data.getFilteredUrlCount();
        }
    },

    SAVED_RESOURCE_COUNT(4, "savedResourceCount") {

        @Override
        public boolean compare(Dashboard data, long maxFetchSize) {
            return data.getSavedResourceCount() > maxFetchSize;
        }

        @Override
        public long getValue(Dashboard data) {
            return data.getSavedResourceCount();
        }
    },

    INDEXED_RESOURCE_COUNT(5, "indexedResourceCount") {

        @Override
        public boolean compare(Dashboard data, long maxFetchSize) {
            return data.getIndexedResourceCount() > maxFetchSize;
        }

        @Override
        public long getValue(Dashboard data) {
            return data.getIndexedResourceCount();
        }


    };

    private CountingType(int value, String repr) {
        this.value = value;
        this.repr = repr;
    }

    private final int value;
    private final String repr;

    @JsonValue
    public Object getValue() {
        return value;
    }

    public String getRepr() {
        return repr;
    }

    @JsonCreator
    public static CountingType valueOf(int value) {
        return EnumUtils.valueOf(CountingType.class, value);
    }

    public abstract boolean compare(Dashboard data, long maxFetchSize);

    public abstract long getValue(Dashboard data);

}
