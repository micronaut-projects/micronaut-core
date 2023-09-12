package io.micronaut.inject.beans.intro;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;

@Requires(property = "spec", value = "IntroductionInjectionSpec")
@MyIntroductionBean
@Secondary
public class AImplementationLong implements AInterface<Long, Long> {

    private Long l1,l2;

    @Override
    public void set(Long s, Long s2) {
        l1=s;
        l2=s2;
    }

    @Override
    public String get(Long s, Long s2) {
        return s.getClass().getName() +","+s.getClass().getName();
    }
}
