/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.configurations.hystrix;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Context;
import org.particleframework.context.annotation.Factory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class allows hooking into {@link HystrixPlugins} simply by defining beans
 *
 * @author graemerocher
 * @since 1.0
 */
@Context
public class HystrixConfiguration {


    /**
     * Allows defining the {@link HystrixCommandExecutionHook} as a bean
     * @param commandExecutationHook The command execution hook
     */
    @Inject
    void setCommandExecutationHook(@Nullable HystrixCommandExecutionHook commandExecutationHook) {
        if(commandExecutationHook != null) {
            HystrixPlugins instance = HystrixPlugins.getInstance();
            instance.registerCommandExecutionHook(commandExecutationHook);
        }
    }

    /**
     * Allows defining the {@link HystrixEventNotifier} as a bean
     * @param eventNotifier The command execution hook
     */
    @Inject
    void setEventNotifiers(@Nullable HystrixEventNotifier eventNotifier) {
        if(eventNotifier != null) {
            HystrixPlugins instance = HystrixPlugins.getInstance();
            instance.registerEventNotifier(eventNotifier);
        }
    }

    /**
     * Allows defining the {@link com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy} as a bean
     * @param concurrencyStrategy The command execution hook
     */
    @Inject
    void setMetricsPublisher(@Nullable HystrixConcurrencyStrategy concurrencyStrategy) {
        if(concurrencyStrategy != null) {
            HystrixPlugins instance = HystrixPlugins.getInstance();
            instance.registerConcurrencyStrategy(concurrencyStrategy);
        }
    }

    /**
     * Allows defining the {@link HystrixMetricsPublisher} as a bean
     * @param metricsPublisher The command execution hook
     */
    @Inject
    void setMetricsPublisher(@Nullable HystrixMetricsPublisher metricsPublisher) {
        if(metricsPublisher != null) {
            HystrixPlugins instance = HystrixPlugins.getInstance();
            instance.registerMetricsPublisher(metricsPublisher);
        }
    }
}
