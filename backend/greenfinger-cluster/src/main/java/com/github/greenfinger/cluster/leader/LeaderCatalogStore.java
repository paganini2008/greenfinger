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
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.greenfinger.cluster.replication.ReplicatedCatalogStore;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogStore;
import com.github.greenfinger.core.model.Catalog;
import lombok.extern.slf4j.Slf4j;

/**
 * Every change to a catalog, performed on the leader; every read, answered from here.
 *
 * <p>
 * The node that was asked forwards the call and waits. One writer is the point: catalog rows are
 * what everything else is addressed by, and two nodes writing them at once is how two copies stop
 * agreeing -- with one, nothing has to work out afterwards which copy was newer.
 *
 * <p>
 * Reads stay local, because sending them to the leader would make it the ceiling on how many
 * people can use the application. The cost is a window of milliseconds after a write, which
 * closes by itself -- except on the node that took the request, which is the one the caller talks
 * to next, so a write applies the leader's answer here before returning. See {@code applyHere}.
 *
 * @Description: LeaderCatalogStore
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class LeaderCatalogStore implements CatalogStore, ManagedBeanLifeCycle {

    public static final String SAVE = "catalog.save";
    public static final String DELETE = "catalog.delete";
    public static final String INCREMENT_INDEX_VERSION = "catalog.incrementIndexVersion";
    public static final String PUBLISH_SEARCH_VERSION = "catalog.publishSearchVersion";
    public static final String RESET_VERSIONS = "catalog.resetVersions";
    public static final String SET_RUNNING_STATE = "catalog.setRunningState";
    public static final String ALL = "catalog.all";

    /** This node's own copy, which every read is answered from. */
    private final CatalogStore local;

    /** The same store, writing and telling the others. Only ever used on the leader. */
    private final ReplicatedCatalogStore leaderSide;

    private final LeaderGateway gateway;

    public LeaderCatalogStore(CatalogStore local, ReplicatedCatalogStore leaderSide,
            LeaderGateway gateway) {
        this.local = local;
        this.leaderSide = leaderSide;
        this.gateway = gateway;
    }

    /** A catalog and a number, for the two operations that need both. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Versioned(String id, int version) {}

    /** A catalog and a running state. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Running(String id, String runningState) {}

    /**
     * Registered on every node, not only on the one that is the leader now: leadership moves, and
     * a node that took it over without the handlers would refuse every write with "unknown
     * operation".
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        gateway.handle(SAVE, Catalog.class, leaderSide::save);
        gateway.handle(DELETE, String.class, leaderSide::deleteById);
        // The row itself comes back from the three that change a version and the one that sets
        // the running state, not just the number: what the caller does next is read, and it will
        // read its own copy. See applyHere.
        gateway.handle(INCREMENT_INDEX_VERSION, String.class, id -> {
            leaderSide.incrementIndexVersion(id);
            return leaderSide.findById(id).orElse(null);
        });
        gateway.handle(PUBLISH_SEARCH_VERSION, Versioned.class, request -> {
            leaderSide.publishSearchVersion(request.id(), request.version());
            return leaderSide.findById(request.id()).orElse(null);
        });
        gateway.handle(RESET_VERSIONS, String.class, id -> {
            leaderSide.resetVersions(id);
            return leaderSide.findById(id).orElse(null);
        });
        gateway.handle(SET_RUNNING_STATE, Running.class, request -> {
            leaderSide.setRunningState(request.id(), request.runningState());
            return leaderSide.findById(request.id()).orElse(null);
        });
        // the whole table, for a node that has just joined and has to catch up
        gateway.handle(ALL, Void.class, ignored -> leaderSide.findAll().toArray(new Catalog[0]));
    }

    @Override
    public void destroy() throws Exception {
        // the gateway is stopped by its own lifecycle; nothing here holds anything open
    }

    @Override
    public String getName() {
        return "leader:" + local.getName();
    }

    // ---- writes: one node performs them ---------------------------------------------------

    @Override
    public Catalog save(Catalog catalog) {
        return applyHere(gateway.onLeader(SAVE, catalog, Catalog.class));
    }

    @Override
    public boolean deleteById(String id) {
        boolean deleted = Boolean.TRUE.equals(gateway.onLeader(DELETE, id, Boolean.class));
        if (deleted && !gateway.isLeader()) {
            forgetHere(id);
        }
        return deleted;
    }

    @Override
    public int incrementIndexVersion(String id) {
        Catalog after = applyHere(gateway.onLeader(INCREMENT_INDEX_VERSION, id, Catalog.class));
        return after != null && after.getIndexVersion() != null ? after.getIndexVersion() : 0;
    }

    @Override
    public void publishSearchVersion(String id, int version) {
        applyHere(gateway.onLeader(PUBLISH_SEARCH_VERSION, new Versioned(id, version),
                Catalog.class));
    }

    @Override
    public void resetVersions(String id) {
        applyHere(gateway.onLeader(RESET_VERSIONS, id, Catalog.class));
    }

    @Override
    public void setRunningState(String id, String runningState) {
        applyHere(gateway.onLeader(SET_RUNNING_STATE, new Running(id, runningState),
                Catalog.class));
    }

    /**
     * Writes what the leader did into this node's own copy, at once, so a create followed
     * immediately by a crawl does not answer "No such catalog". It is the same row the broadcast
     * will bring, so the broadcast becomes a no-op and the ordering cannot invert.
     *
     * <p>
     * A failure here is logged and stepped over: the write did happen, and the usual cause is a
     * stale row colliding on the name index, which is the catch-up's business.
     */
    private Catalog applyHere(Catalog fromLeader) {
        if (fromLeader == null || gateway.isLeader()) {
            return fromLeader;
        }
        try {
            local.save(fromLeader);
        } catch (RuntimeException e) {
            log.warn("Could not take the leader's copy of '{}' here: {}. The next catch-up will"
                    + " put it right.", fromLeader.getName(), e.getMessage());
        }
        return fromLeader;
    }

    private void forgetHere(String id) {
        try {
            local.deleteById(id);
        } catch (RuntimeException e) {
            log.warn("Could not remove '{}' here after the leader deleted it: {}", id,
                    e.getMessage());
        }
    }

    /** What the leader holds, which is what a node catching up makes its own table match. */
    public List<Catalog> fromLeader() {
        Catalog[] all = gateway.onLeader(ALL, null, Catalog[].class);
        return all != null ? List.of(all) : List.of();
    }

    // ---- reads: this node's own copy -------------------------------------------------------

    @Override
    public Optional<Catalog> findById(String id) {
        return local.findById(id);
    }

    @Override
    public Optional<Catalog> findByName(String name) {
        return local.findByName(name);
    }

    @Override
    public List<Catalog> findAll() {
        return local.findAll();
    }

    @Override
    public List<String> findAllCategories() {
        return local.findAllCategories();
    }

    @Override
    public List<Catalog> findRunning() {
        return local.findRunning();
    }

}
