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
