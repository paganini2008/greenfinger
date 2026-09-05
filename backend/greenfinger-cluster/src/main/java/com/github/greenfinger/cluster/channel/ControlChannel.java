/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.cluster.channel;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.Channels;
import lombok.extern.slf4j.Slf4j;

/**
 * Three sentences the cluster says to itself about a crawl: it started, stop it, it is over.
 *
 * <p>
 * Deliberately not buffered. Every one of these is a few bytes and a flag, and the whole point of
 * a stop is that it takes effect now -- putting it behind a queue that is full of the very urls it
 * is trying to stop would be the one place a buffer costs more than it saves.
 * 
 * <h2>It registers itself</h2>
 * {@link SelfRegisteringListener} is not decoration. Without it the auto-registrar also puts this
 * listener on the <em>default</em> channel, and every message then arrives twice -- which shows up
 * not as an error but as a crawl that fetches every page a second time.
 *
 * @Description: ControlChannel
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class ControlChannel implements GossipListener, SelfRegisteringListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GossipCluster cluster;
    private final Consumer<ControlMessage> handler;

    public ControlChannel(GossipCluster cluster, Consumer<ControlMessage> handler) {
        this.cluster = cluster;
        this.handler = handler;
    }

    public void start() {
        cluster.addListener(Channels.CONTROL, this);
    }

    public void stop() {
        cluster.removeListener(this);
    }

    /**
     * Tells everyone, this node included. Including self is what makes the sender take the same
     * path as everybody else, so there is no second copy of "and also do it here" to keep in step.
     */
    public void announce(ControlMessage message) {
        try {
            cluster.multicastOn(Channels.CONTROL, null,
                    OBJECT_MAPPER.writeValueAsString(message).getBytes(StandardCharsets.UTF_8),
                    true);
        } catch (Exception e) {
            log.error("Could not announce {}: {}", message, e.getMessage());
        }
    }

    @Override
    public void onPayload(Node sender, byte[] content) {
        ControlMessage message;
        try {
            message = OBJECT_MAPPER.readValue(content, ControlMessage.class);
        } catch (Exception e) {
            log.warn("Discarded an unreadable control message from {}: {}", sender.label(),
                    e.getMessage());
            return;
        }
        try {
            // a flag, not work: whatever this does has to be over in microseconds, because it is
            // running on the thread every other component's messages are waiting on
            handler.accept(message);
        } catch (RuntimeException e) {
            log.error("Could not apply {}: {}", message, e.getMessage(), e);
        }
    }

}
