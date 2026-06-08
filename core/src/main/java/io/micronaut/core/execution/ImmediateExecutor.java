/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * An executor that immediately executes its tasks.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
public final class ImmediateExecutor implements ConditionalExecutionExecutor {
    public static final Executor INSTANCE = new ImmediateExecutor();

    private static final Logger LOG = LoggerFactory.getLogger(ImmediateExecutor.class);

    private ImmediateExecutor() {
    }

    @Override
    public void execute(Runnable command) {
        try {
            command.run();
        } catch (Exception e) {
            LOG.error("Error in immediate executor", e);
        }
    }

    @Override
    public boolean canExecuteImmediately() {
        return true;
    }
}
