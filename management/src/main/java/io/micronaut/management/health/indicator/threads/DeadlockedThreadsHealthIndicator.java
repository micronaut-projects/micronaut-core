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
import io.micronaut.context.condition.NotInNativeImage;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.AbstractHealthIndicator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.annotation.Liveness;
import jakarta.inject.Singleton;

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
@Requires(condition = NotInNativeImage.class)
@Requires(property = HealthEndpoint.PREFIX + ".deadlocked-threads.enabled", notEquals = StringUtils.FALSE)
@Requires(beans = HealthEndpoint.class)
@Internal
class DeadlockedThreadsHealthIndicator extends AbstractHealthIndicator {

    private static final String NAME = "deadlockedThreads";
    private static final String KEY_THREAD_ID = "threadId";
    private static final String KEY_THREAD_NAME = "threadName";
    private static final String KEY_THREAD_STATE = "threadState";
    private static final String KEY_DAEMON = "daemon";
    private static final String KEY_PRIORITY = "priority";
    private static final String KEY_SUSPENDED = "suspended";
    private static final String KEY_IN_NATIVE = "inNative";
    private static final String KEY_LOCK_NAME = "lockName";
    private static final String KEY_LOCK_OWNER_NAME = "lockOwnerName";
    private static final String KEY_LOCK_OWNER_ID = "lockOwnerId";
    private static final String KEY_LOCKED_SYNCHRONIZERS = "lockedSynchronizers";
    private static final String KEY_STACK_TRACE = "stackTrace";

    @Override
    protected Object getHealthInformation() {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreads = null;
            if (threadMXBean.isSynchronizerUsageSupported()) {
                deadlockedThreads = threadMXBean.findDeadlockedThreads();
            } else if (threadMXBean.isObjectMonitorUsageSupported()) {
                deadlockedThreads = threadMXBean.findMonitorDeadlockedThreads();
            }
            if (deadlockedThreads == null) {
                this.healthStatus = HealthStatus.UP;
                return null;
            }
            this.healthStatus = HealthStatus.DOWN;
            return Arrays.stream(threadMXBean.getThreadInfo(deadlockedThreads, true, true, Integer.MAX_VALUE))
                    .map(DeadlockedThreadsHealthIndicator::getDetails)
                    .toList();
    }

    @Override
    public String getName() {
        return NAME;
    }

    private static Map<String, Object> getDetails(ThreadInfo threadInfo) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(KEY_THREAD_ID, String.valueOf(threadInfo.getThreadId()));
        details.put(KEY_THREAD_NAME, threadInfo.getThreadName());
        details.put(KEY_THREAD_STATE, threadInfo.getThreadState().name());
        details.put(KEY_DAEMON, String.valueOf(threadInfo.isDaemon()));
        details.put(KEY_PRIORITY, String.valueOf(threadInfo.getPriority()));
        details.put(KEY_SUSPENDED, String.valueOf(threadInfo.isSuspended()));
        details.put(KEY_IN_NATIVE, String.valueOf(threadInfo.isInNative()));
        details.put(KEY_LOCK_NAME, threadInfo.getLockName());
        details.put(KEY_LOCK_OWNER_NAME, threadInfo.getLockOwnerName());
        details.put(KEY_LOCK_OWNER_ID, String.valueOf(threadInfo.getLockOwnerId()));
        details.put(KEY_LOCKED_SYNCHRONIZERS, Arrays.stream(threadInfo.getLockedSynchronizers()).map(String::valueOf).toList());
        details.put(KEY_STACK_TRACE, formatStackTrace(threadInfo));
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
