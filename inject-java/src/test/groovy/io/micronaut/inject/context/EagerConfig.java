package io.micronaut.inject.context;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("eager")
public class EagerConfig {
    static boolean created = false;
    private String something;

    public EagerConfig() {
        created = true;
    }

    public String getSomething() {
        return something;
    }

    public void setSomething(String something) {
        this.something = something;
    }
}
