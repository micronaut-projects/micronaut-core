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
package io.micronaut.context;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;

/**
 * A life cycle interface providing a start method and extending Closeable which provides a close() method for
 * termination.
 * <p>
 * Components can implement this interface
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface LifeCycle<T extends LifeCycle> extends Closeable, AutoCloseable {

    /**
     * @return Whether the component is running
     */
    boolean isRunning();

    /**
     * Starts the lifecyle component.
     *
     * @return This lifecycle component
     */
    default @NonNull T start() {
        return (T) this;
    }

    /**
     * Stops the life cycle component.
     *
     * @return This lifecycle component
     */
    default @NonNull T stop() {
        return (T) this;
    }

    /**
     * Delegates to {@link #stop()}.
     */
    @Override
    default void close() {
        stop();
    }

    /**
     * Refreshes the current life cycle object. Effectively this calls {@link #stop()} followed by {@link #start()}.
     *
     * @return This lifecycle component
     */
    default @NonNull T refresh() {
        stop();
        start();
        return (T) this;
    }
}
