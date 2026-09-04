/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.close.shutdowncreated;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * A singleton that is only created from within {@link ShutdownCreator}'s {@code @PreDestroy} hook,
 * i.e. after the context has already snapshotted its singletons for destruction.
 */
@Requires(property = "spec.name", value = "CreatedDuringShutdownSpec")
@Singleton
public class LateSingleton {

    @PreDestroy
    void close() {
        CreatedDuringShutdownSpec.getDestroyed().add(LateSingleton.class);
    }
}
