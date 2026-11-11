package io.micronaut.docs.aop.lifecycle.pertarget.labelled;

import io.micronaut.context.annotation.EachBean;

// tag::channel[]
@EachBean(ChannelConfiguration.class) // <1>
@Labelled
public class Channel {

    private final ChannelConfiguration configuration;

    public Channel(ChannelConfiguration configuration) {
        this.configuration = configuration;
    }

    public String send(String message) {
        return message + " from " + configuration.getSender();
    }
}
// end::channel[]
