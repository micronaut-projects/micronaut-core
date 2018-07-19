package io.micronaut.inject.repeatable

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import javax.inject.Singleton

@Singleton
@Requirements(Requires(property = "foo"), Requires(property = "bar"))
class MultipleRequires {
}