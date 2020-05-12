package io.micronaut.tracing.instrument.util;

import io.micronaut.reactive.rxjava2.ConditionalInstrumenter;
import org.slf4j.MDC;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class MdcKeyValuePairInstrumenter implements ConditionalInstrumenter {

    private final String key;
    private final String value;

    public MdcKeyValuePairInstrumenter(String key, String value) {
        this.key = requireNonNull(key, "key");
        this.value = requireNonNull(value, "value");
    }

    @Override
    public boolean isActive() {
        return Objects.equals(MDC.get(key), value);
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
