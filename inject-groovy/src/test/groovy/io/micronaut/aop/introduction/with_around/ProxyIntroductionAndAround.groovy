package io.micronaut.aop.introduction.with_around

import java.lang.annotation.Documented
import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME

@Documented
@Retention(RUNTIME)
@ProxyIntroduction
@ProxyAround
@interface ProxyIntroductionAndAround {
}
