package io.micronaut.http.server.tck.netty.tests;

import org.junit.platform.suite.api.*;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests")
@ExcludeClassNamePatterns("io.micronaut.http.server.tck.tests.forms.FormUrlEncodedBodyInRequestFilterTest") // It is flaky in HTTP 2
@SuiteDisplayName("HTTP Server TCK for Netty")
public class NettyHttpServerTestSuite {
}
