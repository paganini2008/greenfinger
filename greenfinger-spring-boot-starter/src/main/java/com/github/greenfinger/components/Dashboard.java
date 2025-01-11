package com.github.greenfinger.components;


/**
 * 
 * @Description: Dashboard
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface Dashboard extends WebCrawlerComponent {

    void reset(long durationInMs, boolean includingCount);

    boolean isCompleted();

    void setCompleted(boolean completed);

    long incrementCount(String type);

    long incrementCount(String type, int delta);

    long getTotalUrlCount();

    long getInvalidUrlCount();

    long getExistingUrlCount();

    long getFilteredUrlCount();

    long getSavedResourceCount();

    long getIndexedResourceCount();

    long getStartTime();

    long getEndTime();

    long getElapsedTime();

    long getTimestamp();

}
