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
package io.micronaut.function.client;

import io.micronaut.context.annotation.Primary;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * The default {@link FunctionInvokerChooser}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Primary
class DefaultFunctionInvokerChooser implements FunctionInvokerChooser {

    private final FunctionInvokerChooser[] choosers;

    /**
     * Constructor.
     *
     * @param choosers arrange of function choosers
     */
    DefaultFunctionInvokerChooser(FunctionInvokerChooser[] choosers) {
        this.choosers = choosers;
    }

    @Override
    public <I, O> Optional<FunctionInvoker<I, O>> choose(FunctionDefinition definition) {
        for (FunctionInvokerChooser chooser : choosers) {
            Optional<FunctionInvoker<I, O>> chosen = chooser.choose(definition);
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        return Optional.empty();
    }
}
