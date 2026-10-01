package io.micronaut.inject.beans.lookups;

/**
 * The bean a runtime bean definition builds in the lookup tests. It is not itself a compiled bean, so that a
 * compiled bean that injects it can only be satisfied by the runtime definition the test registers.
 */
public class LookupHolder {

    private final Object created;

    public LookupHolder(Object created) {
        this.created = created;
    }

    public Object getCreated() {
        return created;
    }
}
