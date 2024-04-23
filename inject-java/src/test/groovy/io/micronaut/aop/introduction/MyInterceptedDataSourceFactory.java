package io.micronaut.aop.introduction;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "InterceptorQualifierSpec")
@Factory
public class MyInterceptedDataSourceFactory {

    @Singleton
    @Named("FOO")
    MyDataSource foo() {
        return new MyDataSource() {
            @Override
            public String getValue() {
                return "FOO";
            }
        };
    }

    @Singleton
    @Named("BAR")
    MyDataSource bar() {
        return new MyDataSource() {
            @Override
            public String getValue() {
                return "BAR";
            }
        };
    }

}
