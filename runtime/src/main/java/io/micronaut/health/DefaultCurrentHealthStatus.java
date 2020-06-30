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
package io.micronaut.health;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The default health status stores the values in memory.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
class DefaultCurrentHealthStatus implements CurrentHealthStatus {

    private final AtomicReference<HealthStatus> current = new AtomicReference<>(HealthStatus.UP);

    @Override
    public HealthStatus current() {
        return current.get();
    }

    @Override
    public HealthStatus update(HealthStatus newStatus) {
        if (newStatus != null) {
            return current.getAndSet(newStatus);
        }
        return current.get();
    }
}
