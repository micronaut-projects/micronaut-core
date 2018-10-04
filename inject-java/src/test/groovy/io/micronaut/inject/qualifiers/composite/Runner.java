package io.micronaut.inject.qualifiers.composite;

import groovy.lang.Singleton;

import javax.inject.Named;

@Singleton
@Named("thread")
public class Runner implements Runnable {
    @Override
    public void run() {

    }
}