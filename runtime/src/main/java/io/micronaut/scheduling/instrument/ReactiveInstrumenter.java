/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.scheduling.instrument;

import io.micronaut.core.annotation.Indexed;

import java.util.Optional;

/**
 * An interface for reactive instrumentation where the instrumenter is initialized a head of time
 * at the point where state is available.
 *
 * @author graemerocher
 * @since 1.1
 * @deprecated Use {@link InvocationInstrumenter} and {@link ReactiveInvocationInstrumenterFactory} instead.
 */
@Indexed(ReactiveInstrumenter.class)
@Deprecated
public interface ReactiveInstrumenter {

    /**
     * An optional instrumentation.
     * @return An optional instrumentation.
     */
    Optional<RunnableInstrumenter> newInstrumentation();
}
