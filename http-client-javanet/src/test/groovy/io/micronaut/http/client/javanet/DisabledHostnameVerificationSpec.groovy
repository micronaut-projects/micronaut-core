package io.micronaut.http.client.javanet

import spock.lang.Specification

abstract class DisabledHostnameVerificationSpec extends Specification {

    def setupSpec() {
        // https://bugs.openjdk.org/browse/JDK-8213309
        // There's no way to programmatically disable hostname verification in the Java 11 HttpClient
        System.properties['jdk.internal.httpclient.disableHostnameVerification'] = 'true'
    }

    def cleanupSpec() {
        System.properties.remove('jdk.internal.httpclient.disableHostnameVerification')
    }
}
