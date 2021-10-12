package io.micronaut.jackson.modules.wrappers

import io.micronaut.core.annotation.Introspected

@Introspected
class IntWrapper {
    final int value
    IntWrapper(int value) {
        this.value = value
    }
}
