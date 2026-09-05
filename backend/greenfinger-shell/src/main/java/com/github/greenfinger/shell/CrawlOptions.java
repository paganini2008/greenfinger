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

package com.github.greenfinger.shell;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * The options one command was given, by name.
 *
 * <p>
 * Only the one-line form builds one of these: at the prompt Spring Shell binds straight to the
 * method parameters. It exists because the two forms must reach the same methods, and a map of
 * strings is the smallest thing that can carry what was typed across to them.
 *
 * <p>
 * Names are matched ignoring case, {@code -} and {@code _}, so {@code max-size}, {@code max_size}
 * and {@code maxSize} are one option. There is no longer a properties file behind this: a catalog
 * is defined by {@code catalog-save}, which asks, and the definition then lives in the database
 * where a crawl launched from anywhere reads the same one.
 * 
 * @Description: CrawlOptions
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class CrawlOptions {

    private final Map<String, String> values = new LinkedHashMap<>();

    /**
     * Sets a value unless it is blank, so an option absent from the command line never erases one
     * that came from the file.
     */
    public CrawlOptions override(String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            put(key, value);
        }
        return this;
    }

    public CrawlOptions override(String key, Object value) {
        return value != null ? override(key, String.valueOf(value)) : this;
    }

    private void put(String key, String value) {
        values.put(normalize(key), value != null ? value.trim() : null);
    }

    private String normalize(String key) {
        return key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    public String get(String key, String defaultValue) {
        String value = values.get(normalize(key));
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new WebCrawlerException(
                    "Option '" + key + "' must be a whole number but was '" + value + "'");
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new WebCrawlerException(
                    "Option '" + key + "' must be a whole number but was '" + value + "'");
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, null);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public Integer getIntegerOrNull(String key) {
        String value = get(key, null);
        return value != null ? getInt(key, 0) : null;
    }

    public Long getLongOrNull(String key) {
        String value = get(key, null);
        return value != null ? getLong(key, 0L) : null;
    }

    public Boolean getBooleanOrNull(String key) {
        String value = get(key, null);
        return value != null ? Boolean.valueOf(value) : null;
    }

    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }

}
