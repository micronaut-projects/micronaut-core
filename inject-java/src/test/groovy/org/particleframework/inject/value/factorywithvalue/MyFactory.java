package org.particleframework.inject.value.factorywithvalue;

import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.Value;

@Factory
public class MyFactory {
    @Bean
    public A newA(@Value("foo.bar") int port) {
        return new A(port);
    }

    @Bean
    public B newB(A a, @Value("foo.bar") int port) {
        return new B(a, port);
    }
}
