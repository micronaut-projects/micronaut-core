/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.propertystore

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParametersRequest
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParametersResult
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterRequest
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterResult
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.discovery.aws.parameterstore.AWSParameterStoreConfigClient
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Tests real calling to AWS Property store. If you are having trouble getting the feature working get this test running.
 * Set the environment variable ENABLE_AWS_PARAMETER_STORE to enable running of this test.
 * @author Rvanderwerf
 * @since 1.0
 */
@IgnoreIf({
    System.out.println(System.getenv('ENABLE_AWS_PARAMETER_STORE'))
    !System.getenv('ENABLE_AWS_PARAMETER_STORE')
})
@Stepwise
class AWSParameterStoreClientSpec extends Specification {

    @AutoCleanup @Shared EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'aws.client.system-manager.parameterstore.enabled': 'true',
                    'aws.client.system-manager.parameterstore.useSecureParameters' : 'true',
                    'micronaut.application.name':'amazonTest'],
            Environment.AMAZON_EC2,Environment.TEST, Environment.CLOUD

    )
    @Shared AWSParameterStoreConfigClient client = embeddedServer.applicationContext.getBean(AWSParameterStoreConfigClient)
    // just for creating/dropping test data we use async version for micronaut
    @Shared AWSSimpleSystemsManagement awsSimpleSystemsManagement = AWSSimpleSystemsManagementClientBuilder.standard().withClientConfiguration(client.awsConfiguration.clientConfiguration).build()

    def setupSpec() {
        createTestData()
    }

    void "test is a configuration client"() {
        expect:
        client instanceof AWSParameterStoreConfigClient
        client instanceof ConfigurationClient
    }

    void "test discovery property sources from AWS Systems Manager Parameter Store - StringList, String, and SecureString"() {

        given:

        Environment environment = embeddedServer.environment

        when:

        Publisher<List<PropertySource>> propertySourcesPublisher = client.getPropertySources(environment)


        then: "verify property source characteristics"

        List<PropertySource> propertySources = Flowable.fromPublisher(propertySourcesPublisher).toList().blockingGet()
        propertySources.size() == 2
        propertySources[0].order > EnvironmentPropertySource.POSITION
        propertySources[0].name == 'route53-application'
        propertySources[0].get('datasource.url') == "mysql://blah"
        propertySources[0].get('datasource.driver') == "java.SomeDriver"
        propertySources[0].toList().size() == 3
        propertySources[1].name == 'route53-application[test]'
        propertySources[1].get("foo") == "bar"
        propertySources[1].order > propertySources[0].order
        propertySources[1].toList().size() == 1

    }

    def cleanupSpec() {
        deleteTestData()
    }

    private createTestData() {

        PutParameterRequest putParameterRequest = new PutParameterRequest()
        putParameterRequest.type = "StringList"
        putParameterRequest.name = "/config/application"
        putParameterRequest.value = "datasource.url=mysql://blah,datasource.driver=java.SomeDriver"
        putParameterRequest.overwrite = true
        PutParameterResult result = awsSimpleSystemsManagement.putParameter(putParameterRequest)
        assert result.version > 0

        putParameterRequest = new PutParameterRequest()
        putParameterRequest.type = "String"
        putParameterRequest.name = "/config/application_test"
        putParameterRequest.value = "foo=bar"
        putParameterRequest.overwrite = true
        result = awsSimpleSystemsManagement.putParameter(putParameterRequest)
        assert result.version > 0

        putParameterRequest = new PutParameterRequest()
        putParameterRequest.type = "SecureString"
        putParameterRequest.name = "/config/application/encryptedStuff"
        putParameterRequest.value = "encryptedValue=true"
        putParameterRequest.overwrite = true
        result = awsSimpleSystemsManagement.putParameter(putParameterRequest)
        assert result.version > 0

        GetParametersRequest request = new GetParametersRequest().withNames("/config/application").withWithDecryption(true)
        GetParametersResult pathResult = awsSimpleSystemsManagement.getParameters(request)
        assert pathResult.parameters
        assert pathResult.parameters.size() > 0

        request = new GetParametersRequest().withNames("/config/application_test").withWithDecryption(true)
        pathResult = awsSimpleSystemsManagement.getParameters(request)
        assert pathResult.parameters
        assert pathResult.parameters.size() > 0
    }

    private deleteTestData() {
        def params = ["/config/application", "/config/application_test","/config/application/encryptedStuff"]
        DeleteParametersRequest deleteParametersRequest = new DeleteParametersRequest().withNames(params)
        DeleteParametersResult result = awsSimpleSystemsManagement.deleteParameters(deleteParametersRequest)
        assert result.deletedParameters.containsAll(params)
    }
}
