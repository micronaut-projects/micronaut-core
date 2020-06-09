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
package io.micronaut.scheduling.instrument;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

/**
 * Implementation of {@link InvocationInstrumenter} which invoked multiple instrumenters.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Internal
final class MultipleInvocationInstrumenter implements InvocationInstrumenter {
    private static final Logger LOG = LoggerFactory.getLogger(InvocationInstrumenter.class);

    private final Collection<InvocationInstrumenter> invocationInstrumenters;

    /**
     * Creates new instance.
     *
     * @param invocationInstrumenters multiple instrumenters
     */
    MultipleInvocationInstrumenter(Collection<InvocationInstrumenter> invocationInstrumenters) {
        this.invocationInstrumenters = invocationInstrumenters;
    }

    @NonNull
    @Override
    public Instrumentation newInstrumentation() {
        List<Instrumentation> instrumentationList = new ArrayList<>(invocationInstrumenters.size());
        for (InvocationInstrumenter instrumenter : invocationInstrumenters) {
            try {
                instrumentationList.add(instrumenter.newInstrumentation());
            } catch (Exception e) {
                LOG.warn("InvocationInstrumenter.newInstrumentation invocation error: {}", e.getMessage(), e);
            }
        }
        return new Instrumentation() {
            @Override
            public void close(boolean cleanup) {
                // invoke in reverse order
                for (ListIterator<Instrumentation> iterator = instrumentationList.listIterator(instrumentationList.size()); iterator.hasPrevious(); ) {
                    try {
                        iterator.previous().close(cleanup);
                    } catch (Exception e) {
                        LOG.warn("Instrumentation.close invocation error: {}", e.getMessage(), e);
                    }
                }
            }
        };
    }
}
