package io.micronaut.docs.aop.lifecycle.pertarget.labelled

import io.micronaut.context.annotation.EachProperty

@EachProperty("labelled-channels")
class ChannelConfiguration {

    String sender
}
