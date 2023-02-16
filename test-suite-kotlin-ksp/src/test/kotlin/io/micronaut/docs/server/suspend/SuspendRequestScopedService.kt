package io.micronaut.docs.server.suspend

import io.micronaut.runtime.http.scope.RequestScope
import java.util.*

@RequestScope
open class SuspendRequestScopedService {
    open val requestId = UUID.randomUUID().toString()
}