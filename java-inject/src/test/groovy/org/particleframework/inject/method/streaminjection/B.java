package org.particleframework.inject.method.streaminjection;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class B {
    private Stream<A> all;
    private Stream<A> another;
    private List<A> allList;

    private Stream<A> another2;

    @Inject
    private void setAll(Stream<A> all) {
        this.all = all;
    }

    @Inject
    protected void setAnother(Stream<A> all) {
        this.another = all;
    }
    @Inject
    void setAnother2(Stream<A> all) {
        this.another2 = all;
    }

    Stream<A> getAnother() {
        return another;
    }

    Stream<A> getAnother2() {
        return another2;
    }

    List<A> getAll() {
        if(allList == null) {
            allList = this.all.collect(Collectors.toList());
        }
        return allList;
    }
}
