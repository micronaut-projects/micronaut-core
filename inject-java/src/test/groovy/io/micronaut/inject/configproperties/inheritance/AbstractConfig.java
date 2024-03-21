package io.micronaut.inject.configproperties.inheritance;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractConfig {
    @NotNull
    private final String value;
    @NotNull
    private String notThing;

    public AbstractConfig(@NotNull String value) {
        this.value = value;
        this.notThing = "def notThing";
    }

    @NotNull
    public final String getValue() {
        return this.value;
    }

    @NotNull
    public final String getNotThing() {
        return this.notThing;
    }

    public final void setNotThing(@NotNull String notThing) {
        this.notThing = notThing;
    }

    @NotNull
    public abstract String getThing();
}
