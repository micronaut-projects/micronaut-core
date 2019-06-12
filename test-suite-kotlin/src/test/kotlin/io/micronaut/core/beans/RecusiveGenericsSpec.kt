package io.micronaut.core.beans

import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.specs.StringSpec

class RecusiveGenericsSpec : StringSpec({
    // issue https://github.com/micronaut-projects/micronaut-core/issues/1607
    "test recursive generics on bean introspection".config(enabled = false) {
        val introspection = BeanIntrospection.getIntrospection(Item::class.java)
        // just check compilation works
        introspection.shouldNotBeNull()
    }
})