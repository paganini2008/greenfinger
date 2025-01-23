package com.github.greenfinger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import com.github.doodler.common.utils.SingleObservable;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 
 * @Description: CatalogDelayQueue
 * @Author: Fred Feng
 * @Date: 23/01/2025
 * @Version 1.0.0
 */
public class CatalogDelayQueue {

    private static final String NAME = "crawler";
    private final Queue<Action> actions = new ConcurrentLinkedQueue<>();
    private final SingleObservable observable = new SingleObservable(true);

    CatalogDelayQueue(Function<Action, Void> f) {
        observable.addObserver(NAME, (ob, arg) -> {
            f.apply((Action) arg);
        });
    }

    public void rebuild(long catalogId) {
        Action action = new Action(catalogId, "rebuild");
        if (!actions.contains(action)) {
            actions.add(action);
        }
    }

    public void crawl(long catalogId) {
        Action action = new Action(catalogId, "crawl");
        if (!actions.contains(action)) {
            actions.add(action);
        }
    }

    public void runNext() {
        Action nextAction = actions.poll();
        if (nextAction != null) {
            observable.notifyObservers(NAME, nextAction);
        }
    }

    @Data
    @AllArgsConstructor
    static class Action {

        private long catalogId;
        private String action;
    }

}
