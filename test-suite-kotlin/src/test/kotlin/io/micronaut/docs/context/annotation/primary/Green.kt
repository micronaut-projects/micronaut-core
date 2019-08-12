package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.annotation.Requires

//tag::imports[]
import io.micronaut.context.annotation.Primary
import javax.inject.Singleton

//end::imports[]

@Requires(property = "spec.name", value = "primaryspec")
//tag::clazz[]
@Primary
@Singleton
class Green: ColorPicker {
    override fun color(): String {
        return "green"
    }
}
//end::clazz[]
