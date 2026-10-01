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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.OrderUtil;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Combines listeners into one that calls each in turn, and loads the report listeners registered as services.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class TestEventListeners {

    private static final Logger LOG = LoggerFactory.getLogger(TestEventListeners.class);

    private TestEventListeners() {
    }

    /**
     * One listener that calls each of these in order. A listener that throws is logged and the others still
     * receive the event, so a broken report never breaks a run or another report.
     *
     * @param listeners The listeners
     * @return The combined listener
     */
    public static TestEventListener composite(List<? extends TestEventListener> listeners) {
        List<TestEventListener> all = List.copyOf(listeners);
        return new TestEventListener() {
            @Override
            public void runStarted(TestRunStarted event) {
                each(all, listener -> listener.runStarted(event));
            }

            @Override
            public void testStarted(TestId test) {
                each(all, listener -> listener.testStarted(test));
            }

            @Override
            public void output(TestId test, TestOutput stream, String text) {
                each(all, listener -> listener.output(test, stream, text));
            }

            @Override
            public void testFinished(TestId test, TestOutcome outcome) {
                each(all, listener -> listener.testFinished(test, outcome));
            }

            @Override
            public void runFinished(TestRunSummary summary) {
                each(all, listener -> listener.runFinished(summary));
            }
        };
    }

    /**
     * The report listeners registered as services with a loader, in their order.
     *
     * @param classLoader The loader to find them with
     * @return The listeners
     */
    public static List<TestReportListener> reportListeners(ClassLoader classLoader) {
        List<TestReportListener> listeners = new ArrayList<>();
        for (TestReportListener listener : ServiceLoader.load(TestReportListener.class, classLoader)) {
            listeners.add(listener);
        }
        OrderUtil.sort(listeners);
        return listeners;
    }

    private static void each(List<TestEventListener> listeners, Consumer<TestEventListener> call) {
        for (TestEventListener listener : listeners) {
            try {
                call.accept(listener);
            } catch (RuntimeException e) {
                LOG.error("The test listener {} failed: {}", listener.getClass().getName(), e.getMessage(), e);
            }
        }
    }
}
