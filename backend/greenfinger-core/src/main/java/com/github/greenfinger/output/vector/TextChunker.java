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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Splits a page into passages for embedding.
 *
 * <p>
 * Embedding a whole page produces one vector that averages everything on it, which is precisely
 * what makes a long page unfindable: the one paragraph that answers a question is diluted by the
 * thousand words around it. Chunks overlap so a passage split across a boundary still appears whole
 * in one of them.
 * 
 * @Description: TextChunker
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class TextChunker {

    private final int chunkSize;
    private final int overlap;
    private final int maxChunks;

    public TextChunker(int chunkSize, int overlap, int maxChunks) {
        this.chunkSize = Math.max(100, chunkSize);
        this.overlap = Math.max(0, Math.min(overlap, this.chunkSize / 2));
        this.maxChunks = maxChunks;
    }

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return chunks;
        }
        String value = text.strip();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(value.length(), start + chunkSize);
            if (end < value.length()) {
                // prefer to break where a sentence does, so a chunk reads as a passage
                int boundary = lastBoundary(value, start, end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String chunk = value.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (maxChunks >= 0 && chunks.size() >= maxChunks) {
                break;
            }
            if (end >= value.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private int lastBoundary(String text, int start, int end) {
        // both western and CJK sentence enders, plus a paragraph break
        String enders = ".!?\u3002\uff01\uff1f\n";
        for (int i = end - 1; i > start + chunkSize / 2; i--) {
            if (enders.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }

}
