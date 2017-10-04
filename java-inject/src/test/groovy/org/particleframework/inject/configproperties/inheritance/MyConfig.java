package org.particleframework.inject.configproperties.inheritance;

import org.particleframework.context.annotation.ConfigurationProperties;

@ConfigurationProperties("foo.bar")
public class MyConfig {
    int port;
    String host;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
