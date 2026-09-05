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

package com.github.greenfinger.core.component.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.component.state.DefaultDashboard.ElapsedWindow;

/**
 * Owns the dashboard for a standalone crawl. The member list is kept -- and always holds the single
 * local instance -- so the distributed edition can replace this class without any caller changing.
 * 
 * @Description: DefaultGlobalStateManager
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class DefaultGlobalStateManager implements GlobalStateManager {

    private final CatalogDetails catalogDetails;
    private final DefaultDashboard dashboard;
    private final List<String> members = new CopyOnWriteArrayList<>();

    public DefaultGlobalStateManager(CatalogDetails catalogDetails) {
        this.catalogDetails = catalogDetails;
        this.dashboard = new DefaultDashboard(catalogDetails);
    }

    @Override
    public String getName() {
        return "default";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        dashboard.afterPropertiesSet();
    }

    @Override
    public void addMember(String instanceId) {
        if (!members.contains(instanceId)) {
            members.add(instanceId);
        }
    }

    @Override
    public void removeMember(String instanceId) {
        members.remove(instanceId);
    }

    @Override
    public List<String> getMembers() {
        return List.copyOf(members);
    }

    @Override
    public boolean isCompleted() {
        return dashboard.isCompleted();
    }

    @Override
    public void setCompleted(boolean completed, String reason, boolean interrupted) {
        // the reason before the flag: whoever reads the flag reads the reason in the same breath
        if (completed && dashboard.completionReason == null) {
            dashboard.completionReason = reason;
            dashboard.interrupted = interrupted;
        }
        dashboard.completed.set(completed);
        dashboard.lastModified = System.currentTimeMillis();
    }

    @Override
    public void overrideAsUnproductive(String reason) {
        dashboard.completionReason = reason;
        dashboard.interrupted = true;
        dashboard.completed.set(true);
        dashboard.lastModified = System.currentTimeMillis();
    }

    @Override
    public long incrementCount(long startTime, CountingType countingType, int delta) {
        long value = dashboard.counterOf(countingType).addAndGet(delta);
        if (startTime > 0) {
            dashboard.elapsed.computeIfAbsent(countingType, k -> new ElapsedWindow())
                    .add(System.currentTimeMillis() - startTime);
        }
        dashboard.lastModified = System.currentTimeMillis();
        return value;
    }

    @Override
    public boolean isTimeout(long delay, TimeUnit timeUnit) {
        return System.currentTimeMillis() - dashboard.getLastModified() > timeUnit.toMillis(delay);
    }

    @Override
    public Dashboard getDashboard() {
        return dashboard;
    }

    @Override
    public CatalogDetails getCatalogDetails() {
        return catalogDetails;
    }

}
