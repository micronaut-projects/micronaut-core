package io.micronaut.inject.collect;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ThingThatNeedsMySetOfStrings {
    private MySetOfStrings strings;

    @Inject
    MySetOfStrings otherStrings;


    @Inject
    public ThingThatNeedsMySetOfStrings(MySetOfStrings strings) {
        this.strings = strings;
    }

    public MySetOfStrings getStrings() {
        return strings;
    }

    @Inject
    public void setStrings(MySetOfStrings strings) {
        this.strings = strings;
    }
}