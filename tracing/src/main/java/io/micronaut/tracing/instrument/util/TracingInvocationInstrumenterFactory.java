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
package io.micronaut.tracing.instrument.util;

import io.micronaut.scheduling.instrument.InvocationInstrumenter;

import javax.annotation.Nullable;

/**
 * An factory interface for tracing invocation instrumentation, factory method decides if instrumentation is needed.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
public interface TracingInvocationInstrumenterFactory {

    /**
     * An optional instrumentation.
     * @return An instrumentation or null if non is necessary.
     */
    @Nullable InvocationInstrumenter newTracingInvocationInstrumenter();

}
