package io.micronaut.docs.aop.introduction.generics;

@PublisherProxy
public interface SpecificPublisher extends GenericPublisher<SpecificEvent> {}
