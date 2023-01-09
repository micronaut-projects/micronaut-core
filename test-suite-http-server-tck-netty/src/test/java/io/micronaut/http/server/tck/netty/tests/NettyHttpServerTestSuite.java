package io.micronaut.http.server.tck.netty.tests;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests.cors")
@IncludeClassNamePatterns("io.micronaut.http.server.tck.tests.cors.CrossOriginTest")
@SuiteDisplayName("HTTP Server TCK for Netty")
public class NettyHttpServerTestSuite {
}
