package io.micronaut.inject.context.deadlock;

import jakarta.inject.Singleton;

@Singleton
public class BeanB {
    boolean visitedFromA = false;
}
