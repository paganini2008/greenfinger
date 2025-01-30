package com.github.greenfinger;

import java.util.function.Function;
import org.springframework.data.redis.core.RedisOperations;
import com.github.doodler.common.utils.SingleObservable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: CatalogDelayQueue
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
@Slf4j
public class CatalogDelayQueue {

    private static final String NAME = "greenfinger:catalog:queue";
    private final RedisOperations<String, Object> redisOperations;
    private final SingleObservable observable = new SingleObservable(true);

    CatalogDelayQueue(RedisOperations<String, Object> redisOperations, Function<Action, Void> f) {
        this.redisOperations = redisOperations;
        observable.addObserver(NAME, (ob, arg) -> {
            f.apply((Action) arg);
        });
    }

    public void rebuild(long catalogId) {
        Action action = new Action(catalogId, "rebuild");
        redisOperations.opsForList().leftPush(NAME, action);
    }

    public void crawl(long catalogId) {
        Action action = new Action(catalogId, "crawl");
        redisOperations.opsForList().leftPush(NAME, action);
    }

    public void runNext() {
        Action nextAction = (Action) redisOperations.opsForList().rightPop(NAME);
        if (nextAction != null) {
            observable.notifyObservers(NAME, nextAction);
            if (log.isInfoEnabled()) {
                log.info("Start running next operation: {}", nextAction);
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {

        private long catalogId;
        private String action;
    }

}
