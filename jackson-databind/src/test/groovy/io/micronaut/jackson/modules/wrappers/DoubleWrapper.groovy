package io.micronaut.jackson.modules.wrappers

import io.micronaut.core.annotation.Introspected

@Introspected
class DoubleWrapper {
    final double value
    DoubleWrapper(double value) {
        this.value = value
    }
}
