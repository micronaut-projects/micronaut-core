/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.tracing;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.noop.NoopTracerFactory;

import javax.inject.Singleton;

/**
 * Creates a default NoopTracer if no other tracer is present.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class DefaultTracer {

    /**
     * Creates a default NoopTracer if no other tracer is present.
     *
     * @return {@link NoopTracer} No-op implementation of the Tracer interface, all methods are no-ops.
     */
    @Singleton
    @Primary
    @Requires(missingBeans = Tracer.class)
    NoopTracer noopTracer() {
        return NoopTracerFactory.create();
    }
}
