package io.micronaut.inject.configproperties.inheritance;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EachProperty("teams")
public class NotAbstractConfigImpl extends NotAbstractConfig {
    @NotNull
    private final String name;
    @NotNull
    private final String thing;
    @Nullable
    private String childThing;
    @Nullable
    private String superValueWithOverride;

    @ConfigurationInject
    public NotAbstractConfigImpl(@Parameter("name") @NotNull String name, @NotNull String value, @NotNull String thing) {
        super(value);
        this.name = name;
        this.thing = thing;
        this.childThing = "def childThing";
        this.superValueWithOverride = "my defaultValue";
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    @NotNull
    public final String getThing() {
        return this.thing;
    }

    @Nullable
    public final String getChildThing() {
        return this.childThing;
    }

    public final void setChildThing(@Nullable String var1) {
        this.childThing = var1;
    }

    @Override
    @Nullable
    public String getSuperValueWithOverride() {
        return this.superValueWithOverride;
    }

    @Override
    public void setSuperValueWithOverride(@Nullable String var1) {
        this.superValueWithOverride = var1;
    }
}

