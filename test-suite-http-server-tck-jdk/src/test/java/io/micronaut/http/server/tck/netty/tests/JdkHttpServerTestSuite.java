package io.micronaut.http.server.tck.netty.tests;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests")
@SuiteDisplayName("HTTP Server TCK for Javanet client")
@ExcludeTags("multipart") // Multipart not supported by HttpClient
public class JdkHttpServerTestSuite {
}
