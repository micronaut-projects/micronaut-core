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
package io.micronaut.http.server.routing;

import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.List;
import java.util.Optional;

/**
 * Cost gate for preserving the HTTP pending marker with a delegate to core composition.
 * Returning the result models publication to a later binding phase and prevents scalar replacement.
 */
@State(Scope.Thread)
public class PendingCompositionBenchmark {
    @Param({"HTTP", "DELEGATE"})
    public String implementation;

    @Param({"true", "false"})
    public boolean pending;

    private static final Optional<String> VALUE = Optional.of("value");
    private static final BindingResult<String> CHILD = BindingResult.converted("child");

    @Benchmark
    public BindingResult<String> compose() {
        BindingResult<String> result = implementation.equals("HTTP")
            ? new HttpSource(pending).flatMap(value -> CHILD)
            : new Delegate<>(new CoreSource(pending).flatMap(value -> CHILD));
        result.isPending();
        result.getConversionErrors();
        result.getValue();
        return result;
    }

    private record HttpSource(boolean isPending) implements PendingRequestBindingResult<String> {
        @Override
        public Optional<String> getValue() {
            return isPending ? Optional.empty() : VALUE;
        }
    }

    private record CoreSource(boolean isPending) implements BindingResult<String> {
        @Override
        public Optional<String> getValue() {
            return isPending ? Optional.empty() : VALUE;
        }
    }

    private record Delegate<T>(BindingResult<T> delegate) implements PendingRequestBindingResult<T> {
        @Override
        public boolean isPending() {
            return delegate.isPending();
        }

        @Override
        public boolean isSatisfied() {
            return delegate.isSatisfied();
        }

        @Override
        public Optional<T> getValue() {
            return delegate.getValue();
        }

        @Override
        public List<ConversionError> getConversionErrors() {
            return delegate.getConversionErrors();
        }

        @Override
        public boolean isConvertedToArgumentType() {
            return delegate.isConvertedToArgumentType();
        }
    }
}
