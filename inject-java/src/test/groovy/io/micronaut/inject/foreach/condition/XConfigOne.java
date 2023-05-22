package io.micronaut.inject.foreach.condition;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("one")
public class XConfigOne implements XConfig {
    private String name;
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
