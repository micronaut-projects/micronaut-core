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

/**
 * The default {@link Instrumentation} implementation. When using in a try-with-resources block will act identical to:
 * <p/>
 * <pre>
 * try {
 *     instrumenter.beforeInvocation();
 *     ...
 * } finally {
 *     instrumenter.afterInvocation();
 * }
 * </pre>
 *
 * @author lgathy
 * @since 2.0
 */
public final class DefaultInstrumentation implements Instrumentation {

    private final InvocationInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param instrumenter The instrumenter to be used in the instrumentation
     */
    public DefaultInstrumentation(InvocationInstrumenter instrumenter) {
        instrumenter.beforeInvocation();
        this.instrumenter = instrumenter;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void close(boolean cleanup) {
        instrumenter.afterInvocation(cleanup);
    }

    @Override
    public void close() {
        instrumenter.afterInvocation();
    }
}
