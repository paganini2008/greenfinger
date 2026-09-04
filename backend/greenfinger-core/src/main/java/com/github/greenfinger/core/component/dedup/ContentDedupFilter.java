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

package com.github.greenfinger.core.component.dedup;

import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.component.WebCrawlerComponent;

/**
 * The second dedup pass: has this <em>text</em> been seen before, whatever url it arrived under?
 *
 * <p>
 * Url dedup cannot catch a page republished at a second address -- print views, pagination
 * parameters, mirrored sections, syndicated articles -- because the addresses genuinely differ.
 * This runs after the fetch and before the output channel, so a duplicate costs one request but
 * never reaches storage.
 * 
 * @Description: ContentDedupFilter
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public interface ContentDedupFilter extends WebCrawlerComponent, ManagedBeanLifeCycle {

    /**
     * Records the text and reports whether an equivalent document was already seen.
     *
     * @param text the extracted body text, not the html
     * @return true when this is a duplicate and should be dropped
     */
    boolean isDuplicate(String text);

    /**
     * The hash to persist on the resource row, so the decision can be audited later.
     */
    String fingerprint(String text);

    default void clean() throws Exception {}

    default long size() throws Exception {
        return -1;
    }

    /**
     * A filter that never rejects anything, used when content dedup is switched off.
     * 
     * @Description: NoOp
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    class NoOp implements ContentDedupFilter {

        @Override
        public String getName() {
            return "none";
        }

        @Override
        public boolean isDuplicate(String text) {
            return false;
        }

        @Override
        public String fingerprint(String text) {
            return null;
        }

    }

}
