package io.micronaut.inject.provider;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StringProviderReceiver {

    @Inject StringProvider stringProvider;

    String get() {
        return stringProvider.get();
    }
}
