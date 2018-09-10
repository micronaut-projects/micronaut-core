package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(IntroductionA.class)
public class IntroductionReplacement implements IntroductionOperations {
    @Override
    public String test(String name) {
        return "foo";
    }

    @Override
    public String test(String name, int age) {
        return "foo";
    }
}
