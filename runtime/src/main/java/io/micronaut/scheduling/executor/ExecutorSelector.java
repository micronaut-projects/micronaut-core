/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.scheduling.executor;

import io.micronaut.inject.MethodReference;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Interface that allows customizing the selection of the {@link ExecutorService} to run an operation on.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ExecutorSelector {

    /**
     * Select an {@link ExecutorService} for the given {@link MethodReference}.
     *
     * @param method The {@link MethodReference}
     * @param threadSelection The thread selection mode
     * @return An optional {@link ExecutorService}. If an {@link ExecutorService} cannot be established
     * {@link Optional#empty()} is returned
     */
    Optional<ExecutorService> select(MethodReference method, ThreadSelection threadSelection);
}
