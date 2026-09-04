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
package io.micronaut.inject.parallel;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import jakarta.inject.Singleton;

/**
 * Releases {@link SlowShutdownParallelBean}'s constructor once shutdown has started, so that the
 * bean is guaranteed to finish construction after the context began stopping.
 */
@Singleton
@Requires(property = "parallel.shutdown.bean.enabled")
public class ParallelShutdownReleaseListener implements ApplicationEventListener<ShutdownEvent> {

    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        SlowShutdownParallelBean.RELEASE.countDown();
    }
}
