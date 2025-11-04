package io.micronaut.docs.server.request_scope

import io.micronaut.core.annotation.Introspected

@Introspected
data class DemoObject(
    val text: String,
    val list: List<String>,
)
