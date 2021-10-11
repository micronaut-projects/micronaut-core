package io.micronaut.inject.provider;

import jakarta.inject.Singleton;

@Singleton
public class StringProviderImpl implements StringProvider {
    @Override
    public String get() {
        return "hello world";
    }
}
