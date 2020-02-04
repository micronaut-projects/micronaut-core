package io.micronaut.inject.injectionpoint;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SomeBeanConsumer {

    private final SomeBean fromConstructor;

    @Inject
    @SomeAnn("two")
    SomeBean fromField;

    @Inject
    @SomeAnn("four")
    SomeType someType;

    private SomeBean fromMethod;

    public SomeBeanConsumer(@SomeAnn("one") SomeBean fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    public SomeBean getFromConstructor() {
        return fromConstructor;
    }

    public SomeBean getFromField() {
        return fromField;
    }

    @Inject
    public void setFromMethod(@SomeAnn("three") SomeBean fromMethod) {
        this.fromMethod = fromMethod;
    }

    public SomeBean getFromMethod() {
        return fromMethod;
    }

    public SomeType getSomeType() {
        return someType;
    }
}
