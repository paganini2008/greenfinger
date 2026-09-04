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

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: TextChunkerTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class TextChunkerTest {

    @Test
    void shortTextStaysOneChunk() {
        assertThat(new TextChunker(1000, 200, 20).split("A short page.")).hasSize(1);
    }

    @Test
    @DisplayName("a long page is split, because one vector for the whole page averages it away")
    void longTextIsSplit() {
        String text = ("Sentence number one is here. ").repeat(80);
        List<String> chunks = new TextChunker(300, 50, 20).split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }

    @Test
    @DisplayName("chunks overlap so a passage split at a boundary survives whole in one of them")
    void chunksOverlap() {
        String text = ("word ").repeat(400);
        List<String> chunks = new TextChunker(200, 100, 20).split(text);
        assertThat(chunks).hasSizeGreaterThan(1);

        int totalLength = chunks.stream().mapToInt(String::length).sum();
        assertThat(totalLength).isGreaterThan(text.strip().length());
    }

    @Test
    @DisplayName("a break is preferred where a sentence ends, so a chunk reads as a passage")
    void breaksOnSentenceBoundaries() {
        String text = "First sentence here. Second sentence follows on. Third one closes it out. "
                + "Fourth sentence continues the paragraph for a while longer than the rest.";
        List<String> chunks = new TextChunker(100, 10, 20).split(text);
        assertThat(chunks.get(0)).endsWith(".");
    }

    @Test
    void handlesCjkSentenceEnders() {
        String text = ("\u8fd9\u662f\u4e00\u4e2a\u4e2d\u6587\u53e5\u5b50\u3002").repeat(60);
        assertThat(new TextChunker(120, 20, 20).split(text)).hasSizeGreaterThan(1);
    }

    @Test
    void capsTheNumberOfChunks() {
        String text = ("word ").repeat(2000);
        assertThat(new TextChunker(100, 10, 3).split(text)).hasSize(3);
    }

    @Test
    void negativeCapMeansUnlimited() {
        String text = ("word ").repeat(500);
        assertThat(new TextChunker(100, 10, -1).split(text)).hasSizeGreaterThan(5);
    }

    @Test
    void emptyTextGivesNoChunks() {
        assertThat(new TextChunker(1000, 200, 20).split("")).isEmpty();
        assertThat(new TextChunker(1000, 200, 20).split(null)).isEmpty();
        assertThat(new TextChunker(1000, 200, 20).split("   ")).isEmpty();
    }

    @Test
    void guardsAgainstAbsurdConfiguration() {
        // an overlap at or beyond the chunk size would never advance
        assertThat(new TextChunker(10, 9999, 20).split(("word ").repeat(100)))
                .hasSizeGreaterThan(1);
    }

}
