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

package com.github.greenfinger.cluster.leader;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import com.chaconneai.openspreader.cluster.SelfRegisteringListener;
import com.chaconneai.spreader.GossipCluster;
import com.chaconneai.spreader.Node;
import com.chaconneai.spreader.event.GossipListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.cluster.Channels;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * One request, to whoever holds the cluster port, and the answer back -- the shape
 * {@code CacheService} uses for its own writes: read {@link GossipCluster#leader()} at the moment
 * of the call, run it locally if that is this node, otherwise unicast and wait. Nothing caches who
 * the leader is, because leadership moves.
 *
 * <p>
 * Three transient failures are retried by reading the leader again: nobody holds the port mid
 * election, the message is lost, or the node answers that it is no longer leader. A handler that
 * threw for its own reason -- a name already taken -- is not, since the answer would be the same.
 *
 * <p>
 * The request id matches a reply to its caller and is reused across retries so the leader can spot
 * a message it has seen. Every operation here is safe to repeat, which is what makes at-least-once
 * acceptable.
 *
 * @Description: LeaderChannel
 * @Author: Fred Feng
 * @Date: 22/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class LeaderChannel
        implements LeaderGateway, GossipListener, SelfRegisteringListener, ManagedBeanLifeCycle {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GossipCluster cluster;
    private final long timeoutMs;
    private final int maxAttempts;

    private final Map<String, Handler<?>> handlers = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Message>> waiting = new ConcurrentHashMap<>();
    private final AtomicLong requestIds = new AtomicLong();

    public LeaderChannel(GossipCluster cluster, long timeoutMs, int maxAttempts) {
        this.cluster = cluster;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
    }

    /**
     * One shape for both directions, with a field saying which it is -- both travel on one channel,
     * and inferring the direction from which fields are present works until somebody adds one.
     *
     * @param ask       true for a request, false for the answer to one
     * @param notLeader set on an answer, and told apart from an ordinary failure because only
     *                  this one is worth asking somebody else about
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(long id, boolean ask, String operation, boolean ok, boolean notLeader,
            String error, String payload) {

        static Message request(long id, String operation, String payload) {
            return new Message(id, true, operation, false, false, null, payload);
        }

        static Message answer(long id, String payload) {
            return new Message(id, false, null, true, false, null, payload);
        }

        static Message refused(long id, String error, boolean notLeader) {
            return new Message(id, false, null, false, notLeader, error, null);
        }
    }

    private record Handler<A>(Class<A> argType, Function<A, ?> action) {

        Object apply(ObjectMapper mapper, String payload) throws Exception {
            A argument = payload == null ? null : mapper.readValue(payload, argType);
            return action.apply(argument);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cluster.addListener(Channels.LEADER, this);
    }

    @Override
    public void destroy() throws Exception {
        cluster.removeListener(this);
        waiting.values()
                .forEach(future -> future.completeExceptionally(
                        new WebCrawlerException("This node is stopping")));
        waiting.clear();
    }

    @Override
    public <A, R> void handle(String operation, Class<A> argType, Function<A, R> handler) {
        handlers.put(operation, new Handler<>(argType, handler));
    }

    @Override
    public boolean isLeader() {
        return cluster.isLeader();
    }

    @Override
    public <T> T onLeader(String operation, Object request, Class<T> type) {
        long id = requestIds.incrementAndGet();
        String payload = encode(request);
        WebCrawlerException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Node leader = cluster.leader();
            if (leader == null) {
                last = new WebCrawlerException(
                        "The cluster has no leader at the moment, so '" + operation
                                + "' cannot be performed. It is being elected; try again.");
                pause(attempt);
                continue;
            }
            Message message = Message.request(id, operation, payload);
            if (leader.id().equals(cluster.self().id())) {
                // this node performs the writes: a plain call, no network, no serialising
                return decode(answerOf(perform(message)), type);
            }
            try {
                return decode(askLeader(leader, message), type);
            } catch (Retryable e) {
                last = new WebCrawlerException(e.getMessage());
                log.info("'{}' will be asked again: {}", operation, e.getMessage());
                pause(attempt);
            }
        }
        throw last != null ? last
                : new WebCrawlerException("'" + operation + "' could not be performed");
    }

    /**
     * Runs the operation here, which is what the leader does with everything that arrives.
     *
     * @return the reply, whose {@code ok} says whether the handler ran or threw
     */
    Message perform(Message request) {
        if (!cluster.isLeader()) {
            // leadership moved between the sender reading it and this message arriving
            return Message.refused(request.id(), "No longer the leader", true);
        }
        Handler<?> handler = handlers.get(request.operation());
        if (handler == null) {
            // a node running an older build, asked for something it has never heard of
            return Message.refused(request.id(),
                    "Unknown operation '" + request.operation() + "'", false);
        }
        try {
            Object result = handler.apply(OBJECT_MAPPER, request.payload());
            return Message.answer(request.id(), encode(result));
        } catch (Exception e) {
            // the handler's own refusal, which the caller asked for and should be given
            log.warn("'{}' failed here: {}", request.operation(), e.getMessage());
            return Message.refused(request.id(), messageOf(e), false);
        }
    }

    /** A reply that refused is the caller's answer, not a value to be read. */
    private static String answerOf(Message reply) {
        if (reply.notLeader()) {
            throw new Retryable(reply.error());
        }
        if (!reply.ok()) {
            throw new WebCrawlerException(reply.error());
        }
        return reply.payload();
    }

    private String askLeader(Node leader, Message request) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        waiting.put(request.id(), future);
        try {
            byte[] bytes = OBJECT_MAPPER.writeValueAsString(request)
                    .getBytes(StandardCharsets.UTF_8);
            if (!cluster.unicastOn(Channels.LEADER, leader, bytes)) {
                throw new Retryable("it did not reach " + leader.label());
            }
            return answerOf(future.get(timeoutMs, TimeUnit.MILLISECONDS));
        } catch (Retryable | WebCrawlerException e) {
            throw e;
        } catch (TimeoutException e) {
            throw new Retryable(leader.label() + " did not answer within " + timeoutMs + "ms");
        } catch (Exception e) {
            throw new WebCrawlerException("Could not ask the leader: " + messageOf(e), e);
        } finally {
            waiting.remove(request.id());
        }
    }

    @Override
    public void onPayload(Node sender, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        try {
            Message message = OBJECT_MAPPER.readValue(text, Message.class);
            if (message.ask()) {
                Message reply = perform(message);
                cluster.unicastOn(Channels.LEADER, sender,
                        OBJECT_MAPPER.writeValueAsString(reply).getBytes(StandardCharsets.UTF_8));
                return;
            }
            CompletableFuture<Message> future = waiting.remove(message.id());
            if (future != null) {
                future.complete(message);
            }
            // no future: the caller gave up waiting, and the leader did the work anyway. Harmless,
            // because everything behind this gateway can be repeated
        } catch (Exception e) {
            log.warn("Discarded an unreadable leader message from {}: {}", sender.label(),
                    e.getMessage());
        }
    }

    private String encode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new WebCrawlerException("Could not encode the request", e);
        }
    }

    private <T> T decode(String text, Class<T> type) {
        if (text == null || Void.class.equals(type)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, type);
        } catch (Exception e) {
            throw new WebCrawlerException("Could not read the leader's answer", e);
        }
    }

    /**
     * Between attempts, and growing: an election settles in a second or two, and asking three
     * times inside one millisecond only uses up the attempts before anything can have changed.
     */
    private void pause(int attempt) {
        try {
            Thread.sleep(Math.min(1000L, 100L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebCrawlerException("Interrupted while waiting for the leader");
        }
    }

    private static String messageOf(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Worth asking again, of whoever is the leader now. */
    private static final class Retryable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Retryable(String message) {
            super(message);
        }
    }

}
