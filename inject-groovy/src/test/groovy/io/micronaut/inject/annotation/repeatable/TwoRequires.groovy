package io.micronaut.inject.annotation.repeatable

import io.micronaut.context.annotation.Requires

@Requires(property = "foo")
@Requires(property = "baz")
@interface TwoRequires {
}
