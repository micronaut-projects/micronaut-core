/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context.propagation.slf4j;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.slf4j.MDC;

import java.util.Map;

/**
 * MDC propagation.
 *
 * @param state The MDC state as map
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public record MdcPropagationContext(Map<String, String> state) implements ThreadPropagatedContextElement<Map<String, String>> {

    public MdcPropagationContext() {
        this(MDC.getCopyOfContextMap());
    }

    @Override
    public Map<String, String> updateThreadContext() {
        Map<String, String> oldState = MDC.getCopyOfContextMap();
        setCurrent(state);
        return oldState;
    }

    @Override
    public void restoreThreadContext(Map<String, String> oldState) {
        setCurrent(oldState);
    }

    private void setCurrent(Map<String, String> contextMap) {
        if (contextMap == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextMap);
        }
    }
}
