package io.micronaut.docs.context.annotation.primary

import io.micronaut.context.annotation.Requires
//tag::imports[]

import javax.inject.Singleton

//end::imports[]

@Requires(property = "spec.name", value = "primaryspec")
//tag::clazz[]
@Singleton
class Blue: ColorPicker {
    override fun color(): String {
        return "blue"
    }
}
//end::clazz[]
