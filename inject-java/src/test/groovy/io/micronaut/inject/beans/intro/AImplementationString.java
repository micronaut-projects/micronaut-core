package io.micronaut.inject.beans.intro;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;

@Requires(property = "spec", value = "IntroductionInjectionSpec")
@MyIntroductionBean
@Secondary
public class AImplementationString implements AInterface<String, String> {

    private String s1,s2;

    @Override
    public void set(String s, String s2) {
        s1=s;
        this.s2=s2;
    }

    @Override
    public String get(String s, String s2) {
        return s.getClass().getName() +","+s.getClass().getName();
    }
}
