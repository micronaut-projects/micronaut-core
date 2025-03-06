package io.micronaut.docs.ioc.introspection

import io.micronaut.core.annotation.Introspected

import javax.persistence.Transient

@Introspected
data class ObjectWithJavaxTransient(@field:Transient var tmp: String) {
}
