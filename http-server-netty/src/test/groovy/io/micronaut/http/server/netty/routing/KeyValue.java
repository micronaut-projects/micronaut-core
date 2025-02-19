package io.micronaut.http.server.netty.routing;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class KeyValue {
    private String key;
    private String value;

    @Creator
    public static KeyValue of(String key, String value) {
        final KeyValue keyValue = new KeyValue();
        keyValue.key = key;
        keyValue.value = value;
        return keyValue;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
