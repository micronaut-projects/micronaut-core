package io.micronaut.inject.configproperties.eachbeaninterceptor;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.sql.Connection;

@Requires(property = "spec", value = "EachBeanInterceptorSpec")
@Singleton
class MyBean {

    private final Connection defaultConnection, fooConnection, barConnection;

    MyBean(@Named("default") Connection defaultConnection, @Named("foo") Connection fooConnection, @Named("bar") Connection barConnection) {
        this.defaultConnection = defaultConnection;
        this.fooConnection = fooConnection;
        this.barConnection = barConnection;
   }

    public Connection getDefaultConnection() {
        return defaultConnection;
    }

    public Connection getFooConnection() {
        return fooConnection;
    }

    public Connection getBarConnection() {
        return barConnection;
    }
}
