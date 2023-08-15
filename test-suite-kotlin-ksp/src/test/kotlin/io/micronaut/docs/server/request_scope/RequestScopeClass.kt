package io.micronaut.docs.server.request_scope

import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Inject

@RequestScope
open class RequestScopeClass (
    open var text: String?,
    open val list: MutableList<String> = mutableListOf()
) {
    @Inject
    constructor() : this(null)
}
