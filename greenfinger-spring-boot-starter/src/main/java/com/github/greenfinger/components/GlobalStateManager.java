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
