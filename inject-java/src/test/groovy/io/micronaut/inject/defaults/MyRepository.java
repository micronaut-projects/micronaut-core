package io.micronaut.inject.defaults;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;

@Introduction
@Type(DataIntroductionAdvice.class)
@SomeRepositoryConfiguration
public interface MyRepository {
}
