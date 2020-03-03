/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.tracing.brave;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;

public class DeadlockDetection implements Runnable {

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(Duration.ofMinutes(5).toMillis());
            } catch (InterruptedException e) {
                break;
            }

            ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
            long[] ids = tmx.findDeadlockedThreads();
            if (ids != null) {
                ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
                System.out.println("The following threads are deadlocked:");
                BuildDebug.dumpThreadInfos(infos);
            }
        }
    }
}
