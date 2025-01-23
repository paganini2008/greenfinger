package com.github.greenfinger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 
 * @Description: ImmutableSettingsEnvironmentPostProcessor
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
public class ImmutableSettingsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
            SpringApplication application) {
        environment.getSystemProperties()
                .put("doodler.transmitter.nio.default-external-channel-accessable", "false");
        environment.getSystemProperties().put("doodler.transmitter.event.buffer-cleaner-enabled",
                "false");
    }

}
