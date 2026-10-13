package io.micronaut.docs.aop.lifecycle.pertarget.labelled

import io.micronaut.context.annotation.EachBean

// tag::channel[]
@EachBean(ChannelConfiguration::class) // <1>
@Labelled
open class Channel(private val configuration: ChannelConfiguration) {

    open fun send(message: String): String = "$message from ${configuration.sender}"
}
// end::channel[]
