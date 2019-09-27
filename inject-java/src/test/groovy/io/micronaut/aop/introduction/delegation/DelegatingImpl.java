package io.micronaut.aop.introduction.delegation;

public class DelegatingImpl implements Delegating {
    @Override
    public String test() {
        return "good";
    }
}
