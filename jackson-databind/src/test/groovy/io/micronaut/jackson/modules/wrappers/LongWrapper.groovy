package io.micronaut.jackson.modules.wrappers

import io.micronaut.core.annotation.Introspected

@Introspected
class LongWrapper {
    final long value
    LongWrapper(long value) {
        this.value = value
    }
}
