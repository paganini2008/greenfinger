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

import com.github.greenfinger.cluster.leader.LeaderGateway;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.model.Catalog;
import com.github.greenfinger.service.ops.GreenfingerOperations;

/**
 * What a terminal elsewhere in the cluster may ask for, registered on the leader channel. The
 * whitelist is the point: nothing is reachable from another process unless it is named here.
 *
 * @Description: OperationsServer
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
public class OperationsServer implements ManagedBeanLifeCycle {

    private final LeaderGateway gateway;
    private final GreenfingerOperations operations;

    public OperationsServer(LeaderGateway gateway, GreenfingerOperations operations) {
        this.gateway = gateway;
        this.operations = operations;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        gateway.handle(Ops.OVERVIEW, Void.class, ignored -> operations.overview());
        gateway.handle(Ops.CATALOG, Ops.Ref.class, ref -> operations.catalog(ref.idOrName()));
        gateway.handle(Ops.CATEGORIES, Void.class,
                ignored -> new Ops.Names(operations.categories()));
        gateway.handle(Ops.FORM, Ops.Ref.class, ref -> operations.form(ref.idOrName()));
        gateway.handle(Ops.SAVE_CATALOG, Catalog.class, operations::saveCatalog);
        gateway.handle(Ops.ENSURE_CATALOG, Ops.NameAndUrl.class,
                ask -> operations.ensureCatalog(ask.name(), ask.url()));
        gateway.handle(Ops.DELETE_CATALOG, Ops.Ref.class,
                ref -> new Ops.Said(true, operations.deleteCatalog(ref.idOrName())));
        gateway.handle(Ops.VERSIONS, Ops.Ref.class, ref -> operations.versions(ref.idOrName()));
        gateway.handle(Ops.REPORT, Ops.ReportAsk.class,
                ask -> operations.report(ask.idOrName(), ask.version()));
        gateway.handle(Ops.START, GreenfingerOperations.StartAsk.class,
                ask -> new Ops.Said(true, operations.start(ask)));
        gateway.handle(Ops.INTERRUPT, Ops.Ref.class,
                ref -> new Ops.Said(operations.interrupt(ref.idOrName()), null));
        gateway.handle(Ops.LIVE, Ops.LiveAsk.class,
                ask -> operations.live(ask.catalogId(), ask.perNode()));
        gateway.handle(Ops.DELETE, GreenfingerOperations.DeleteAsk.class,
                ask -> new Ops.Lines(operations.delete(ask)));
        gateway.handle(Ops.REPLAY, GreenfingerOperations.ReplayAsk.class, operations::replay);
        gateway.handle(Ops.SEARCH, GreenfingerOperations.SearchAsk.class, operations::search);
        gateway.handle(Ops.INDEX_INFO, Void.class, ignored -> operations.indexInfo());
        gateway.handle(Ops.VECTOR_INFO, Void.class, ignored -> operations.vectorInfo());
    }

}
