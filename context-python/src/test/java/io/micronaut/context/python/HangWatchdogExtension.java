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
package io.micronaut.context.python;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dumps every thread's stack when one test runs for longer than a few minutes, so a build that
 * hangs inside GraalPy (a thread stuck in a context, a Truffle safepoint waiting for it) explains
 * itself in the CI log instead of timing out silently hours later.
 */
public final class HangWatchdogExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final long LIMIT_MINUTES = 4;
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "test-hang-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(HangWatchdogExtension.class);

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        String name = context.getDisplayName();
        ScheduledFuture<?> alarm = WATCHDOG.schedule(() -> dump(name), LIMIT_MINUTES, TimeUnit.MINUTES);
        context.getStore(NAMESPACE).put("alarm", alarm);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        ScheduledFuture<?> alarm = context.getStore(NAMESPACE).remove("alarm", ScheduledFuture.class);
        if (alarm != null) {
            alarm.cancel(false);
        }
    }

    private static void dump(String test) {
        StringBuilder report = new StringBuilder();
        report.append("Test [").append(test).append("] has been running for ").append(LIMIT_MINUTES).append(" minutes; thread dump:\n");
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            report.append('"').append(thread.getName()).append("\" daemon=").append(thread.isDaemon()).append(" state=").append(thread.getState()).append('\n');
            for (StackTraceElement frame : entry.getValue()) {
                report.append("    at ").append(frame).append('\n');
            }
        }
        System.err.println(report);
        System.err.flush();
    }
}
