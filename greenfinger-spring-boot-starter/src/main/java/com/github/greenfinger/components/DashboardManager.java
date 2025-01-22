package com.github.greenfinger.components;

/**
 * 
 * @Description: DashboardManager
 * @Author: Fred Feng
 * @Date: 22/01/2025
 * @Version 1.0.0
 */
public interface DashboardManager {

    void reset(long durationInMs);

    boolean isCompleted();

    void setCompleted(boolean completed);

    long incrementCount(CountingType countingType);

    long incrementCount(CountingType countingType, int delta);

}
