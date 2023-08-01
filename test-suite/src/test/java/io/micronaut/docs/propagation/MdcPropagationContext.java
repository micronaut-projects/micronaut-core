package io.micronaut.docs.propagation;

import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.slf4j.MDC;

import java.util.Map;

// tag::class[]
public record MdcPropagationContext(Map<String, String> state) implements ThreadPropagatedContextElement<Map<String, String>> { // <1>

    public MdcPropagationContext() {
        this(MDC.getCopyOfContextMap());
    }

    @Override
    public Map<String, String> updateThreadContext() {
        Map<String, String> oldState = MDC.getCopyOfContextMap();
        setCurrent(state); // <2>
        return oldState; // <3>
    }

    @Override
    public void restoreThreadContext(Map<String, String> oldState) {
        setCurrent(oldState); // <4>
    }

    private void setCurrent(Map<String, String> contextMap) {
        if (contextMap == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextMap);
        }
    }
}
// end::class[]
