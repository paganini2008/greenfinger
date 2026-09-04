package com.github.greenfinger.ui;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.github.doodler.common.swagger.EnableSwaggerResource;
import com.github.greenfinger.EnableGreenfingerServer;

/**
 * 
 * @Description: GreenfingerApplicationMain
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@EnableSwaggerResource
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
@EnableGreenfingerServer
@SpringBootApplication
public class GreenfingerApplicationMain {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }

    public static void main(String[] args) {
        SpringApplication.run(GreenfingerApplicationMain.class, args);
    }
}
