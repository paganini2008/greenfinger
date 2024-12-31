package com.github.greenfinger.utils;

/**
 * 
 * @Description: RobotRuleFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface RobotRuleFilter {

    boolean isAllowed(String url);

}
