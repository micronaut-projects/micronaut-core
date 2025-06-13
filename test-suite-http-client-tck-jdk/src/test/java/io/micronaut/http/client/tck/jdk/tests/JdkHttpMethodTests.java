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
@SuppressWarnings("java:S2187") // This runs a suite of tests, but has no tests of its own
@ExcludeClassNamePatterns({
    "io.micronaut.http.client.tck.tests.ContinueTest", // Unsupported body type errors
    "io.micronaut.http.client.tck.tests.RawTest", // There's no raw client for the JDK client
    "io.micronaut.http.client.tck.tests.StreamTest", // There's no streaming client for the JDK client
})
public class JdkHttpMethodTests {
}

