package io.micronaut.aop.introduction;

import jakarta.inject.Singleton;

@Singleton
public class InjectParentInterface {

    public InjectParentInterface(ParentInterface<?> parentInterface) {}
}
