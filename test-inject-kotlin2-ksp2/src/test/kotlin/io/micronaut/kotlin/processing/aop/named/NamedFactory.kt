package io.micronaut.kotlin.processing.aop.named

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.kotlin.processing.aop.Logged
import io.micronaut.runtime.context.scope.Refreshable
import jakarta.inject.Named
import jakarta.inject.Singleton

@Factory
class NamedFactory {

    @EachProperty(value = "aop.test.named", primary = "default")
    @Refreshable
    fun namedInterface(@Parameter name: String): NamedInterface {
        return object : NamedInterface {
            override fun doStuff(): String {
                return name
            }
        }
    }

    @Named("first")
    @Logged
    @Singleton
    fun first(): OtherInterface {
        return object : OtherInterface {
            override fun doStuff(): String {
                return "first"
            }
        }
    }

    @Named("second")
    @Logged
    @Singleton
    fun second(): OtherInterface {
        return object : OtherInterface {
            override fun doStuff(): String {
                return "second"
            }
        }
    }

    @EachProperty("other.interfaces")
    fun third(config: Config, @Parameter name: String): OtherInterface {
        return object : OtherInterface {
            override fun doStuff(): String {
                return name
            }
        }
    }
}
