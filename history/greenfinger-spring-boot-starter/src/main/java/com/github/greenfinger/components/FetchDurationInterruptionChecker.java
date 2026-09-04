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

import java.util.concurrent.TimeUnit;
import com.github.doodler.common.utils.DateUtils;
import com.github.greenfinger.CatalogDetails;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: FetchDurationInterruptionChecker
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class FetchDurationInterruptionChecker implements InterruptionChecker {

    @Override
    public boolean shouldInterrupt(CatalogDetails catalogDetails, Dashboard dashboard) {
        long duration = catalogDetails.getFetchDuration();
        long durationInMs = DateUtils.convertToMillis(duration, TimeUnit.MINUTES);
        return dashboard.getElapsedTime() > durationInMs;
    }

}
