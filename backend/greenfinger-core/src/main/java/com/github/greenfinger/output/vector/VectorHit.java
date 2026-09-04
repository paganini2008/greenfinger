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

package com.github.greenfinger.output.vector;

import java.util.Map;

/**
 * One neighbour, with the payload that makes it presentable without a database lookup.
 * 
 * @Description: VectorHit
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public record VectorHit(String id, double score, Map<String, Object> payload) {

    /**
     * Anchor text over total text: near one for a listing, near zero for an article.
     *
     * <p>
     * The two numbers travel in the payload precisely so this can be computed at query time. A
     * vector store has no equivalent of Elasticsearch's function_score, so the same preference for
     * detail pages has to be applied by re-ranking what came back.
     */
    public double linkDensity() {
        double text = number("textLength");
        double anchor = number("linkTextLength");
        if (text <= 0) {
            return 0.5;
        }
        return Math.min(1.0, anchor / text);
    }

    private double number(String key) {
        Object value = payload != null ? payload.get(key) : null;
        return value instanceof Number n ? n.doubleValue() : 0d;
    }

    public String text(String key) {
        Object value = payload != null ? payload.get(key) : null;
        return value != null ? String.valueOf(value) : null;
    }

}
