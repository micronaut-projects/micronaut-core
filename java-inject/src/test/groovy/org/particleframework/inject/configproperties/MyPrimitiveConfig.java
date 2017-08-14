package org.particleframework.inject.configproperties;

import org.particleframework.config.ConfigurationProperties;

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
