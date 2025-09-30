package io.micronaut.docs.ioc.introspection

import io.micronaut.core.annotation.Introspected

import jakarta.persistence.Transient

@Introspected
data class ObjectWithJakartaTransient(@field:Transient var tmp: String) {
}
