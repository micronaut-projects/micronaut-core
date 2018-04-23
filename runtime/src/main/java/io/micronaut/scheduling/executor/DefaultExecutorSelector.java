/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.annotation.NonBlocking;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.inject.MethodReference;
import io.micronaut.scheduling.TaskExecutors;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation of the {@link ExecutorSelector} interface that regards methods that return reactive types as non-blocking.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultExecutorSelector implements ExecutorSelector {

    private final ExecutorService ioExecutor;

    /**
     * Construct a default implementation for the given executor service for asynchronous IO tasks.
     *
     * @param ioExecutor A service that provide method to manager termination and produce future for tracking
     *                   progress of one or more asynchronous IO tasks.
     */
    protected DefaultExecutorSelector(@Named(TaskExecutors.IO) ExecutorService ioExecutor) {
        this.ioExecutor = ioExecutor;
    }

    @Override
    public Optional<ExecutorService> select(MethodReference method) {
        if (method.hasStereotype(NonBlocking.class)) {
            return Optional.empty();
        } else {
            Class returnType = method.getReturnType().getType();
            if (Publishers.isConvertibleToPublisher(returnType) || CompletableFuture.class.isAssignableFrom(returnType)) {
                return Optional.empty();
            }
        }
        return Optional.of(ioExecutor);
    }
}
