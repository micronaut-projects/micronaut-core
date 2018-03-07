package io.micronaut.inject.qualifiers.bytypespec;

import io.micronaut.context.annotation.Type;
import io.micronaut.context.annotation.Type;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
class Bean {
    List<Foo> foos;

    public Bean(@Type({One.class,Two.class}) Foo[] foos) {
        this.foos = Arrays.asList(foos);
    }

    public List<Foo> getFoos() {
        return foos;
    }

    public void setFoos(List<Foo> foos) {
        this.foos = foos;
    }
}
