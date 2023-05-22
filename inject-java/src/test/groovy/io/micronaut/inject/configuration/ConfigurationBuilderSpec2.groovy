package io.micronaut.inject.configuration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource

/**
 * This is a test for the failure in the AWS module here:
 *
 * https://github.com/micronaut-projects/micronaut-aws/blob/df4be0bedb8ae58cc02abb22475d0e9eba465064/function-client-aws/src/test/groovy/io/micronaut/function/client/aws/AwsLambdaInvokeSpec.groovy#L69-L87
 *
 * The AWSLambdaConfiguration https://github.com/micronaut-projects/micronaut-aws/blob/df4be0bedb8ae58cc02abb22475d0e9eba465064/function-client-aws/src/main/java/io/micronaut/function/client/aws/AWSLambdaConfiguration.java
 * uses a configuration builder based on AWSLambdaAsyncClientBuilder which is in a hierarchy of subclassed builders eventually using AwsClientBuilder
 *
 * This is represented by the HierarchyConfig class in this test.
 */
class ConfigurationBuilderSpec2 extends AbstractTypeElementSpec {

    void "test definition uses getter instead of field"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
package test;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.inject.configuration.HierarchyConfig;

@ConfigurationProperties("test.props")
final class TestProps {

    @ConfigurationBuilder(prefixes = "with")
    private HierarchyConfig.RealizedBuilder builder = new HierarchyConfig.RealizedBuilder();

    public HierarchyConfig.RealizedBuilder getBuilder() {
        return builder;
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.name": "Tim Yates"]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        ctx.getProperty("test.props.name", String).get() == "Tim Yates"
        testPropBean.builder.build().name == "Tim Yates"

        cleanup:
        ctx.close()
    }
}
