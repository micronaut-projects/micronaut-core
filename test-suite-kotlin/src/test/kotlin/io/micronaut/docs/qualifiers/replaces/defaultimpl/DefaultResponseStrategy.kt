package io.micronaut.docs.qualifiers.replaces.defaultimpl

//tag::clazz[]
import javax.inject.Singleton

@Singleton
internal class DefaultResponseStrategy : ResponseStrategy
//end::clazz[]