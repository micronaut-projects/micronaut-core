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
package io.micronaut.scheduling;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(property = "spec.name", value = "ScheduledFixedRateSpecMyTask")
@Requires(property = "scheduled-test.task.enabled", value = StringUtils.TRUE)
public class MyJavaTask {
    private boolean wasRun = false;
    AtomicInteger cronEvents = new AtomicInteger(0);

    @Scheduled(fixedRate = "10ms")
    public void runSomething() {
        wasRun = true;
    }

    @Scheduled(fixedRate = "5s")
    @Scheduled(fixedRate = "6s")
    void runCron() {
        cronEvents.incrementAndGet();
    }

    public boolean isWasRun() {
        return wasRun;
    }
}
