package io.micronaut.jackson.modules.wrappers

import io.micronaut.core.annotation.Introspected

@Introspected
class BooleanWrapper {
    final boolean value
    BooleanWrapper(boolean value) {
        this.value = value
    }
}
