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

