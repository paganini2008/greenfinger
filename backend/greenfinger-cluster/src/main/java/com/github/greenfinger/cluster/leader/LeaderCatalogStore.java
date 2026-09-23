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
 * <h2>Writes</h2>
 * The node that was asked forwards the call and waits for the answer. The leader performs it
 * against its own table and tells the others, which is what {@link ReplicatedCatalogStore} was
 * already doing -- so the execution half is unchanged and this class is only about where it
 * happens. On the leader the forward is a plain call, which is the ordinary case in a cluster of
 * one.
 *
 * <p>
 * One writer is the point. Catalog rows are the state everything else is addressed by -- a crawl
 * opens the version the row names, a search serves the version the row published -- and two nodes
 * writing them at once is how two copies stop agreeing. Nothing here has to work out afterwards
 * which copy was newer, because there is only ever one place a change is made.
 *
 * <h2>Reads</h2>
 * Local, and deliberately. A read that went to the leader would turn every page of the catalog
 * list into a round trip and make the leader the ceiling on how many people can look at the
 * application at once. What that costs is a window after a write in which a node has not heard
 * yet, measured in milliseconds, and it closes by itself.
 *
 * <p>
 * With one exception, because that window is not harmless where it lands: the node that took the
 * request is the node the caller will talk to next. Creating a catalog and immediately crawling
 * it answered "No such catalog", which is true of that node's copy and absurd to the person who
 * had just made it. So a write applies the leader's answer here before returning -- see
 * {@code applyHere}.
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
     * Writes what the leader did into this node's own copy, at once.
     *
     * <h2>Read your own write</h2>
     * The broadcast reaches every node including this one, but it is asynchronous, and the node
     * that took the request is the very node the caller will talk to next. Creating a catalog and
     * immediately starting a crawl of it answered "No such catalog" for a few milliseconds --
     * true of this node's copy, absurd to the person who had just made it.
     *
     * <p>
     * So the answer is applied here before the call returns. It is the same row the broadcast
     * will bring, so the broadcast becomes a no-op rather than a second write, and the ordering
     * cannot invert: this is the row the leader produced, not a guess at it.
     *
     * <p>
     * A failure here is logged and stepped over. The write happened -- the leader performed it --
     * and refusing to return it because this node could not keep up would be a lie about what
     * took place. The usual reason is a stale row of this node's colliding on the unique name
     * index, which is the catch-up's business.
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
