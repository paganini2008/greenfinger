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

package com.github.greenfinger.core.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a page is fetched.
 *
 * <p>
 * It was a free-text column, which meant a typo was only discovered at the moment the crawl went to
 * build its extractor -- after the catalog had been saved, after the run had been launched, and
 * with the whole of the configuration already committed. As an enum the same typo is refused where
 * it is typed, and the accepted values can be listed to whoever is typing them.
 *
 * <p>
 * The 1.x spellings {@code default} and {@code resttemplate} are still accepted for
 * {@link #RESTCLIENT}, so a catalog carried over from the old schema still loads.
 * 
 * @Description: ExtractorType
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public enum ExtractorType {

    /** Plain http first, a browser only for the pages that came back as an unrendered shell. */
    ADAPTIVE("adaptive"),

    /** Plain http, and nothing else. The fastest, and blind to anything javascript builds. */
    RESTCLIENT("restclient"),

    /** A browser engine in the jar; nothing to install. */
    HTMLUNIT("htmlunit"),

    /** Downloads its own browsers on first use. */
    PLAYWRIGHT("playwright"),

    /** Drives a browser that must already be on the machine. */
    SELENIUM("selenium");

    private final String repr;

    ExtractorType(String repr) {
        this.repr = repr;
    }

    @JsonValue
    public String getRepr() {
        return repr;
    }

    /**
     * @return null when nothing was given, so a caller can fall back to the configured default
     *         rather than having one chosen for it here.
     */
    @JsonCreator
    public static ExtractorType of(String repr) {
        if (repr == null || repr.isBlank()) {
            return null;
        }
        String value = repr.trim().toLowerCase(Locale.ROOT);
        // the two 1.x names for the same thing
        if ("default".equals(value) || "resttemplate".equals(value)) {
            return RESTCLIENT;
        }
        return Arrays.stream(values()).filter(t -> t.repr.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown extractor '" + repr + "'. Use one of: " + choices()));
    }

    /** The accepted values, for a message that has to tell somebody what to type. */
    public static String choices() {
        return Arrays.stream(values()).map(ExtractorType::getRepr)
                .collect(Collectors.joining(", "));
    }

}
