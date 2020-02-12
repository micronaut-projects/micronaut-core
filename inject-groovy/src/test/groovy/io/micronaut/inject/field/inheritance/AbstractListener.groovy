package io.micronaut.inject.field.inheritance


import javax.inject.Inject

abstract class AbstractListener {
    @Inject protected SomeBean someBean
}
