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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.OutputType;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.core.output.OutputChannel;
import com.github.greenfinger.core.output.OutputPayload;
import com.github.greenfinger.core.record.ResourceRecord;
import com.github.greenfinger.core.utils.UrlUtils;
import com.github.greenfinger.output.OutputProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Full text search, embedded. The default, and the reason a fresh clone can crawl and then search
 * without anything having been installed.
 *
 * <p>
 * One index per catalog, holding every version, exactly as the Elasticsearch channel does; versions
 * are kept apart by the {@code catalogVersion} field rather than by separate indices, so a search
 * across catalogs whose current versions differ is one filter, promoting a finished version is a
 * change of the value being matched, and deleting a version is one term query.
 *
 * <p>
 * Documents are written by id with {@code updateDocument}, not added: an update re-crawls pages it
 * already has, and the id is derived from the url, so replacing is what keeps a second run from
 * doubling the index. It is also what makes a replay idempotent.
 * 
 * @Description: LuceneOutputChannel
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class LuceneOutputChannel implements OutputChannel {

    private final OutputProperties.Index config;
    private final LuceneIndexes indexes;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong written = new AtomicLong(0);
    private final AtomicLong sinceCommit = new AtomicLong(0);

    @Getter
    private String indexName;
    private CatalogDetails catalogDetails;

    /**
     * The open indices behind this channel. Exposed for the cluster edition, which writes the same
     * document into the same index and then tells the other nodes about it.
     */
    @Getter
    private final LuceneIndexes openIndexes;

    public LuceneOutputChannel(OutputProperties.Index config, LuceneIndexes indexes) {
        this.config = config;
        this.indexes = indexes;
        this.openIndexes = indexes;
    }

    @Override
    public String getName() {
        return "index";
    }

    @Override
    public OutputType getType() {
        return OutputType.INDEX;
    }

    @Override
    public void open(CatalogDetails catalogDetails) {
        this.catalogDetails = catalogDetails;
        this.indexName = IndexAdmin.indexOf(config.getPrefix(), catalogDetails.getId());
        // opening the writer here rather than on the first page, so a directory that cannot be
        // created fails at the start of the crawl instead of a thousand pages in
        indexes.writer(indexName);
        log.info("Indexing into {}/{} as {}", indexes.getRoot(), indexName,
                catalogDetails.getCatalogVersion());
    }

    @Override
    public void write(OutputPayload payload) throws Exception {
        write(indexes, indexName, fieldsOf(catalogDetails, payload, objectMapper));
        written.incrementAndGet();
        // committing on a count rather than leaving it to Lucene's memory ceiling, so a long crawl
        // watched from the search page shows progress rather than nothing until it ends
        if (sinceCommit.incrementAndGet() >= Math.max(1, config.getLucene().getCommitEvery())) {
            sinceCommit.set(0);
            indexes.commit(indexName);
        }
    }

    /**
     * One page as a plain map of field name to value.
     *
     * <p>
     * A map rather than a {@link Document} because this is also what travels between nodes: every
     * node holds a complete index, so a page indexed on one has to reach the others, and a Lucene
     * document is not something that can be put on a wire. The map is, and building the document
     * from it is {@link #write}, which is therefore the same code on both sides.
     */
    public static Map<String, Object> fieldsOf(CatalogDetails catalogDetails,
            OutputPayload payload, ObjectMapper objectMapper) throws Exception {
        ResourceRecord record = payload.getRecord();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(LuceneFields.ID, record.resource().getId());
        fields.put(LuceneFields.TITLE, record.resource().getTitle());
        fields.put(LuceneFields.CONTENT, payload.getText());
        fields.put(LuceneFields.URL, record.resource().getUrl());
        fields.put(LuceneFields.HOST, UrlUtils.getHost(record.resource().getUrl()));
        fields.put(LuceneFields.CAT, record.resource().getCat());
        fields.put(LuceneFields.CATALOG, catalogDetails.getName());
        fields.put(LuceneFields.CATALOG_ID, record.resource().getCatalogId());
        fields.put(LuceneFields.CATALOG_VERSION,
                record.resource().getCatalogId() + ":" + record.resource().getVersion());
        fields.put(LuceneFields.REFERER, record.resource().getReferer());
        fields.put(LuceneFields.CONTENT_HASH, record.resource().getContentHash());
        fields.put(LuceneFields.HTML_FILE_PATH, record.resource().getHtmlFilePath());
        fields.put(LuceneFields.HTML_CONTENT_FILE_PATH,
                record.resource().getHtmlContentFilePath());
        fields.put(LuceneFields.VERSION, record.resource().getVersion());
        fields.put(LuceneFields.DEPTH, record.resource().getDepth());
        fields.put(LuceneFields.LINK_COUNT, record.resource().getLinkCount());
        // the two ranking signals: a listing is mostly links and little prose, a detail page the
        // reverse, and search boosts on that without anything having to classify pages
        fields.put(LuceneFields.TEXT_LENGTH, record.resource().getTextLength());
        fields.put(LuceneFields.LINK_TEXT_LENGTH, record.resource().getLinkTextLength());
        fields.put(LuceneFields.CREATE_TIME, record.resource().getCreatedAt() != null
                ? record.resource().getCreatedAt().getTime()
                : System.currentTimeMillis());

        List<Map<String, Object>> images = imagesOf(payload);
        if (!images.isEmpty()) {
            fields.put(LuceneFields.IMAGES, objectMapper.writeValueAsString(images));
            fields.put(LuceneFields.IMAGE_TEXT, imageText(images));
        }
        return fields;
    }

    /**
     * Writes one page's fields into an index, replacing whatever was there under the same id.
     *
     * <p>
     * By id with {@code updateDocument}, never {@code addDocument}: an update re-crawls pages it
     * already has, and the id is derived from the url, so replacing is what keeps a second run
     * from doubling the index. It is also what makes a replay idempotent, and what lets the same
     * page arrive twice from two nodes without becoming two documents.
     */
    public static void write(LuceneIndexes indexes, String indexName, Map<String, Object> fields)
            throws Exception {
        String id = String.valueOf(fields.get(LuceneFields.ID));
        Document document = new Document();
        keyword(document, LuceneFields.ID, id);
        // the same value again, as a sort key: the cursor needs a unique tiebreaker, and a
        // postings-only field cannot be sorted on
        document.add(new SortedDocValuesField(LuceneFields.SORT_ID, new BytesRef(id)));

        for (String field : List.of(LuceneFields.TITLE, LuceneFields.CONTENT,
                LuceneFields.IMAGE_TEXT)) {
            text(document, field, string(fields, field));
        }
        for (String field : List.of(LuceneFields.URL, LuceneFields.HOST, LuceneFields.CAT,
                LuceneFields.CATALOG, LuceneFields.CATALOG_ID, LuceneFields.CATALOG_VERSION,
                LuceneFields.REFERER, LuceneFields.CONTENT_HASH, LuceneFields.HTML_FILE_PATH,
                LuceneFields.HTML_CONTENT_FILE_PATH)) {
            keyword(document, field, string(fields, field));
        }
        for (String field : List.of(LuceneFields.VERSION, LuceneFields.DEPTH,
                LuceneFields.LINK_COUNT, LuceneFields.TEXT_LENGTH,
                LuceneFields.LINK_TEXT_LENGTH)) {
            number(document, field, (int) number(fields, field));
        }
        long createTime = number(fields, LuceneFields.CREATE_TIME);
        document.add(new LongPoint(LuceneFields.CREATE_TIME, createTime));
        document.add(new StoredField(LuceneFields.CREATE_TIME, createTime));
        document.add(new NumericDocValuesField(LuceneFields.CREATE_TIME, createTime));

        String images = string(fields, LuceneFields.IMAGES);
        if (StringUtils.isNotBlank(images)) {
            document.add(new StoredField(LuceneFields.IMAGES, images));
        }

        indexes.writer(indexName).updateDocument(new Term(LuceneFields.ID, id), document);
    }

    private static String string(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static long number(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Empty when the catalog is running in text-only mode, in which case the images are still
     * crawled and stored -- turning them on later is a replay, not a re-crawl.
     */
    private static List<Map<String, Object>> imagesOf(OutputPayload payload) {
        if (!payload.getCatalogDetails().getContentMode().includesImages()) {
            return List.of();
        }
        List<Map<String, Object>> images = new ArrayList<>();
        for (ResourceRecord.ImageRecord image : payload.getRecord().images()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("imageId", image.image().getId());
            one.put("imageFilePath", image.image().getImageFilePath());
            one.put("contentType", image.image().getContentType());
            one.put("sourceUrl", image.reference().getSourceUrl());
            one.put("width", image.image().getWidth());
            one.put("height", image.image().getHeight());
            one.put("alt", image.reference().getAltText());
            one.put("title", image.reference().getTitleText());
            one.put("context", image.reference().getContextText());
            images.add(one);
        }
        return images;
    }

    /**
     * What the pictures are described as, flattened. Elasticsearch indexes these inside a nested
     * document; here they are one more field on the page, which loses the ability to say which
     * picture matched and keeps the ability to find the page at all.
     */
    private static String imageText(List<Map<String, Object>> images) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> image : images) {
            for (String key : List.of("alt", "title", "context")) {
                Object value = image.get(key);
                if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                    text.append(value).append(' ');
                }
            }
        }
        return text.toString();
    }

    private static void keyword(Document document, String field, String value) {
        if (StringUtils.isNotBlank(value)) {
            document.add(new StringField(field, value, Field.Store.YES));
        }
    }

    private static void text(Document document, String field, String value) {
        if (StringUtils.isNotBlank(value)) {
            document.add(new TextField(field, value, Field.Store.YES));
        }
    }

    private static void number(Document document, String field, Integer value) {
        int number = value != null ? value : 0;
        document.add(new IntPoint(field, number));
        document.add(new StoredField(field, number));
        document.add(new NumericDocValuesField(field, number));
    }

    @Override
    public void flush() throws Exception {
        sinceCommit.set(0);
        indexes.commit(indexName);
    }

    @Override
    public void close() throws Exception {
        flush();
        log.info("Indexed {} document(s) into {}", written.get(), indexName);
    }

    public long getWrittenCount() {
        return written.get();
    }

    /**
     * Whether a document carries any of the fields this channel writes, which is what tells a
     * reader that an index was written by this version of the code.
     */
    static boolean isPageDocument(Iterable<IndexableField> fields) {
        for (IndexableField field : fields) {
            if (LuceneFields.CATALOG_VERSION.equals(field.name())) {
                return true;
            }
        }
        return false;
    }

}
