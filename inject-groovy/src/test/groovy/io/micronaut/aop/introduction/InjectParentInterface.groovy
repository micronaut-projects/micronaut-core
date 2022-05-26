package io.micronaut.aop.introduction;

import jakarta.inject.Singleton;

@Singleton
class InjectParentInterface {

    InjectParentInterface(ParentInterface<?> parentInterface) {}
}
