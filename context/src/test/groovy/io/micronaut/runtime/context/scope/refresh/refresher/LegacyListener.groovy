package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.context.scope.refresh.RefreshEventListener
import jakarta.inject.Singleton

/**
 * Written before the refresher: it keeps receiving the event, once per refresh, after the rebind.
 */
@Singleton
@Requires(property = "spec.name", value = "ConfigurationRefresherSpec")
class LegacyListener implements RefreshEventListener {
    final List<Map<String, Object>> received = []
    final PoolConfiguration configuration
    final List<String> urlsSeen = []

    LegacyListener(PoolConfiguration configuration) {
        this.configuration = configuration
    }

    @Override
    Set<String> getObservedConfigurationPrefixes() {
        ["pool"] as Set
    }

    @Override
    void onApplicationEvent(RefreshEvent event) {
        received << event.source
        urlsSeen << configuration.url
    }
}
