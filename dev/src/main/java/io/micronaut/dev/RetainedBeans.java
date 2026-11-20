/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.reload.RetentionCriteria;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.loader.GenerationClassLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * What the stop of a generation retained for the next one. The runtime holds it until a generation that has it started:
 * one that fails to start is no reason to destroy it, as correcting a typo would then pay for a new pool, so it goes to
 * the generation after that one, as if the failed one had never been. The failed generation's context is stopped at once
 * instead, since what it created, a pool among it, would otherwise stay alive with nothing left to stop it.
 *
 * @since 5.3.0
 */
@Internal
final class RetainedBeans {

    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);

    private volatile Collection<BeanRegistration<?>> registrations = List.of(); // NOSONAR java:S3077 replaced whole, never mutated once published
    /**
     * The context whose stop retained them, which destroys them should no context adopt them.
     */
    private volatile @Nullable BeanContext from; // NOSONAR java:S3077 only the reference is published; the context is thread-safe
    private volatile int count;
    private boolean taken; // guarded by this: whether a context of the generation was configured with them

    /**
     * @param registration A registration
     * @return Whether it is of a bean of the application, of a class of a generation, which is never retained
     */
    static boolean isStale(BeanRegistration<?> registration) {
        if (registration.getBeanDefinition().getClass().getClassLoader() instanceof GenerationClassLoader) {
            return true;
        }
        Object bean = registration.getBean();
        return bean.getClass().getClassLoader() instanceof GenerationClassLoader;
    }

    /**
     * @return How many a generation was last configured with
     */
    int count() {
        return count;
    }

    /**
     * Stops a running generation, retaining what the criteria retain.
     *
     * @param generation The generation's context
     * @param criteria What it retains
     */
    void stopRetaining(DefaultBeanContext generation, RetentionCriteria criteria) {
        from = generation;
        hold(generation.stopRetaining(criteria));
    }

    /**
     * A new generation launches: the first context it builds is configured with them.
     */
    synchronized void nextGeneration() {
        taken = false;
    }

    /**
     * @return The registrations for the first context of the generation, nothing for any other
     */
    synchronized Collection<BeanRegistration<?>> take() {
        if (taken) {
            return List.of();
        }
        taken = true;
        Collection<BeanRegistration<?>> retained = registrations;
        count = retained.size();
        return retained;
    }

    /**
     * Once a generation's start was judged: a context that started has what it was configured with now, and one that
     * failed to start is stopped, which destroys what it created and leaves them for the next generation, as does a
     * generation whose main failed before any context took them.
     *
     * @param generation The context of the generation, null when it made none
     */
    void settle(@Nullable ApplicationContext generation) {
        boolean given;
        synchronized (this) {
            given = taken;
            taken = false;
        }
        if (generation != null) {
            if (given) {
                hold(stopIfFailed(generation, registrations));
            } else {
                stopIfFailed(generation, List.of());
            }
        }
    }

    /**
     * The generation that ran last did not stop by retaining, as it no longer runs. Stops it if its start failed after it
     * was judged.
     *
     * @param generation Its context
     * @param configurationChanged Whether a configuration file changed since: no context runs to tell what the edit
     *                             changed, and it may be what they were made from, so that it releases them
     */
    void afterGenerationThatDoesNotRun(ApplicationContext generation, boolean configurationChanged) {
        stopIfFailed(generation, List.of());
        if (configurationChanged) {
            destroy();
        }
    }

    /**
     * A start timed out or was interrupted, with a context that may yet start and keeps what it took. What no context
     * took belongs to nobody.
     */
    void abandon() {
        if (take().isEmpty()) {
            hold(List.of());
        } else {
            destroy();
        }
    }

    /**
     * Destroys them: no context will adopt them. The stopped context they came from still knows how to.
     */
    void destroy() {
        Collection<BeanRegistration<?>> retained = registrations;
        BeanContext context = from;
        hold(List.of());
        if (context == null) {
            return;
        }
        for (BeanRegistration<?> registration : retained) {
            try {
                context.destroyBean(registration);
            } catch (RuntimeException e) {
                LOG.debug("Cannot destroy retained bean {}", registration, e);
            }
        }
    }

    private void hold(Collection<BeanRegistration<?>> retained) {
        registrations = retained;
        if (retained.isEmpty()) {
            // nothing is held on to any longer, nor the generation it came from
            from = null;
        }
    }

    /**
     * Stops the context of a generation if its start failed, or if it never started, unless it is still starting on
     * another thread: the context's lock would keep this thread waiting for that.
     *
     * @return What is still retained
     */
    private static Collection<BeanRegistration<?>> stopIfFailed(ApplicationContext generation, Collection<BeanRegistration<?>> retained) {
        if (generation instanceof DefaultBeanContext context && !generation.isRunning() && context.isStoppingOrStopped()) {
            try {
                return context.stopFailedStart(retained);
            } catch (RuntimeException e) {
                LOG.warn("The context of a generation that failed to start could not be stopped: {}", e.getMessage(), e);
                return retained;
            }
        }
        return List.of();
    }
}
