package io.micronaut.docs.expressions;

import jakarta.inject.Singleton;
import java.util.Random;

@Singleton
public class CustomEvaluationContext {
    public int generateRandom(int min, int max) {
        return new Random().nextInt(max - min) + min;
    }
}
