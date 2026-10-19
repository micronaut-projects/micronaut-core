package io.micronaut.docs.aop.lifecycle.pertarget.labelled;

import io.micronaut.context.annotation.EachProperty;

@EachProperty("labelled-channels")
public class ChannelConfiguration {

    private String sender;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
