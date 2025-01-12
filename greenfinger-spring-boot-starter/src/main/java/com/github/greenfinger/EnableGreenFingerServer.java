package com.github.greenfinger;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import com.github.doodler.common.jdbc.annotations.DaoScan;
import com.github.doodler.common.transmitter.EnableNioTransmitter;

/**
 * 
 * @Description: EnableGreenFingerServer
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@DaoScan(basePackages = "com.github.greenfinger.jdbc")
@EnableNioTransmitter
@Import({GreenFingerAutoConfiguration.class})
public @interface EnableGreenFingerServer {
}
