package io.micronaut.kotlin.processing.aop.adapter

import io.micronaut.aop.Adapter
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton

@Singleton
@Requires(property = "foo.bar")
internal class Test {

    var isInvoked = false
        private set

    @Adapter(ApplicationEventListener::class)
    fun onStartup(event: StartupEvent) {
        isInvoked = true
    }
}
