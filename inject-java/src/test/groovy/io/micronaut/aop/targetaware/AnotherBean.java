package io.micronaut.aop.targetaware;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named("another")
public class AnotherBean {
    @TestTargetAwareAnn
    void foo() {

    }
}
