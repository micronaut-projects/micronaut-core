package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Singleton
@Replaces(IntroductionB.class)
public class AnotherIntroductionReplacement  implements IntroductionB {
    @Override
    public String test(String name) {
        return "foo";
    }

    @Override
    public String test(String name, int age) {
        return "foo";
    }
}
