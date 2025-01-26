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
