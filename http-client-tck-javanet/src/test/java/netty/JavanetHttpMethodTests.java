package netty;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.client.tck.tests")
@SuiteDisplayName("HTTP Client TCK for Java 11 based client")
public class JavanetHttpMethodTests {
}

