
package io.micronaut.jackson

import io.micronaut.core.annotation.Introspected

@Introspected
class NullPropertyDto {
    var longField: Long? = null
}
