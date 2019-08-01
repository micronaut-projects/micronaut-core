package io.micronaut.docs.factories.nullable;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.util.Toggleable;

import javax.validation.constraints.NotNull;

// tag::class[]
@EachProperty("engines")
public class EngineConfiguration implements Toggleable {

    private boolean enabled = true;
    private Integer cylinders;

    @NotNull
    public Integer getCylinders() {
        return cylinders;
    }

    public void setCylinders(Integer cylinders) {
        this.cylinders = cylinders;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
// end::class[]
