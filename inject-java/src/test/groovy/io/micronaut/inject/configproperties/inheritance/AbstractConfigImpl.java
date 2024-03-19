package io.micronaut.inject.configproperties.inheritance;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@EachProperty("teams")
public class AbstractConfigImpl extends AbstractConfig {
    @NotNull
    private final String name;
    @Nullable
    private String childThing;
    @NotNull
    private String thing;

    @ConfigurationInject
    public AbstractConfigImpl(@Parameter("name") @NotNull String name, @NotNull String value) {
        super(value);
        this.name = name;
        this.childThing = "def childThing";
        this.thing = "thing";
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    @Nullable
    public final String getChildThing() {
        return this.childThing;
    }

    public final void setChildThing(@Nullable String childThing) {
        this.childThing = childThing;
    }

    @Override
    @NotNull
    public String getThing() {
        return this.thing;
    }

    public void setThing(@NotNull String thing) {
        this.thing = thing;
    }
}
