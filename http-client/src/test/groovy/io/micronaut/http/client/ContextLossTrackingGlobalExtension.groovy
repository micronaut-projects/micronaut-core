package io.micronaut.http.client

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.AbstractGlobalExtension
import reactor.core.publisher.Hooks

@CompileStatic
class ContextLossTrackingGlobalExtension extends AbstractGlobalExtension {
    @Override
    void start() {
        Hooks.onOperatorDebug()
        Hooks.enableContextLossTracking()
    }
}
