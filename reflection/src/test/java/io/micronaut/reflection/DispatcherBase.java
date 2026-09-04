package io.micronaut.reflection;

import jakarta.inject.Inject;

/**
 * The super class of a reflective bean: its injection points are inherited.
 */
public class DispatcherBase {

    @Inject
    protected Warehouse baseWarehouse;

    private Warehouse setterWarehouse;

    @Inject
    protected void injectBase(Warehouse warehouse) {
        this.setterWarehouse = warehouse;
    }

    public Warehouse getBaseWarehouse() {
        return baseWarehouse;
    }

    public Warehouse getSetterWarehouse() {
        return setterWarehouse;
    }
}
