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
import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollectorManager;
import com.github.greenfinger.core.output.IndexAdmin;
import com.github.greenfinger.output.OutputProperties;

/**
 * Index housekeeping for the embedded index.
 *
 * <p>
 * Deleting a version is a term query, as it is on Elasticsearch, and equally a marked deletion --
 * the space comes back when segments merge. Deleting a whole catalog is different and better here:
 * the index is that catalog's own directory, so it goes at once rather than document by document.
 * 
 * @Description: LuceneIndexAdmin
 * @Author: Fred Feng
 * @Date: 03/09/2026
 * @Version 2.0.0
 */
public class LuceneIndexAdmin implements IndexAdmin {

    private final OutputProperties.Index config;
    private final LuceneIndexes indexes;

    public LuceneIndexAdmin(OutputProperties.Index config, LuceneIndexes indexes) {
        this.config = config;
        this.indexes = indexes;
    }

    @Override
    public String getName() {
        return "lucene";
    }

    @Override
    public String getLocation() {
        return indexes.getRoot().toString();
    }

    @Override
    public String getIndexPrefix() {
        return config.getPrefix();
    }

    @Override
    public String indexOf(String catalogId) {
        return IndexAdmin.indexOf(config.getPrefix(), catalogId);
    }

    @Override
    public boolean indexExists(String catalogId) {
        return indexes.exists(indexOf(catalogId));
    }

    @Override
    public long countByCatalogVersion(String catalogVersion) throws IOException {
        String name = indexOf(IndexAdmin.catalogIdOf(catalogVersion));
        IndexSearcher searcher = indexes.acquire(name);
        if (searcher == null) {
            return 0L;
        }
        try {
            return searcher.search(
                    new TermQuery(new Term(LuceneFields.CATALOG_VERSION, catalogVersion)),
                    new TotalHitCountCollectorManager());
        } finally {
            indexes.release(name, searcher);
        }
    }

    @Override
    public long countByCatalog(String catalogId) throws IOException {
        String name = indexOf(catalogId);
        IndexSearcher searcher = indexes.acquire(name);
        if (searcher == null) {
            return 0L;
        }
        try {
            return searcher.getIndexReader().numDocs();
        } finally {
            indexes.release(name, searcher);
        }
    }

    @Override
    public long deleteByCatalogVersion(String catalogVersion) throws IOException {
        String name = indexOf(IndexAdmin.catalogIdOf(catalogVersion));
        if (!indexes.exists(name)) {
            return 0L;
        }
        // counted first: Lucene's deleteDocuments returns a sequence number rather than a count,
        // and a delete that reports its own sequence number as "42 removed" is worse than one
        // that costs an extra query
        long counted = countByCatalogVersion(catalogVersion);
        indexes.writer(name)
                .deleteDocuments(new Term(LuceneFields.CATALOG_VERSION, catalogVersion));
        indexes.commit(name);
        return counted;
    }

    @Override
    public long deleteAllVersions(String catalogId) throws IOException {
        String name = indexOf(catalogId);
        if (!indexes.exists(name)) {
            return 0L;
        }
        long counted = countByCatalog(catalogId);
        indexes.writer(name).deleteDocuments(new Term(LuceneFields.CATALOG_ID, catalogId));
        indexes.commit(name);
        return counted;
    }

    @Override
    public long deleteByCatalog(String catalogId) throws IOException {
        String name = indexOf(catalogId);
        if (!indexes.exists(name)) {
            return 0L;
        }
        long counted = countByCatalog(catalogId);
        indexes.drop(name);
        return counted;
    }

    @Override
    public List<String> listIndices() {
        return indexes.names();
    }

    @Override
    public void refresh() {
        indexes.commitAll();
    }

}
