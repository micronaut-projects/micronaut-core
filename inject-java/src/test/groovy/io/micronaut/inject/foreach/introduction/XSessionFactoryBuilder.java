package io.micronaut.inject.foreach.introduction;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;

@Factory
public class XSessionFactoryBuilder {

    @EachBean(XDataSource.class)
    XSessionFactory xSessionFactory(@Parameter String name) {
        return new XSessionFactoryImpl(name);
    }

    private record XSessionFactoryImpl(String name) implements XSessionFactory {
    }
}
