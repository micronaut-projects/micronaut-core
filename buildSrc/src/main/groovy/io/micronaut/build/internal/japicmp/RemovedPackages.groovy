package io.micronaut.build.internal.japicmp

import me.champeau.gradle.japicmp.report.Violation
import me.champeau.gradle.japicmp.report.ViolationTransformer
import me.champeau.gradle.japicmp.report.Severity
import java.util.Optional
import groovy.transform.CompileStatic

@CompileStatic
class RemovedPackages implements ViolationTransformer {
    private final List<String> excludedPackagesPrefixes
    private final List<String> excludedPackages
    
    public RemovedPackages(Map<String, List<String>> params) {
        this.excludedPackagesPrefixes = params.prefixes as List<String>
        this.excludedPackages = params.exact as List<String>
    }
    
    Optional<Violation> transform(String type, Violation v) {
        String pkg = type.substring(0, type.lastIndexOf('.'))

        if (excludedPackagesPrefixes.any { pkg.startsWith(it) }) {
            return Optional.of(v.withSeverity(Severity.accepted))
        } else if (excludedPackages.any { pkg == it }) {
            return Optional.of(v.withSeverity(Severity.accepted))
        }
        return Optional.of(v)
    }
}

