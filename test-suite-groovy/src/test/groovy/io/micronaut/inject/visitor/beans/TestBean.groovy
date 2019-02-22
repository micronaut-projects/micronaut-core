package io.micronaut.inject.visitor.beans

import io.micronaut.core.annotation.Introspected

@Introspected
class TestBean {
    String name
    int age
    String[] stringArray
    int[] primitiveArray
}
