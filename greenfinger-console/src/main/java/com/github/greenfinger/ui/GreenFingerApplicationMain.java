package com.github.greenfinger.ui;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.github.greenfinger.EnableGreenFingerServer;

/**
 * 
 * @Description: GreenFingerApplicationMain
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@EnableDiscoveryClient
@EnableGreenFingerServer
@SpringBootApplication
public class GreenFingerApplicationMain {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }

    public static void main(String[] args) {
        SpringApplication.run(GreenFingerApplicationMain.class, args);
    }
}
