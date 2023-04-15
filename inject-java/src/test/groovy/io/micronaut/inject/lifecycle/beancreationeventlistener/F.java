package io.micronaut.inject.lifecycle.beancreationeventlistener;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class F {
    F(Provider<G> g) {}
}
