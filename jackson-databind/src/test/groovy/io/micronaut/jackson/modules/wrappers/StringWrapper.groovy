package io.micronaut.jackson.modules.wrappers

import io.micronaut.core.annotation.Introspected

@Introspected
class StringWrapper {
    final String value
    StringWrapper(String value) {
        this.value = value
    }
}
