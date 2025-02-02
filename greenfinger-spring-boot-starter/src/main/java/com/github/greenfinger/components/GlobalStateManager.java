/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.components;

import java.util.List;
import java.util.concurrent.TimeUnit;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: GlobalStateManager
 * @Author: Fred Feng
 * @Date: 22/01/2025
 * @Version 1.0.0
 */
public interface GlobalStateManager extends WebCrawlerComponent {

    void addMember(String instanceId);

    void removeMember(String instanceId);

    List<String> getMembers();

    boolean isCompleted();

    void setCompleted(boolean completed);

    default long incrementCount(long startTime, CountingType countingType) {
        return incrementCount(startTime, countingType, 1);
    }

    long incrementCount(long startTime, CountingType countingType, int delta);

    boolean isTimeout(long delay, TimeUnit timeUnit);

    Dashboard getDashboard();

    CatalogDetails getCatalogDetails();

}
