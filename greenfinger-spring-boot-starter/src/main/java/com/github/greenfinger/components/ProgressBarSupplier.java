package com.github.greenfinger.components;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.github.doodler.common.Constants;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
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

    private final CatalogDetails catalogDetails;
    private final Dashboard dashboard;
    private final AtomicLong counter = new AtomicLong(0);
    private TimeWaitProgressBar maxFetchSizeProgressBar;
    private TimeWaitProgressBar durationProgressBar;


    @Override
    public void afterPropertiesSet() throws Exception {
        if (maxFetchSizeProgressBar == null) {
            maxFetchSizeProgressBar = new TimeWaitProgressBar(3, TimeUnit.SECONDS,
                    () -> catalogDetails.getMaxFetchSize().longValue(),
                    () -> catalogDetails.getCountingType().getValue(dashboard),
                    () -> dashboard.getAverageExecutionTime(), new MaxFetchSize());
            maxFetchSizeProgressBar.afterPropertiesSet();
        }
        if (durationProgressBar == null) {
            durationProgressBar = new TimeWaitProgressBar(3, TimeUnit.SECONDS,
                    () -> catalogDetails.getFetchDuration(), () -> dashboard.getElapsedTime(),
                    () -> dashboard.getAverageExecutionTime(), new Duration());
            durationProgressBar.afterPropertiesSet();
        }
    }


    @Override
    public void destroy() throws Exception {
        if (maxFetchSizeProgressBar != null) {
            maxFetchSizeProgressBar.destroy();
        }
        if (durationProgressBar != null) {
            durationProgressBar.destroy();
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
