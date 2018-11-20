package io.micronaut.inject.requires

import io.micronaut.context.annotation.Requires
import javax.inject.Singleton

@Singleton
@Requires(sdk = Requires.Sdk.KOTLIN, version = "1.3.50")
class RequiresFuture