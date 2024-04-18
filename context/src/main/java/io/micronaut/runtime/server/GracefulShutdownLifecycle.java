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
package io.micronaut.runtime.server;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.context.LifeCycle;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Interface implemented by beans that support graceful shutdown.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
public interface GracefulShutdownLifecycle {

    /**
     * Trigger a graceful shutdown. The returned {@link CompletionStage} will complete when the
     * shutdown is complete, i.e. a normal {@link LifeCycle#stop()} will not interrupt any
     * processes.
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
     * @return The current shutdown progress, or {@link Optional#empty()} if the shutdown is
     * complete or no state can be reported
     */
    @NonNull
    default Optional<ShutdownState> reportShutdownState() {
        return Optional.empty();
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
    static CompletionStage<?> shutdownAll(@NonNull Stream<? extends GracefulShutdownLifecycle> stages) {
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

    /**
     * State of a graceful shutdown operation.
     */
    sealed interface ShutdownState {
    }

    /**
     * Complex shutdown state composed of many {@link ShutdownState}s. This is used e.g. to report
     * state of multiple open connections.
     *
     * @param members The member states
     */
    record CombinedShutdownState(
        @JsonValue @NonNull Map<String, ShutdownState> members) implements ShutdownState {
        private static final int MAX_REPORT_ENTRIES = 20;

        /**
         * Combine the state of multiple {@link GracefulShutdownLifecycle}s into a
         * {@link CombinedShutdownState}.
         *
         * @param parts         The individual {@link GracefulShutdownLifecycle}s
         * @param key           The function to create the {@link #members} map key
         * @param overflowValue Entry to add to the {@link #members} when there are too many entries
         * @param <G>           The {@link GracefulShutdownLifecycle} type
         * @return The combined state, or {@link Optional#empty()} if none of the inputs reported
         * any state
         */
        @NonNull
        public static <G extends GracefulShutdownLifecycle> Optional<ShutdownState> combineShutdownState(@NonNull Collection<? extends G> parts, @NonNull Function<G, String> key, @NonNull IntFunction<Map.Entry<String, ShutdownState>> overflowValue) {
            Map<String, ShutdownState> memberStates = CollectionUtils.newLinkedHashMap(Math.min(MAX_REPORT_ENTRIES, parts.size()));
            int remaining = parts.size();
            for (G part : parts) {
                remaining--;
                Optional<ShutdownState> shutdownState = part.reportShutdownState();
                if (shutdownState.isEmpty()) {
                    continue;
                }
                String k = key.apply(part);
                memberStates.put(k, shutdownState.get());
                if (memberStates.size() >= MAX_REPORT_ENTRIES) {
                    Map.Entry<String, ShutdownState> o = overflowValue.apply(remaining);
                    memberStates.put(o.getKey(), o.getValue());
                    break;
                }
            }
            if (memberStates.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CombinedShutdownState(memberStates));
        }
    }

    /**
     * A single shutdown state.
     *
     * @param description A readable description of the current state
     */
    record SingleShutdownState(@JsonValue @NonNull String description) implements ShutdownState {
    }
}

class LogHolder {
    static final Logger LOG = LoggerFactory.getLogger(GracefulShutdownLifecycle.class);
}
