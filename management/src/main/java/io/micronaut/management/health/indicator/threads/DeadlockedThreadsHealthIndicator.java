/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.management.health.indicator.threads;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.annotation.Liveness;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>A {@link HealthIndicator} that uses the {@link ThreadMXBean} to check for deadlocked threads.
 * Returns {@link HealthStatus#DOWN} if any are found and their {@link ThreadInfo} in the details.</p>
 *
 * @author Andreas Brenk
 * @since 4.8.0
 */
@Singleton
@Liveness
@Requires(property = HealthEndpoint.PREFIX + ".deadlocked-threads.enabled", notEquals = StringUtils.FALSE)
@Requires(beans = HealthEndpoint.class)
public class DeadlockedThreadsHealthIndicator implements HealthIndicator {

    private static final String NAME = "deadlockedThreads";

    @Override
    public Publisher<HealthResult> getResult() {
        HealthResult.Builder builder = HealthResult.builder(NAME);

        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

            if (deadlockedThreads == null) {
                builder.status(HealthStatus.UP);
            } else {
                builder.status(HealthStatus.DOWN);
                builder.details(
                    Arrays.stream(threadMXBean.getThreadInfo(deadlockedThreads, true, true, Integer.MAX_VALUE))
                        .map(DeadlockedThreadsHealthIndicator::getDetails)
                        .toList());
            }
        } catch (Exception e) {
            builder.status(HealthStatus.UNKNOWN);
            builder.exception(e);
        }

        return Publishers.just(builder.build());
    }

    private static Map<String, Object> getDetails(ThreadInfo threadInfo) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("threadId", String.valueOf(threadInfo.getThreadId()));
        details.put("threadName", threadInfo.getThreadName());
        details.put("threadState", threadInfo.getThreadState().name());
        details.put("daemon", String.valueOf(threadInfo.isDaemon()));
        details.put("priority", String.valueOf(threadInfo.getPriority()));
        details.put("suspended", String.valueOf(threadInfo.isSuspended()));
        details.put("inNative", String.valueOf(threadInfo.isInNative()));
        details.put("lockName", threadInfo.getLockName());
        details.put("lockOwnerName", threadInfo.getLockOwnerName());
        details.put("lockOwnerId", String.valueOf(threadInfo.getLockOwnerId()));
        details.put("lockedSynchronizers", Arrays.stream(threadInfo.getLockedSynchronizers()).map(String::valueOf).toList());
        details.put("stackTrace", formatStackTrace(threadInfo));

        return details;
    }

    private static String formatStackTrace(ThreadInfo threadInfo) {
        StringBuilder sb = new StringBuilder();

        int i = 0;
        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        for (; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append(ste.toString());
            sb.append('\n');

            if (i == 0 && threadInfo.getLockInfo() != null) {
                switch (threadInfo.getThreadState()) {
                    case BLOCKED:
                        sb.append("-  blocked on ");
                        sb.append(threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING, TIMED_WAITING:
                        sb.append("-  waiting on ");
                        sb.append(threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("-  locked ");
                    sb.append(mi);
                    sb.append('\n');
                }
            }
        }

        return sb.toString();
    }
}
