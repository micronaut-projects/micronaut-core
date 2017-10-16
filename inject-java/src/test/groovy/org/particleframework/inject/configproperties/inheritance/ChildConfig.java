package org.particleframework.inject.configproperties.inheritance;

import org.particleframework.context.annotation.ConfigurationProperties;

@ConfigurationProperties("baz")
public class ChildConfig extends MyConfig {
    String stuff;

    public String getStuff() {
        return stuff;
    }

    public void setStuff(String stuff) {
        this.stuff = stuff;
    }
}
