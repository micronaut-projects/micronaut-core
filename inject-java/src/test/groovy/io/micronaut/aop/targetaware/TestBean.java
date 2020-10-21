package io.micronaut.aop.targetaware;

import javax.inject.Singleton;

@Singleton
public class TestBean {

    @TestTargetAwareAnn
    void foo() {

    }
}
