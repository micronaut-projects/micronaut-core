package io.micronaut.inject.collect;

import jakarta.inject.Singleton;

@Singleton
public class ThingThatNeedsMyIterable {

    public ThingThatNeedsMyIterable(MyIterable myIterable) {

    }
}
