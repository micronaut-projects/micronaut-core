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

/**
 * <p>Strategy interface for retrieving and updating the current {@link HealthStatus} of the application.</p>
 * <p>
 * <p>Implementations of this class should be thread safe</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface CurrentHealthStatus {

    /**
     * @return The current {@link HealthStatus} of the server
     */
    HealthStatus current();

    /**
     * Updates the {@link HealthStatus} of the application.
     *
     * @param newStatus The new status
     * @return The previous {@link HealthStatus}
     */
    HealthStatus update(HealthStatus newStatus);
}
