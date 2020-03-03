/*
 * Copyright 2017-2020 original authors
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

/**
 * Enum the controls the ability to select threads in a Micronaut application.
 *
 * @since 1.3
 * @author graemerocher
 */
public enum ThreadSelection {
    /**
     * Automatically select the thread to run operations on based on the return type and/or {@link io.micronaut.core.annotation.Blocking} or {@link io.micronaut.core.annotation.NonBlocking} annotations.
     *
     * <p>This is the default strategy in 1.x and will run operations on the I/O thread pool if the return type
     * of the method is not a reactive top and the method is not annotated with {@link io.micronaut.core.annotation.NonBlocking}</p>
     *
     * <p>If the return type is a reactive type and the method is not annotated with {@link io.micronaut.core.annotation.Blocking} then the server event loop thread will used to run the operation.</p>
     */
    AUTO,
    /**
     * Manual selection leaves it up to the user code to spawn threads to run any blocking I/O operations. This could be
     * via an appropriate call to {@code subscribeOn(..)} or by using the {@link io.micronaut.scheduling.annotation.Async} annotation or whatever mechanism the user chooses to spawn the additional thread.
     */
    MANUAL,
    /**
     * I/O selection will run all operations regardless of return type and annotations on the I/O thread pool and will never schedule an operation on the server event loop thread.
     */
    IO
}
