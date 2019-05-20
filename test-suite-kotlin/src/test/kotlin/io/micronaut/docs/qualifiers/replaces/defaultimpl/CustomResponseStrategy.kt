package io.micronaut.docs.qualifiers.replaces.defaultimpl

//tag::clazz[]
import io.micronaut.context.annotation.Replaces
import javax.inject.Singleton

@Singleton
@Replaces(ResponseStrategy::class)
class CustomResponseStrategy : ResponseStrategy
//end::clazz[]