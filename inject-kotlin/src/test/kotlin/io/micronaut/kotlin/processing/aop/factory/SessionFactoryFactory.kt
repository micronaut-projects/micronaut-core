package io.micronaut.kotlin.processing.aop.factory

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryDelegatingImpl

@Factory
class SessionFactoryFactory {

    @Mutating("name")
    @Prototype
    fun sessionFactory(): SessionFactory {
        return SessionFactoryDelegatingImpl(null)
    }
}
