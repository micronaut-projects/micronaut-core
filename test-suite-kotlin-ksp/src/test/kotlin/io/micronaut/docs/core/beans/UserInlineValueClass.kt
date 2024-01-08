package io.micronaut.docs.core.beans

import io.micronaut.core.annotation.Introspected

/**
 * https://kotlinlang.org/docs/inline-classes.html
 */
@JvmInline
@Introspected
value class UserInlineValueClass(val name: String) {
}