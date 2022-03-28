package io.micronaut.http.client

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.IGlobalExtension
import reactor.core.publisher.Hooks

@CompileStatic
class ContextLossTrackingGlobalExtension implements IGlobalExtension {

    @Override
    void start() {
        Hooks.onOperatorDebug()
        Hooks.enableContextLossTracking()
    }
}
