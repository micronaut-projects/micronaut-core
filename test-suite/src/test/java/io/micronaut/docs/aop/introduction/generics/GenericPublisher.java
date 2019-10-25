package io.micronaut.docs.aop.introduction.generics;

public interface GenericPublisher<E> {
    
    String publish(E event);
    
}
