package io.micronaut.inject.configproperties.inheritance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NotAbstractConfig {
    @NotNull
    private final String value;
    @Nullable
    private String superValue;
    @Nullable
    private String superValueWithOverride;

    public NotAbstractConfig(@NotNull String value) {
        this.value = value;
        this.superValue = "";
        this.superValueWithOverride = "this is ";
    }

    @NotNull
    public final String getValue() {
        return this.value;
    }

    @Nullable
    public final String getSuperValue() {
        return this.superValue;
    }

    public final void setSuperValue(@Nullable String var1) {
        this.superValue = var1;
    }

    @Nullable
    public String getSuperValueWithOverride() {
        return this.superValueWithOverride;
    }

    public void setSuperValueWithOverride(@Nullable String var1) {
        this.superValueWithOverride = var1;
    }
}
