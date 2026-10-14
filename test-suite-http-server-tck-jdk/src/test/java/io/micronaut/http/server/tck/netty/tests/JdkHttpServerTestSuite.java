package io.micronaut.http.server.tck.netty.tests;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests")
@SuiteDisplayName("HTTP Server TCK for Javanet client")
@ExcludeClassNamePatterns({
    "io.micronaut.http.server.tck.tests.FilterProxyTest", // HttpClient.sendAsync completes on the common ForkJoinPool, whose threads carry no leak detection scope, so the server's buffer allocations fail in this harness
    "io.micronaut.http.server.tck.tests.raw.RawProxyTest", // HttpClient.sendAsync completes on the common ForkJoinPool, whose threads carry no leak detection scope, so the server's buffer allocations fail in this harness
    "io.micronaut.http.server.tck.tests.raw.UpgradeRelayTest", // the JDK client cannot switch a connection to another protocol
    "io.micronaut.http.server.tck.tests.raw.TrailersRelayTest", // the JDK client neither sends nor receives trailers
    "io.micronaut.http.server.tck.tests.forms.FormsJacksonAnnotationsTest", // it seems application/x-www-form-urlencoded is not yet supported by the JDK client
    "io.micronaut.http.server.tck.tests.forms.UploadTest" // multipart
})
@ExcludeTags("multipart") // Multipart not supported by HttpClient
@ConfigurationParameter(key = "junit.jupiter.extensions.autodetection.enabled", value = "true")
public class JdkHttpServerTestSuite {
}
