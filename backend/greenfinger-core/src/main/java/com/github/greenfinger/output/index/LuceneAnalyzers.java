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

package com.github.greenfinger.output.index;

import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import lombok.experimental.UtilityClass;

/**
 * How text is cut into terms.
 *
 * <p>
 * {@code standard} is the default and stays the default. What is crawled is mostly English, and
 * for English the standard analyzer is not a compromise -- it is the right answer. The other two
 * exist because a crawler that cannot segment Chinese at all is a crawler with a hole in it, not
 * because Chinese is the expected case.
 *
 * <ul>
 * <li>{@code standard} tokenizes on word boundaries, which is what English wants. On CJK it falls
 * back to single characters: everything is found and nothing ranks well, because a one-character
 * term matches half the corpus.</li>
 * <li>{@code smartcn} is a real Chinese segmenter with a dictionary, and is the embedded
 * counterpart of installing IK into an Elasticsearch server. Worth turning on for a catalog whose
 * site is Chinese, and worth leaving off for one whose site is not.</li>
 * <li>{@code cjk} is bigrams: no dictionary, so nothing to fall out of date, and it covers
 * Japanese and Korean too. The middle answer for a corpus that is genuinely mixed.</li>
 * </ul>
 *
 * <p>
 * One setting for the whole node today. One index per catalog means it could be one per catalog,
 * which is what a machine crawling an English site and a Chinese one would want; that is a catalog
 * column and an interview question, and neither exists yet.
 * 
 * @Description: LuceneAnalyzers
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@UtilityClass
public class LuceneAnalyzers {

    public static final String STANDARD = "standard";
    public static final String SMARTCN = "smartcn";
    public static final String CJK = "cjk";

    /**
     * @param name one of the three above; anything else is refused rather than quietly treated as
     *        standard, since an analyzer chosen by a typo produces an index that is merely bad
     *        rather than broken, and nobody would notice for months.
     */
    public Analyzer of(String name) {
        String value = name == null ? STANDARD : name.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", STANDARD -> new StandardAnalyzer();
            case SMARTCN, "smart_chinese", "ik", "ik_max_word", "ik_smart" -> {
                // the three ik spellings map here on purpose: it is what somebody moving from an
                // Elasticsearch configuration will have written, and smartcn is what it means
                // when there is no server to install a plugin into
                yield new SmartChineseAnalyzer();
            }
            case CJK, "cjk_bigram" -> new CJKAnalyzer();
            default -> throw new IllegalArgumentException("Unknown lucene analyzer '" + name
                    + "'. Use standard, smartcn or cjk.");
        };
    }

}
