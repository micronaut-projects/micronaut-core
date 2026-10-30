package io.micronaut.docs.aop.lifecycle.pertarget.labelled

import io.micronaut.context.annotation.EachBean

// tag::channel[]
@EachBean(ChannelConfiguration) // <1>
@Labelled
class Channel {

    private final ChannelConfiguration configuration

    Channel(ChannelConfiguration configuration) {
        this.configuration = configuration
    }

    String send(String message) {
        "$message from ${configuration.sender}"
    }
}
// end::channel[]
