package io.micronaut.build.internal.sanity

import groovy.transform.CompileStatic
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * An internal extension aimed at verifying
 * the state of the Micronaut build itself.
 * It's aimed at debugging issues like the
 * annotation processors not being executed
 * properly.
 */
@CompileStatic
abstract class InternalSanityChecks {
    abstract MapProperty<String, Integer> getExpectedServiceCount()
    abstract Property<Boolean> getFailOnMismatch()
}
