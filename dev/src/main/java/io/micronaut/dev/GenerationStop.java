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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.runtime.EmbeddedApplication;

/**
 * Stops the generations of the development runtime.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class GenerationStop {

    private GenerationStop() {
    }

    /**
     * Stops a generation as the shutdown hook {@code Micronaut.run} registers does: through the embedded
     * applications it started, then the context. The HTTP server's {@code stop()} holds the server's lock
     * while it stops the context, so stopping the context first, which stops the server bean under the
     * context's lock, takes the two locks in the opposite order; when the JVM exits, both hooks run at once
     * and would deadlock.
     *
     * @param generation The generation
     */
    static void stop(ApplicationContext generation) {
        // only the applications already created: looking one up must not create it while stopping. Each is stopped
        // whether it reports running or not, as the hook does: the hook's stop clears the flag before it stops the
        // context, and stopping the application waits for it, where stopping the context would deadlock with it
        for (BeanRegistration<EmbeddedApplication> registration : generation.getActiveBeanRegistrations(EmbeddedApplication.class)) {
            registration.getBean().stop();
        }
        if (generation.isRunning()) {
            generation.stop();
        }
    }
}
