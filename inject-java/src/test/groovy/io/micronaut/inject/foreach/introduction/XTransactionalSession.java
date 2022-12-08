package io.micronaut.inject.foreach.introduction;

import io.micronaut.context.annotation.EachBean;

@EachBean(XSessionFactory.class)
@XTransactionalAdvice
public interface XTransactionalSession extends XSession {
}
