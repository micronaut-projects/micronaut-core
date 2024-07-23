package io.micronaut.inject.foreach.generic;

import io.micronaut.context.annotation.EachBean;

@EachBean(value = MyReader1.class, remapGenerics = @EachBean.RemapGeneric(type = CoreReader1.class, name = "X", to = "G"))
public class MyToCoreReader1<T> implements CoreReader1<T> {

    private final MyReader1<T> delegate;

    public MyToCoreReader1(MyReader1<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T read() {
        return delegate.read();
    }
}
