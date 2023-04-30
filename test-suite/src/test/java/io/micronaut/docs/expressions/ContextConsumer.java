package io.micronaut.docs.expressions;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class ContextConsumer {

    @Value("#{ generateRandom(1, 10) }")
    public int randomField;

}

