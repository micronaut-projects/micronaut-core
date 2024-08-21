package io.micronaut.http.server.tck.netty.tests;

import io.micronaut.http.server.tck.tests.codec.JsonCodecAdditionalType2Test;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
//@SelectPackages("io.micronaut.http.server.tck.tests")
@SelectClasses(JsonCodecAdditionalType2Test.class)
@SuiteDisplayName("HTTP Server TCK for Netty")
public class NettyHttpServerTestSuite {
}
