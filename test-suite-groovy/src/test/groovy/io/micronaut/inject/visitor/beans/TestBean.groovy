package io.micronaut.inject.visitor.beans

import io.micronaut.core.annotation.Introspected

@Introspected
class TestBean {
    boolean flag
    String name
    int age
    String[] stringArray
    int[] primitiveArray
}
