package io.micronaut.http.server.tck.netty.tests;

import org.junit.platform.suite.api.*;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests")
@SuiteDisplayName("HTTP Server TCK for Netty")
// fails on native
@ExcludeClassNamePatterns("io.micronaut.http.server.tck.tests.staticresources.StaticResourceTest") 
public class NettyHttpServerTestSuite {
}
