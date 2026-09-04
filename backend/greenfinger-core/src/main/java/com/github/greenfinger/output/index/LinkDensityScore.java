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

import java.io.IOException;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;

/**
 * Pushes detail pages above listings, by the same arithmetic the Elasticsearch script does.
 *
 * <p>
 * A listing matches a search term as readily as the page it links to, and is almost never what
 * someone wanted. The two are told apart without any classification: anchor text over total text --
 * link density, the metric boilerplate detection has used since Boilerpipe -- is near one for a
 * listing and near zero for an article, and is unaffected by how long the page happens to be, which
 * a raw link count is not.
 *
 * <p>
 * The result is a multiplier between 0.5 and 1.5, so it reorders documents of similar relevance and
 * cannot float an irrelevant page above a relevant one.
 * 
 * @Description: LinkDensityScore
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public class LinkDensityScore extends DoubleValuesSource {

    @Override
    public DoubleValues getValues(LeafReaderContext context, DoubleValues scores)
            throws IOException {
        NumericDocValues textLength =
                context.reader().getNumericDocValues(LuceneFields.TEXT_LENGTH);
        NumericDocValues linkTextLength =
                context.reader().getNumericDocValues(LuceneFields.LINK_TEXT_LENGTH);
        return new DoubleValues() {

            private double value = 1d;

            @Override
            public double doubleValue() {
                return value;
            }

            @Override
            public boolean advanceExact(int doc) throws IOException {
                double text = valueAt(textLength, doc);
                double anchor = valueAt(linkTextLength, doc);
                // a page with no measured text is neither a listing nor an article as far as this
                // can tell, and the neutral answer is the middle of the range
                value = text <= 0 ? 1d : 1.5d - Math.min(1d, anchor / text);
                return true;
            }
        };
    }

    private static long valueAt(NumericDocValues values, int doc) throws IOException {
        if (values == null) {
            return 0L;
        }
        // doc values are read in order, and a query may revisit a document it has already passed:
        // advanceExact refuses to go backwards, so an out of order request reads as absent
        if (values.docID() > doc) {
            return 0L;
        }
        return values.advanceExact(doc) ? values.longValue() : 0L;
    }

    @Override
    public boolean needsScores() {
        return false;
    }

    @Override
    public DoubleValuesSource rewrite(IndexSearcher searcher) {
        return this;
    }

    @Override
    public boolean isCacheable(LeafReaderContext context) {
        return true;
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc, Explanation scoreExplanation)
            throws IOException {
        DoubleValues values = getValues(context, null);
        return values.advanceExact(doc)
                ? Explanation.match(values.doubleValue(), "prose over links")
                : Explanation.noMatch("no link density for this document");
    }

    @Override
    public int hashCode() {
        return LinkDensityScore.class.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LinkDensityScore;
    }

    @Override
    public String toString() {
        return "linkDensity()";
    }

}
