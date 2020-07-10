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
package io.micronaut.runtime.event.annotation;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Async;
import io.reactivex.Completable;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class AsyncListener {

    boolean invoked = false;
    boolean completableInvoked = false;

    @EventListener
    @Async
    CompletableFuture<Boolean> onStartup(StartupEvent event) {
        try {
            Thread.currentThread().sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        invoked = true;
        return CompletableFuture.completedFuture(invoked);
    }

    @EventListener
    @Async
    Completable onCompletableStartup(StartupEvent event) {
        try {
            Thread.currentThread().sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        completableInvoked = true;
        return Completable.complete();
    }

    public boolean isInvoked() {
        return invoked;
    }
}
