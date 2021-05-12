package io.micronaut.inject.collect;

import javax.inject.Singleton;

@Singleton
public class ThingThatNeedsMyIterable {

    public ThingThatNeedsMyIterable(MyIterable myIterable) {

    }
}
