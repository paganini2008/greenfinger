package com.github.greenfinger.components;

import java.util.List;

/**
 * 
 * @Description: DashboardManager
 * @Author: Fred Feng
 * @Date: 22/01/2025
 * @Version 1.0.0
 */
public interface DashboardManager {

    void addMember(String instanceId);

    List<String> getMembers();

    boolean isCompleted();

    void setCompleted(boolean completed);

    long incrementCount(CountingType countingType);

    long incrementCount(CountingType countingType, int delta);

}
