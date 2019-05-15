package io.micronaut.docs.qualifiers.replaces.defaultimpl

//tag::clazz[]
import io.micronaut.context.annotation.DefaultImplementation

@DefaultImplementation(DefaultResponseStrategy)
interface ResponseStrategy {
}
//end::clazz[]