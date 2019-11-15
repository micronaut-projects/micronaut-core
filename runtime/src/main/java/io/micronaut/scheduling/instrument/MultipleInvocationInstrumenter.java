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

import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of {@link InvocationInstrumenter} which invoked multiple instrumenters.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Internal
class MultipleInvocationInstrumenter implements InvocationInstrumenter {
    private static final Logger LOG = LoggerFactory.getLogger(InvocationInstrumenter.class);

    private final Collection<InvocationInstrumenter> invocationInstrumenters;
    private final List<InvocationInstrumenter> executedInstrumenters;

    /**
     * Creates new instance.
     *
     * @param invocationInstrumenters multiple instrumenters
     */
    MultipleInvocationInstrumenter(Collection<InvocationInstrumenter> invocationInstrumenters) {
        this.invocationInstrumenters = invocationInstrumenters;
        this.executedInstrumenters = new ArrayList<>(invocationInstrumenters.size());
    }

    /**
     * Invokes beforeInvocation for multiple instrumenters.
     */
    @Override
    public void beforeInvocation() {
        for (InvocationInstrumenter instrumenter : invocationInstrumenters) {
            instrumenter.beforeInvocation();
            executedInstrumenters.add(instrumenter);
        }
    }

    /**
     * Invokes afterInvocation for multiple instrumenters.
     */
    @Override
    public void afterInvocation() {
        for (InvocationInstrumenter instrumenter : executedInstrumenters) {
            try {
                instrumenter.afterInvocation();
            } catch (Exception e) {
                LOG.warn("After instrumentation invocation error: {}", e.getMessage(), e);
            }
        }
        executedInstrumenters.clear();
    }
}
