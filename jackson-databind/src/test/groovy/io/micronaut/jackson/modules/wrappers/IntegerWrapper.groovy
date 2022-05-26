package io.micronaut.jackson.modules.wrappers

import io.micronaut.core.annotation.Introspected

@Introspected
class IntegerWrapper {
    final Integer value
    IntegerWrapper(Integer value) {
        this.value = value
    }
}
