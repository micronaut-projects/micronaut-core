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
package io.micronaut.tracing.instrument.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import io.micronaut.scheduling.instrument.ReactiveInvocationInstrumenterFactory;
import org.slf4j.MDC;

import javax.inject.Singleton;
import java.util.Map;

/**
 * A function that instruments invocations with the Mapped Diagnostic Context for Slf4j.
 *
 * @author graemerocher
 * @author LarsEckart
 * @since 1.1
 */
@Singleton
@Requires(classes = MDC.class)
@Internal
public final class MdcInstrumenter implements InvocationInstrumenterFactory, ReactiveInvocationInstrumenterFactory {

    /**
     * Creates optional invocation instrumenter.
     *
     * @return An optional that contains the invocation instrumenter
     */
    @Override
    public InvocationInstrumenter newInvocationInstrumenter() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return new InvocationInstrumenter() {

            Map<String, String> oldContextMap;

            @Override
            public void beforeInvocation() {
                oldContextMap = MDC.getCopyOfContextMap();
                if (contextMap != null && !contextMap.isEmpty()) {
                    MDC.setContextMap(contextMap);
                }
            }

            @Override
            public void afterInvocation(boolean cleanup) {
                if (oldContextMap != null && !oldContextMap.isEmpty()) {
                    MDC.setContextMap(oldContextMap);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    @Override
    public InvocationInstrumenter newReactiveInvocationInstrumenter() {
        return newInvocationInstrumenter();
    }
}
