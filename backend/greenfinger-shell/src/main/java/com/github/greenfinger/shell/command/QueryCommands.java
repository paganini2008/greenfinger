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

package com.github.greenfinger.shell.command;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import com.github.greenfinger.shell.UsageException;
import com.github.greenfinger.shell.render.Ansi;
import com.github.greenfinger.shell.render.TextTable;
import com.github.greenfinger.service.ops.GreenfingerOperations;
import com.github.greenfinger.service.ops.GreenfingerOperations.CountRow;
import com.github.greenfinger.service.ops.GreenfingerOperations.Hit;
import com.github.greenfinger.service.ops.GreenfingerOperations.Info;
import com.github.greenfinger.service.ops.GreenfingerOperations.InfoRow;
import com.github.greenfinger.service.ops.GreenfingerOperations.SearchAnswer;
import com.github.greenfinger.service.ops.GreenfingerOperations.SearchAsk;
import lombok.RequiredArgsConstructor;

/**
 * Searching what was crawled.
 *
 * <p>
 * The three questions the page asks: words to the index, meaning to the text vectors, pictures to
 * the image vectors. Every table is titled with the mode that produced it, because the same query
 * under two modes is two different results and a reader has to know which one they have.
 *
 * <p>
 * An empty query lists everything, in every mode, exactly as the empty box on the page does.
 *
 * @Description: QueryCommands
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Component
@RequiredArgsConstructor
public class QueryCommands {

    private final GreenfingerOperations ops;

    @Command(name = "search", group = "Search", description = "Search crawled pages")
    public void search(
            @Option(longName = "query",
                    description = "Words to look for; leave it out to list everything") String query,
            @Option(longName = "id",
                    description = "A catalog id to search within; omit for all of them")
                    String id,
            @Option(longName = "size",
                    description = "How many results, 1 or more; default 10") Integer size,
            @Option(longName = "mode",
                    description = "words | meaning | pictures. Default words") String mode,
            @Option(longName = "image",
                    description = "Deprecated; the same as --mode=pictures") Boolean image)
            throws Exception {
        String wanted = StringUtils.isNotBlank(mode) ? mode.trim().toLowerCase()
                : Boolean.TRUE.equals(image) ? "pictures" : "words";
        if (!List.of("words", "meaning", "pictures").contains(wanted)) {
            throw new UsageException(
                    "No such mode: '" + wanted + "'. Use words, meaning or pictures.");
        }
        SearchAnswer answer = ops.search(new SearchAsk(query, id, size, wanted));
        if (answer == null) {
            print(Ansi.dim("Nothing has finished crawling yet."));
            return;
        }
        if (answer.hits().isEmpty()) {
            print(Ansi.dim(StringUtils.isBlank(query) ? "Nothing is stored yet."
                    : "No matches for '" + query + "'"));
            return;
        }
        print(table(answer).render());
    }

    /**
     * The score column is dropped when nothing was compared: a listing carries zero on every row,
     * and a column of 0.0000 invites somebody to wonder what they did wrong.
     */
    private TextTable table(SearchAnswer answer) {
        TextTable table;
        if (answer.ranked()) {
            table = answer.images()
                    ? TextTable.of("Score", "Image", "From page").maxWidth(1, 46).maxWidth(2, 46)
                    : TextTable.of("Score", "Title", "Url").maxWidth(1, 42).maxWidth(2, 46);
        } else {
            table = answer.images()
                    ? TextTable.of("Image", "From page").maxWidth(0, 52).maxWidth(1, 52)
                    : TextTable.of("Title", "Url").maxWidth(0, 48).maxWidth(1, 52);
        }
        table.title(answer.title());
        for (Hit hit : answer.hits()) {
            String first = answer.images() ? hit.first() : Ansi.cyan(hit.first());
            if (answer.ranked()) {
                table.row(String.format("%.4f", hit.score()), first, hit.second());
            } else {
                table.row(first, hit.second());
            }
        }
        return table;
    }

    /**
     * The full text index: where it is, what it is called, and how many documents each version put
     * in it. One command, because "how many" and "which exist" are the same question at two zoom
     * levels -- answered apart, a count of zero looks like an empty index rather than the wrong
     * one.
     */
    @Command(name = "index-info", group = "Search",
            description = "The full text index: where it is, and what is in it")
    public void indexInfo() throws Exception {
        Info info = ops.indexInfo();
        print(about(info, "Index").render());
        if (info.counts().isEmpty()) {
            print(Ansi.dim("Nothing has been indexed yet. A crawl whose output-types include"
                    + " 'index' creates an index of its own."));
            return;
        }
        print(counts(info, "Documents", "Index").render());
        TextTable indices = TextTable.of("Index").title("Every index under the prefix");
        info.names().forEach(indices::row);
        print(indices.render());
    }

    /**
     * The vector store: which one, which collections, and how many points each version put in
     * them.
     */
    @Command(name = "vector-info", group = "Search",
            description = "The vector store: where it is, and what is in it")
    public void vectorInfo() throws Exception {
        Info info = ops.vectorInfo();
        print(about(info, "Vector store").render());
        if (info.counts().isEmpty()) {
            print(Ansi.dim("Nothing has been embedded yet. A crawl whose output-types include"
                    + " 'vector' fills these."));
            return;
        }
        print(counts(info, "Points", "Collection").render());
    }

    private TextTable about(Info info, String title) {
        TextTable table = TextTable.of("Setting", "Value").maxWidth(1, 70).title(title);
        for (InfoRow row : info.about()) {
            table.row(row.name(), row.value());
        }
        return table;
    }

    private TextTable counts(Info info, String title, String where) {
        TextTable table = TextTable.of("Id", "Catalog", "Version", where, title).rightAlign(4)
                .title(title);
        for (CountRow row : info.counts()) {
            table.row(Ansi.cyan(row.catalogId()), row.catalog(), "v" + row.version(), row.where(),
                    row.count());
        }
        return table;
    }

    private void print(String text) {
        System.out.println(text);
    }

}
