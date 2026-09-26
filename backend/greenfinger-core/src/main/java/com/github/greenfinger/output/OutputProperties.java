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

package com.github.greenfinger.output;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Everything about where a crawl's results go. Lives in one external configuration directory
 * beside the launcher, never inside the jar.
 * 
 * @Description: OutputProperties
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@ConfigurationProperties("greenfinger.output")
@Getter
@Setter
@ToString
public class OutputProperties {

    /**
     * The outputs a catalog feeds when it does not say: any combination of file, index and vector.
     * {@code file} is always included whether or not it is listed.
     */
    private String types = "file";

    private File file = new File();
    private Index index = new Index();
    private Vector vector = new Vector();

    /**
     * The mandatory output. Its two targets are alternatives, and their layouts are identical --
     * the string that is a path on disk is the object key in MinIO.
     * 
     * @Description: File
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class File {

        /** local or minio. */
        private String target = "local";

        private String directory = "./data/user/assets";

        /**
         * Directory levels taken from the front of an id, two hex characters each, so no directory
         * ends up holding millions of files. Zero turns it off.
         */
        private int shardDepth = 2;

        private Minio minio = new Minio();

        /**
         * 
         * @Description: Minio
         * @Author: Fred Feng
         * @Date: 30/08/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString(exclude = "secretKey")
        public static class Minio {

            private String endpoint = "http://127.0.0.1:9000";
            private String accessKey = "PLACEHOLDER_ACCESS_KEY";
            private String secretKey = "PLACEHOLDER_SECRET_KEY";
            private String bucket = "greenfinger";
            private boolean createBucketIfMissing = true;
        }
    }

    /**
     * Full text search.
     * 
     * @Description: Index
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString(exclude = "password")
    public static class Index {

        /**
         * lucene or elasticsearch. Lucene is embedded, so a fresh clone needs nothing installed;
         * both write the same documents, so moving is a {@code replay}, not a re-crawl.
         */
        private String provider = "lucene";

        private String uris = "http://localhost:9200";
        private String username;
        private String password;

        /**
         * One index per catalog, {@code <prefix>-<catalogId>}, holding every version of it.
         *
         * <p>
         * Per catalog so a delete is a drop rather than a delete-by-query, and so one catalog can
         * have its own analyzer; a search across everything fans out over {@code <prefix>-*}. By
         * id, never name: a rename would orphan the index.
         */
        private String prefix = "greenfinger";

        private int connectTimeout = 10000;
        private int readTimeout = 60000;

        /** Documents per bulk request. */
        private int batchSize = 100;

        private int numberOfShards = 1;
        private int numberOfReplicas = 0;

        /**
         * Analyzer for the title and content fields. ik_max_word needs the IK plugin installed;
         * "standard" works everywhere but splits CJK into single characters.
         */
        private String analyzer = "standard";

        /**
         * After deleting a version, ask Elasticsearch to reclaim the space rather than waiting for
         * a merge. Off by default: on a large index this is an expensive operation.
         */
        private boolean forcemergeAfterDelete = false;

        private Lucene lucene = new Lucene();

        /**
         * The embedded index.
         * 
         * @Description: Lucene
         * @Author: Fred Feng
         * @Date: 03/09/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString
        public static class Lucene {

            /**
             * One directory per catalog. Beside the frontier rather than the pages: derived,
             * rebuildable with {@code replay}, and belonging to one node rather than shared.
             */
            private String directory = "./data/user/index";

            /**
             * standard, smartcn or cjk. {@code standard} splits CJK into characters and ranks it
             * badly; {@code smartcn} is a real segmenter; {@code cjk} is bigrams, no dictionary.
             */
            private String analyzer = "standard";

