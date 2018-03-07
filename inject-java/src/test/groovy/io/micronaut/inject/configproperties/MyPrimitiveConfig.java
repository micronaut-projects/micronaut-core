package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("foo.bar")
public class MyPrimitiveConfig {
    int port;
    int primitiveDefaultValue = 9999;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPrimitiveDefaultValue() {
        return primitiveDefaultValue;
    }

    public void setPrimitiveDefaultValue(int primitiveDefaultValue) {
        this.primitiveDefaultValue = primitiveDefaultValue;
    }
}
