package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.aop.introduction.Stub;

import javax.inject.Singleton;

@Stub
@Singleton
public interface IntroductionA extends IntroductionOperations {

}
