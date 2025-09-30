package io.micronaut.http.server.tck.netty.tests;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests")
@SuiteDisplayName("HTTP Server TCK for Javanet client")
@ExcludeClassNamePatterns({
    "io.micronaut.http.server.tck.tests.FilterProxyTest", // There's no proxy client for the JDK client
    "io.micronaut.http.server.tck.tests.forms.FormsJacksonAnnotationsTest", // it seems application/x-www-form-urlencoded is not yet supported by the JDK client
    "io.micronaut.http.server.tck.tests.forms.UploadTest" // multipart
})
@ExcludeTags("multipart") // Multipart not supported by HttpClient
public class JdkHttpServerTestSuite {
}
