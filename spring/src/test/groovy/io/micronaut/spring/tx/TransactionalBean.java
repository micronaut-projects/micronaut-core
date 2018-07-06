package io.micronaut.spring.tx;

import io.micronaut.spring.tx.annotation.Transactional;

import javax.inject.Singleton;

@Transactional
@Singleton
public class TransactionalBean {

    String doSomething() {
        return "foo";
    }
}
