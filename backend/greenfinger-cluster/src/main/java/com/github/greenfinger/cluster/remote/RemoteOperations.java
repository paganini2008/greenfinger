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

package com.github.greenfinger.cluster.remote;

import java.util.List;
import java.util.Map;
import com.github.greenfinger.cluster.leader.LeaderGateway;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.DeleteReport;
import com.github.greenfinger.service.ops.CatalogSnapshot;
import com.github.greenfinger.service.ops.GreenfingerOperations;

/**
 * The operations, performed by the leader: what the prompt runs, holding no engine, database or
 * index of its own. Asked of the leader rather than of a node picked by turn, for the reason
 * administrative writes already go there -- one writer, and an answer that is not a stale copy.
 *
 * @Description: RemoteOperations
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public class RemoteOperations implements GreenfingerOperations {

    private final LeaderGateway gateway;

    public RemoteOperations(LeaderGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Overview overview() {
        return gateway.onLeader(Ops.OVERVIEW, null, Overview.class);
    }

    @Override
    public CatalogSnapshot catalog(String idOrName) {
        return gateway.onLeader(Ops.CATALOG, new Ops.Ref(idOrName), CatalogSnapshot.class);
    }

    @Override
    public List<String> categories() {
        return gateway.onLeader(Ops.CATEGORIES, null, Ops.Names.class).values();
    }

    @Override
    public Catalog form(String idOrName) {
        return gateway.onLeader(Ops.FORM, new Ops.Ref(idOrName), Catalog.class);
    }

    @Override
    public CatalogSnapshot saveCatalog(Catalog form) {
        return gateway.onLeader(Ops.SAVE_CATALOG, form, CatalogSnapshot.class);
    }

    @Override
    public CatalogSnapshot ensureCatalog(String name, String url) {
        return gateway.onLeader(Ops.ENSURE_CATALOG, new Ops.NameAndUrl(name, url),
                CatalogSnapshot.class);
    }

    @Override
    public String deleteCatalog(String idOrName) {
        return gateway.onLeader(Ops.DELETE_CATALOG, new Ops.Ref(idOrName), Ops.Said.class).text();
    }

    @Override
    public Versions versions(String idOrName) {
        return gateway.onLeader(Ops.VERSIONS, new Ops.Ref(idOrName), Versions.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> report(String idOrName, Integer version) {
        return gateway.onLeader(Ops.REPORT, new Ops.ReportAsk(idOrName, version), Map.class);
    }

    @Override
    public String start(StartAsk ask) {
        return gateway.onLeader(Ops.START, ask, Ops.Said.class).text();
    }

    @Override
    public boolean interrupt(String idOrName) {
        return gateway.onLeader(Ops.INTERRUPT, new Ops.Ref(idOrName), Ops.Said.class).value();
    }

    @Override
    public Live live(String catalogId, boolean perNode) {
        return gateway.onLeader(Ops.LIVE, new Ops.LiveAsk(catalogId, perNode), Live.class);
    }

    @Override
    public List<DeleteReport.Line> delete(DeleteAsk ask) {
        return gateway.onLeader(Ops.DELETE, ask, Ops.Lines.class).values();
    }

    @Override
    public ReplayAnswer replay(ReplayAsk ask) {
        return gateway.onLeader(Ops.REPLAY, ask, ReplayAnswer.class);
    }

    @Override
    public SearchAnswer search(SearchAsk ask) {
        return gateway.onLeader(Ops.SEARCH, ask, SearchAnswer.class);
    }

    @Override
    public Info indexInfo() {
        return gateway.onLeader(Ops.INDEX_INFO, null, Info.class);
    }

    @Override
    public Info vectorInfo() {
        return gateway.onLeader(Ops.VECTOR_INFO, null, Info.class);
    }

}
