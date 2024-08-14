package io.micronaut.http.server.tck.netty.tests;

import io.micronaut.http.server.tck.tests.NoBodyResponseTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
//@SelectPackages("io.micronaut.http.server.tck.tests")
@SelectClasses(NoBodyResponseTest.class)
@SuiteDisplayName("HTTP Server TCK for Netty")
public class NettyHttpServerTestSuite {
}