            /**
             * How many documents to buffer before writing a segment. The Lucene default is a
             * memory ceiling rather than a count; this is the count the output channel batches on,
             * kept in step with {@code batchSize} above so both providers behave alike.
             */
            private int commitEvery = 1000;
        }
    }

    /**
     * Semantic and cross modal search.
     * 
     * @Description: Vector
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Getter
    @Setter
    @ToString
    public static class Vector {

        /**
         * lucene, elasticsearch, qdrant or weaviate. Lucene's HNSW is what Elasticsearch's own knn
         * is built on, so embedded is a smaller deployment of the same engine, and Elasticsearch
         * is the server the index already uses. Qdrant and Weaviate stay for deployments with one.
         */
        private String store = "lucene";

        /**
         * Two collections, because the text model and the image model produce vectors in different
         * spaces and of different lengths. The dimension is appended at runtime, so switching
         * models puts the new vectors somewhere of their own rather than into a collection that
         * cannot hold them.
         */
        private String textCollection = "greenfinger_text";
        private String imageCollection = "greenfinger_image";

        private Lucene lucene = new Lucene();
        private Elasticsearch elasticsearch = new Elasticsearch();
        private Qdrant qdrant = new Qdrant();
        private Weaviate weaviate = new Weaviate();

        /**
         * Long pages are split before embedding, since an embedding of a whole page averages away
         * whatever made any one passage distinctive.
         */
        private int chunkSize = 1000;
        private int chunkOverlap = 200;
        private int maxChunksPerPage = 20;

        /**
         * The embedded vector store.
         * 
         * @Description: Lucene
         * @Author: Fred Feng
         * @Date: 03/09/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString
        public static class Lucene {

            /** One directory per collection underneath this one. */
            private String directory = "./data/user/vector";

            /**
             * cosine, dot or euclidean. Cosine because the models here produce vectors whose
             * length carries no meaning.
             */
            private String similarity = "cosine";

            /**
             * HNSW graph connections per node, and how wide the search is while the graph is being
             * built. Lucene's own defaults; raising them trades index time and size for recall.
             */
            private int maxConnections = 16;
            private int beamWidth = 100;
        }

        /**
         * Vectors in the server the index already uses. A separate {@code uris} so the two can be
         * put on different clusters; as it ships they point at the same one.
         *
         * @Description: Elasticsearch
         * @Author: Fred Feng
         * @Date: 04/09/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString
        public static class Elasticsearch {

            private String uris = "http://localhost:9200";
            private String username;
            private String password;

            /**
             * How distance is measured. {@code cosine} because the models here return vectors
             * that are not normalised, and cosine is the one that does not care.
             */
            private String similarity = "cosine";

            /**
             * The floor on the candidate pool. Elasticsearch filters after the graph walk, so a
             * walk of exactly k returns fewer once filtered -- which reads as missing data.
             */
            private int minCandidates = 100;

            private int connectTimeout = 10000;
            private int readTimeout = 60000;

            /**
             * Shards and replicas for the index this creates. Zero replicas, like the text index:
             * a single node cannot place a copy of its own shard, so the default of one leaves
             * every fresh install with a permanently yellow cluster and nothing to fix it with.
             */
            private int numberOfShards = 1;

            private int numberOfReplicas = 0;

            private boolean createCollectionIfMissing = true;
        }

        /**
         * 
         * @Description: Qdrant
         * @Author: Fred Feng
         * @Date: 30/08/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString
        public static class Qdrant {

            private String url = "http://localhost:6333";
            private String apiKey;

            /** cosine, dot or euclid. */
            private String distance = "Cosine";

            private int batchSize = 64;
            private boolean createCollectionIfMissing = true;
        }

        /**
         * 
         * @Description: Weaviate
         * @Author: Fred Feng
         * @Date: 30/08/2026
         * @Version 2.0.0
         */
        @Getter
        @Setter
        @ToString(exclude = "apiKey")
        public static class Weaviate {

            /** With or without the trailing /v1; either form is accepted. */
            private String url = "http://localhost:18080";

            private String apiKey;

            /** cosine, dot, l2-squared, hamming or manhattan. */
            private String distance = "cosine";

            private int batchSize = 64;
            private boolean createCollectionIfMissing = true;
        }
    }

}
