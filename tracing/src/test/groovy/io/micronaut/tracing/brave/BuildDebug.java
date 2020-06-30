/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.tracing.brave;

import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Singleton;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

@Singleton
public class BuildDebug {

    static {
        new Thread(new DeadlockDetection()).run();
    }

    @Scheduled( fixedRate = "5m",
                initialDelay = "5m" )
    void dumpThreads() {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMxBean.getThreadInfo(threadMxBean.getAllThreadIds(), 150);
        dumpThreadInfos(threadInfos);
    }

    static void dumpThreadInfos(ThreadInfo[] threadInfos) {
        StringBuilder dump = new StringBuilder();
        for(ThreadInfo info : threadInfos)
        {
            dump.append('"').append(info.getThreadName()).append('"').append("\n");
            Thread.State state = info.getThreadState();
            dump.append("STATE: ").append(state);
            StackTraceElement[] stes = info.getStackTrace();
            for(StackTraceElement ste : stes)
            {
                dump.append("\n    at ").append(ste);
            }
            dump.append("\n\n");
        }
        System.out.println(dump.toString());
    }
}

