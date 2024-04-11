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

import io.micronaut.context.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    CompletionStage<?> shutdownGracefully();

    /**
     * Combine the given futures.
     *
     * @param stages The input futures
     * @return A future that completes when all inputs have completed
     */
    static CompletionStage<?> allOf(Stream<CompletionStage<?>> stages) {
        return CompletableFuture.allOf(stages.map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new));
    }

    /**
     * Shutdown all the given lifecycles.
     *
     * @param stages The input lifecycles
     * @return A future that completes when all inputs have completed shutdown
     */
    static CompletionStage<?> shutdownAll(Stream<? extends GracefulShutdownLifecycle> stages) {
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
}

class LogHolder {
    static final Logger LOG = LoggerFactory.getLogger(GracefulShutdownLifecycle.class);
}
