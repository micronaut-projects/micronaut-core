package io.micronaut.inject.foreach.introduction;

import jakarta.inject.Inject;
import jakarta.inject.Named;

public class MyBean {
    @Inject
    @Named("one")
    XSession sessionOne;

    @Inject
    @Named("two")
    XSession sessionTwo;
}
