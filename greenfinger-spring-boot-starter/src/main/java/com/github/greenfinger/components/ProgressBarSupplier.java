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
import java.util.concurrent.atomic.AtomicLong;
import com.github.doodler.common.Constants;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.utils.DateUtils;
import com.github.doodler.common.utils.DefaultProgressBarBuilder;
import com.github.doodler.common.utils.ProgressBar;
import com.github.doodler.common.utils.TimeWaitProgressBar;
import com.github.greenfinger.CatalogDetails;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: ProgressBarSupplier
 * @Author: Fred Feng
 * @Date: 26/01/2025
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class ProgressBarSupplier implements ManagedBeanLifeCycle, WebCrawlerComponent {

    private static final int PROGRESS_BAR_MOVING_INTERVAL = 5;
    private final CatalogDetails catalogDetails;
    private final Dashboard dashboard;
    private final AtomicLong counter = new AtomicLong(0);
    private TimeWaitProgressBar maxFetchSizeProgressBar;
    private TimeWaitProgressBar durationProgressBar;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (maxFetchSizeProgressBar == null) {
            maxFetchSizeProgressBar = new TimeWaitProgressBar(PROGRESS_BAR_MOVING_INTERVAL,
                    TimeUnit.SECONDS, () -> catalogDetails.getMaxFetchSize().longValue(),
                    () -> catalogDetails.getCountingType().getValue(dashboard),
                    () -> dashboard.getAverageExecutionTime(), new MaxFetchSize());
            maxFetchSizeProgressBar.afterPropertiesSet();
        }
        if (durationProgressBar == null) {
            durationProgressBar =
                    new TimeWaitProgressBar(PROGRESS_BAR_MOVING_INTERVAL, TimeUnit.SECONDS,
                            () -> DateUtils.convertToMillis(catalogDetails.getFetchDuration(),
                                    TimeUnit.MINUTES),
                            () -> dashboard.getElapsedTime(),
                            () -> dashboard.getAverageExecutionTime(), new Duration());
            durationProgressBar.afterPropertiesSet();
        }
    }

    @Override
    public void destroy() {
        if (maxFetchSizeProgressBar != null) {
            try {
                maxFetchSizeProgressBar.destroy();
            } catch (Exception ignored) {
            }
        }
        if (durationProgressBar != null) {
            try {
                durationProgressBar.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    private class MaxFetchSize extends DefaultProgressBarBuilder {

        @Override
        public String getDescription() {
            return String.format("[%s] Crawling MaxFetchSize: ", catalogDetails.getId());
        }

        @Override
        public String getAdditionalInformation(ProgressBar progressBar) {
            String original = super.getAdditionalInformation(progressBar);
            original += Constants.NEWLINE;
            original += dashboard.toString();
            return original;
        }

        @Override
        public void printBar(String bar) {
            if ((counter.getAndIncrement() & 1) == 0) {
                super.printBar(bar);
            }
        }
    }

    private class Duration extends DefaultProgressBarBuilder {

        @Override
        public String getDescription() {
            return String.format("[%s] Crawling Duration: ", catalogDetails.getId());
        }

        @Override
        public String getAdditionalInformation(ProgressBar progressBar) {
            String original = super.getAdditionalInformation(progressBar);
            original += Constants.NEWLINE;
            original += dashboard.toString();
            return original;
        }


        @Override
        public void printBar(String bar) {
            if ((counter.getAndIncrement() & 1) != 0) {
                super.printBar(bar);
            }
        }
    }

}
