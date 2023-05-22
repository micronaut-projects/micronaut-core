package io.micronaut.core.version

import spock.lang.Specification
import spock.lang.Unroll

class SemanticVersionSpec extends Specification {

    @Unroll
    void "it can parse semantic version: #semver"(String semver) {
        when:
        SemanticVersion version = new SemanticVersion(semver)

        then:
        noExceptionThrown()
        SemanticVersion.isAtLeast(version.version, "1.0.0")
        SemanticVersion.isAtLeastMajorMinor(version.version, 1, 0)

        where:
        semver << ["1.0.0", "1.0.0.M1", "1.0.0.RC1", "1.0.0.BUILD-SNAPSHOT", "1.0.0-M1", "1.0.0-RC1", "1.0.0-BUILD-SNAPSHOT", "1.0.0-SNAPSHOT"]
    }

    void "it compare two different major versions: #semver"(String semver) {
        expect:
        SemanticVersion.isAtLeastMajorMinor(semver, 3, 3)
        SemanticVersion.isAtLeastMajorMinor(semver, 3, 0)
        !SemanticVersion.isAtLeastMajorMinor(semver, 5, 3)
        !SemanticVersion.isAtLeastMajorMinor(semver, 5, 0)

        where:
        semver << ["4.0.0", "4.0.0.M1", "4.0.0.RC1", "4.0.0.BUILD-SNAPSHOT", "4.0.0-M1", "4.0.0-RC1", "4.0.0-BUILD-SNAPSHOT", "4.0.0-SNAPSHOT"]
    }

}
