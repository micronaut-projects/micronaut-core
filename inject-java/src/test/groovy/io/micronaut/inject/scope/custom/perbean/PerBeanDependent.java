package io.micronaut.inject.scope.custom.perbean;

/**
 * A bean whose creation creates another bean of the same scope on the same thread.
 */
@PerBeanScope
public class PerBeanDependent {

    private final PerBeanOther other;

    public PerBeanDependent(PerBeanOther other) {
        this.other = other;
    }

    public PerBeanOther getOther() {
        return other;
    }
}
