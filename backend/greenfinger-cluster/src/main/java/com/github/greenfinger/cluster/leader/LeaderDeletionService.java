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

package com.github.greenfinger.cluster.leader;

import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.WebCrawlerSemaphore;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.catalog.CatalogDetailsService;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.record.ResourceRecordStore;
import com.github.greenfinger.core.report.CrawlReportStore;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.output.OutputProperties;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.DeletionBroadcast;
import com.github.greenfinger.service.DeletionService;

/**
 * A delete, performed by the leader wherever it was asked for.
 *
 * <p>
 * A delete is four deletes -- rows, files, index, vectors -- across stores that each node has its
 * own copy of, and two of those four cannot replicate themselves at all. Running it wherever the
 * request happened to land meant four different nodes could be emptying the same catalog at once,
 * each announcing what it had done to the others. One node does it and tells everybody, which is
 * the same rule the catalog rows follow, for the same reason.
 *
 * <p>
 * The detail that made this worth routing rather than merely announcing: the layers that do
 * replicate are removed by the leader's own execution, and the two that do not -- the embedded
 * index and the RocksDB directories -- are removed by every node when the leader says so. Both
 * halves then have one origin, and "who deleted this" has one answer.
 *
 * <h2>What crosses the wire</h2>
 * The catalog's id and what was asked for, not the {@code CatalogDetails}: that is a view over a
 * row plus the configuration in force, and the leader has both. Reloading it there also means the
 * delete runs against the leader's idea of the catalog rather than a follower's, which is the one
 * that matters.
 *
 * @Description: LeaderDeletionService
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
public class LeaderDeletionService extends DeletionService implements ManagedBeanLifeCycle {

    public static final String VERSIONS = "delete.versions";
    public static final String CLEAN = "delete.clean";
    public static final String CATALOG = "delete.catalog";

    private final LeaderGateway gateway;
    private final CatalogDetailsService catalogDetailsService;

    public LeaderDeletionService(OutputFactory outputFactory, OutputProperties outputProperties,
            WebCrawlerProperties webCrawlerProperties, ResourceRecordStore recordStore,
            WebCrawlerSemaphore semaphore, CatalogStore catalogStore,
            CrawlReportStore reportStore, DeletionBroadcast broadcast, LeaderGateway gateway,
            CatalogDetailsService catalogDetailsService) {
        super(outputFactory, outputProperties, webCrawlerProperties, recordStore, semaphore,
                catalogStore, reportStore, broadcast);
        this.gateway = gateway;
        this.catalogDetailsService = catalogDetailsService;
    }

    /**
     * @param versions null for every version of the catalog
     * @param layers   the command line form, so a node running an older build reads the layers it
     *                 knows rather than a set of ordinals that have moved
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String catalogId, List<Integer> versions, String layers, boolean dryRun,
            boolean force) {}

    @Override
    public void afterPropertiesSet() throws Exception {
        gateway.handle(VERSIONS, Request.class,
                request -> linesOf(super.delete(detailsOf(request), request.versions(),
                        DeleteLayer.parse(request.layers()), request.dryRun(),
                        request.force())));
        gateway.handle(CLEAN, Request.class,
                request -> linesOf(super.cleanCatalog(detailsOf(request),
                        DeleteLayer.parse(request.layers()), request.dryRun(),
                        request.force())));
        gateway.handle(CATALOG, Request.class,
                request -> linesOf(super.deleteCatalog(detailsOf(request),
                        DeleteLayer.parse(request.layers()), request.dryRun(),
                        request.force())));
    }

    @Override
    public void destroy() throws Exception {
        // the gateway has its own lifecycle; nothing here holds anything open
    }

    private CatalogDetails detailsOf(Request request) {
        return catalogDetailsService.loadCatalogDetails(request.catalogId());
    }

    /**
     * The report travels as its lines.
     *
     * <p>
     * {@code DeleteReport} accumulates through {@code add} and has no way to be constructed from
     * the outside, which is right for what it is and means it cannot be read back from json. Its
     * lines are records and carry everything it holds, so they are what crosses and the report is
     * built again on the other side.
     */
    private static DeleteReport.Line[] linesOf(DeleteReport report) {
        return report.getLines().toArray(new DeleteReport.Line[0]);
    }

    private static DeleteReport reportOf(DeleteReport.Line[] lines) {
        DeleteReport report = new DeleteReport();
        if (lines != null) {
            for (DeleteReport.Line line : lines) {
                report.add(line.version(), line.layer(), line.count(), line.bytes(),
                        line.error());
            }
        }
        return report;
    }

    @Override
    public DeleteReport delete(CatalogDetails catalogDetails, List<Integer> versions,
            Set<DeleteLayer> layers, boolean dryRun, boolean force) {
        return reportOf(gateway.onLeader(VERSIONS, new Request(catalogDetails.getId(), versions,
                repr(layers), dryRun, force), DeleteReport.Line[].class));
    }

    @Override
    public DeleteReport cleanCatalog(CatalogDetails catalogDetails, Set<DeleteLayer> layers,
            boolean dryRun, boolean force) {
        return reportOf(gateway.onLeader(CLEAN,
                new Request(catalogDetails.getId(), null, repr(layers), dryRun, force),
                DeleteReport.Line[].class));
    }

    @Override
    public DeleteReport deleteCatalog(CatalogDetails catalogDetails, Set<DeleteLayer> layers,
            boolean dryRun, boolean force) {
        return reportOf(gateway.onLeader(CATALOG,
                new Request(catalogDetails.getId(), null, repr(layers), dryRun, force),
                DeleteReport.Line[].class));
    }

    private static String repr(Set<DeleteLayer> layers) {
        return layers.stream().map(DeleteLayer::getRepr).reduce((a, b) -> a + "," + b).orElse("");
    }

}
