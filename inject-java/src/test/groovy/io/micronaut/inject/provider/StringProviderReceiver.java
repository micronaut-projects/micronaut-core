package io.micronaut.inject.provider;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StringProviderReceiver {

    @Inject StringProvider stringProvider;

    String get() {
        return stringProvider.get();
    }
}
