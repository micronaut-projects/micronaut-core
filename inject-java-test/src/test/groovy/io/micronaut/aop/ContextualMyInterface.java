package io.micronaut.aop;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;

@EachBean(Double.class)
@ContextualMyInterfaceAdvice
@Internal
public interface ContextualMyInterface extends MyInterface {
}
