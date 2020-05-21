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

import io.micronaut.reactive.rxjava2.ConditionalInstrumenter;
import org.slf4j.MDC;

import java.util.Objects;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;


/**
 * Conditional instrumenter to set a given key/value pair in {@link MDC}.
 * <p/>
 * The condition of instrumentation is that {@code key} does not exist in MDC or it is associated with a different
 * {@code value}. Note: that the original value associated with {@code key} prior to invocation will NOT be restored
 * after instrumentation, it will be removed instead.
 *
 * @author lgathy
 * @since 2.0
 */
public class MdcKeyValuePairInstrumenter implements ConditionalInstrumenter {

    private final String key;
    private final String value;

    /**
     * Default constructor.
     *
     * @param key   The key to be put into MDC
     * @param value The value to be put into MDC
     */
    public MdcKeyValuePairInstrumenter(String key, String value) {
        this.key = requireNonNull("key", key);
        this.value = requireNonNull("value", value);
    }

    @Override
    public boolean testInstrumentationNeeded() {
        return !Objects.equals(MDC.get(key), value);
    }

    @Override
    public void beforeInvocation() {
        MDC.put(key, value);
    }

    @Override
    public void afterInvocation(boolean cleanup) {
        MDC.remove(key);
    }
}
