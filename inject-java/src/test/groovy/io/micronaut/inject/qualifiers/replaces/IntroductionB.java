package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.aop.introduction.Stub;

import javax.inject.Singleton;

@Stub
@Singleton
public interface IntroductionB {
    String test(String name);

    String test(String name, int age);
}
