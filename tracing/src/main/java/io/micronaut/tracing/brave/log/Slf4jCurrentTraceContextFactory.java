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
package io.micronaut.tracing.brave.log;

import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.slf4j.MDC;


/**
 * Factory for the current trace context object.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class Slf4jCurrentTraceContextFactory {

    /**
     * Current Slf4j trace context.
     *
     * @return Slf4j trace context
     */
    @Requires(classes = {MDC.class, CurrentTraceContext.class})
    @Context
    CurrentTraceContext currentTraceContext() {
        return ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(new Slf4jScopeDecorator()).build();
    }
}
