package io.micronaut.inject.foreach.generic;

import io.micronaut.context.annotation.EachBean;

@EachBean(value = MyReader2.class, remapGenerics = @EachBean.RemapGeneric(type = CoreReader2.class, name = "G"))
public class MyToCoreReader2<T> implements CoreReader2<T> {

    private final MyReader2<T> delegate;

    public MyToCoreReader2(MyReader2<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T read() {
        return delegate.read();
    }
}
