package io.micronaut.http.client.tck.netty.tests;

import io.micronaut.http.client.tck.tests.ClientDisabledCondition;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.client.tck.tests")
@SuiteDisplayName("HTTP Client TCK for the HTTP Client Implementation based on Netty")
@ConfigurationParameter(key = ClientDisabledCondition.HTTP_CLIENT_CONFIGURATION, value = ClientDisabledCondition.NETTY)
@SuppressWarnings("java:S2187") // This runs a suite of tests, but has no tests of its own
public class NettyHttpMethodTests {
}
