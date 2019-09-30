package io.micronaut.aop.introduction.delegation;

@DelegationAdvice
public interface DelegatingIntroduced extends Delegating {
    String test2();
}
