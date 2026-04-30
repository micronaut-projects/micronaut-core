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
package io.micronaut.scheduling.executor;

import io.micronaut.core.execution.ImmediateExecutor;
import io.micronaut.core.execution.ConditionalExecutionExecutor;
import io.micronaut.inject.MethodReference;
import org.jspecify.annotations.Nullable;
import reactor.core.scheduler.NonBlocking;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.Executor;
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
    Optional<ExecutorService> select(@Nullable MethodReference<?, ?> method, ThreadSelection threadSelection);

    /**
     * Obtain executor for the given name.
     * @param name The name of the executor
     * @return An executor if it exists
     * @since 3.1.0
     */
    Optional<ExecutorService> select(String name);

    /**
     * Select an {@link Executor} for the given {@link MethodReference}.
     *
     * @param method The {@link MethodReference}
     * @param configuration The thread selection configuration
     * @return An optional {@link Executor}. If an {@link Executor} cannot be established, an
     * {@link ImmediateExecutor} is returned.
     */
    @SuppressWarnings("resource")
    default Executor selectExecutor(@Nullable MethodReference<?, ?> method, ThreadSelectionConfiguration configuration) {
        ExecutorService es = select(method, configuration.getThreadSelection()).orElse(null);
        if (es == null) {
            return ImmediateExecutor.INSTANCE;
        }
        if (!configuration.isRedispatchNonBlockingOnly()) {
            return es;
        }
        return new ConditionalExecutionExecutor() {
            @Override
            public void execute(Runnable command) {
                if (isOnNonBlockingThread()) {
                    es.execute(command);
                } else {
                    ImmediateExecutor.INSTANCE.execute(command);
                }
            }

            @Override
            public boolean canExecuteImmediately() {
                return !isOnNonBlockingThread();
            }
        };
    }

    private static boolean isOnNonBlockingThread() {
        if (NonBlockingThreadTypeHolder.reactorAvailable) {
            try {
                return ReactorNonBlockingDetector.isOnNonBlockingThread();
            } catch (LinkageError e) {
                NonBlockingThreadTypeHolder.reactorAvailable = false;
            }
        }
        return false;
    }

}

final class NonBlockingThreadTypeHolder {
    static boolean reactorAvailable = true;
}

@SuppressWarnings("ReturnValueIgnored")
final class ReactorNonBlockingDetector {
    static {
        Schedulers.class.getName();
        NonBlocking.class.getName();
    }

    private ReactorNonBlockingDetector() {
    }

    static boolean isOnNonBlockingThread() {
        return Schedulers.isInNonBlockingThread() || Thread.currentThread() instanceof NonBlocking;
    }
}
