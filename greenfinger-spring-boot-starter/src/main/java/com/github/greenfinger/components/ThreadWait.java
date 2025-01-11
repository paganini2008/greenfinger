package com.github.greenfinger.components;

import com.github.doodler.common.utils.ThreadUtils;

/**
 * 
 * @Description: ThreadWaitType
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public enum ThreadWait {

    NONE {

        @Override
        public void doWait(long delay) {}

    },
    RANDOM_SLEEP {

        @Override
        public void doWait(long delay) {
            ThreadUtils.randomSleep(100, delay);
        }

    },

    SLEEP {

        @Override
        public void doWait(long delay) {
            ThreadUtils.sleep(delay);
        }

    };

    public abstract void doWait(long delay);

}
