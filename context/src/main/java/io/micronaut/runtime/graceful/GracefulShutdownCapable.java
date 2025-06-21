/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.runtime.graceful;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Interface implemented by beans that support graceful shutdown.
 *
 * @author Jonas Konrad
 * @since 4.9.0
 */
public interface GracefulShutdownCapable {

    /**
     * Trigger a graceful shutdown. The returned {@link CompletionStage} will complete when the
     * shutdown is complete.
     * <p>
     * Note that the completion of the returned future may be user-dependent. If a user does not
     * close their connection, the future may never terminate. Always add a timeout for a hard
     * shutdown.
     * <p>
     * This method should not throw an exception, nor should the returned stage complete
     * exceptionally. Just log an error instead.
     *
     * @return A future that completes when this bean is fully shut down
     */
    @NonNull
    CompletionStage<?> shutdownGracefully();

    /**
     * After a call to {@link #shutdownGracefully()} report the state of the shutdown. If
     * {@link #shutdownGracefully()} has not been called the behavior of this method is undefined.
     *
     * @return The current number of still-active tasks before the shutdown completes, or
     * {@link Optional#empty()} if no state can be reported
     */
    default OptionalLong reportActiveTasks() {
        return OptionalLong.empty();
    }

    /**
     * Combine the given futures.
     *
     * @param stages The input futures
     * @return A future that completes when all inputs have completed
     */
    @NonNull
    static CompletionStage<?> allOf(@NonNull Stream<CompletionStage<?>> stages) {
        return CompletableFuture.allOf(stages.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new));
    }

    /**
     * Shutdown all the given lifecycles.
     *
     * @param stages The input lifecycles
     * @return A future that completes when all inputs have completed shutdown
     */
    @NonNull
    static CompletionStage<?> shutdownAll(@NonNull Stream<? extends GracefulShutdownCapable> stages) {
        return CompletableFuture.allOf(stages.map(l -> {
            CompletionStage<?> s;
            try {
                s = l.shutdownGracefully();
            } catch (Exception e) {
                LogHolder.LOG.warn("Exception when attempting graceful shutdown", e);
                return CompletableFuture.completedFuture(null);
            }
            return s.toCompletableFuture();
        }).toArray(CompletableFuture[]::new));
    }

    @NonNull
    static OptionalLong combineActiveTasks(@NonNull Iterable<? extends GracefulShutdownCapable> delegates) {
        long sum = 0;
        boolean anyPresent = false;
        for (GracefulShutdownCapable delegate : delegates) {
            OptionalLong r = delegate.reportActiveTasks();
            if (r.isPresent()) {
                anyPresent = true;
                sum += r.getAsLong();
            }
        }
        return anyPresent ? OptionalLong.of(sum) : OptionalLong.empty();
    }
}

@Internal
final class LogHolder {
    static final Logger LOG = LoggerFactory.getLogger(GracefulShutdownCapable.class);
}
