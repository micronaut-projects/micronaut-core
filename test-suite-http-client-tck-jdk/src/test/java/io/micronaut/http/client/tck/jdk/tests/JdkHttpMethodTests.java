package io.micronaut.http.client.tck.jdk.tests;

import io.micronaut.http.client.tck.tests.ClientDisabledCondition;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * <a href="https://openjdk.org/groups/net/httpclient/intro.html">Java HTTP Client</a>
 */
@Suite
@SelectPackages("io.micronaut.http.client.tck.tests")
@SuiteDisplayName("HTTP Client TCK for the HTTP Client Implementation based on Java HTTP Client")
@ConfigurationParameter(key = ClientDisabledCondition.HTTP_CLIENT_CONFIGURATION, value = ClientDisabledCondition.JDK)
@ConfigurationParameter(key = "junit.jupiter.extensions.autodetection.enabled", value = "true")
@SuppressWarnings("java:S2187") // This runs a suite of tests, but has no tests of its own
@ExcludeClassNamePatterns({
    "io.micronaut.http.client.tck.tests.ContinueTest", // Unsupported body type errors
    "io.micronaut.http.client.tck.tests.RawTest", // There's no raw client for the JDK client
    "io.micronaut.http.client.tck.tests.AsyncProxyHttpClientRelayTest", // Relaying a server request through the JDK proxy client times out in this runner, with the reactive proxy too
    "io.micronaut.http.client.tck.tests.StreamTest", // dataStreamRelease: the TCK leak detector flags the buffers the JDK client threads create
    "io.micronaut.http.client.tck.tests.DecompressionConfigTest", // Netty-specific decompression behavior; not applicable to JDK client
    "io.micronaut.http.client.tck.tests.RedirectHeaderCopyTest", // The JDK client does not send the Proxy-Authorization header to a server that is not a proxy
})
public class JdkHttpMethodTests {
}
