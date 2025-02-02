/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger;

import static com.github.doodler.common.transmitter.TransmitterConstants.TRANSMITTER_SERVER_LOCATION;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.github.doodler.common.scheduler.Default;
import com.github.doodler.common.transmitter.ChannelSwitcher;
import com.github.doodler.common.utils.NetUtils;

/**
 * 
 * @Description: ChannelSwitcherJob
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
@Component
public class ChannelSwitcherJob {

    @Value("${spring.application.name}")
    private String applicationName;

    @Autowired
    private WebCrawlerSemaphore semaphore;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private ChannelSwitcher channelSwitcher;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Default
    @Scheduled(cron = "*/10 * * * * ?")
    public void run() throws Exception {
        if (!semaphore.isOccupied()) {
            return;
        }
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails == null) {
            return;
        }
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogDetails.getId(), false);
        if (executionContext == null || executionContext.getGlobalStateManager() == null) {
            return;
        }
        if (executionContext.getGlobalStateManager().isCompleted()) {
            return;
        }
        Optional.ofNullable(executionContext.getGlobalStateManager().getMembers()).ifPresent(l -> {
            l.forEach(instanceId -> {
                Optional<SocketAddress> opt = lookupSocketAddressesFromDiscoveryClient(instanceId);
                if (opt.isPresent()) {
                    channelSwitcher.enableExternalChannel(opt.get(), true);
                } else {
                    executionContext.getGlobalStateManager().removeMember(instanceId);
                }
            });
        });
    }

    private Optional<SocketAddress> lookupSocketAddressesFromDiscoveryClient(String instanceId) {
        List<ServiceInstance> serviceInstances = discoveryClient.getInstances(applicationName);
        return serviceInstances.stream().filter(i -> instanceId.equals(i.getInstanceId()))
                .map(i -> i.getMetadata().get(TRANSMITTER_SERVER_LOCATION))
                .map(s -> NetUtils.parse(s)).findFirst();
    }
}
