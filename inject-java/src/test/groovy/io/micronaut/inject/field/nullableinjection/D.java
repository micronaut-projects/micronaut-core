package io.micronaut.inject.field.nullableinjection;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

public class D {

    @Inject @Nullable
    protected Provider<A> a;

    public Provider<A> getA() {
        return this.a;
    }
}
