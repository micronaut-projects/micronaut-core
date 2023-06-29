package io.micronaut.http.client.tck.netty.tests;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.client.tck.tests")
@SuiteDisplayName("HTTP Client TCK for the HTTP Client Implementation based on Netty")
@SuppressWarnings("java:S2187") // This runs a suite of tests, but has no tests of its own
@ExcludeClassNamePatterns("io.micronaut.http.client.tck.tests.textplain.TxtPlainBooleanTest")
public class NettyHttpMethodTests {
}
