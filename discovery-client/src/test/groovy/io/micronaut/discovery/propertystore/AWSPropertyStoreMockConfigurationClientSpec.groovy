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

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult
import com.amazonaws.services.simplesystemsmanagement.model.Parameter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourcePropertyResolver
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.order.OrderUtil
import io.micronaut.discovery.aws.parameterstore.AWSParameterStoreConfigClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import java.util.concurrent.FutureTask

/**
 * Test for mocking of aws property store.
 * @author RVanderwerf
 */
class AWSPropertyStoreMockConfigurationClientSpec extends Specification {

    static {
        System.setProperty("aws.region", "us-west-1")
    }

    @Shared
    int serverPort = SocketUtils.findAvailableTcpPort()

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'aws.client.system-manager.parameterstore.enabled': 'true',
                    'aws.system-manager.parameterstore.useSecureParameters' : 'false',
                    'micronaut.application.name':'amazonTest'],
            Environment.AMAZON_EC2

    )

    @Shared
    AWSParameterStoreConfigClient client = embeddedServer.applicationContext.getBean(AWSParameterStoreConfigClient)


    def setup() {
        client.client = Mock(AWSSimpleSystemsManagementAsync)
    }

    void "test discovery property sources from AWS Systems Manager Parameter Store - StringList"() {

        given:

        client.client.getParametersByPathAsync(_) >> { GetParametersByPathRequest getRequest ->

            FutureTask<GetParametersByPathResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersByPathResult result = new GetParametersByPathResult()
/*                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.path == "/config/application") {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "encryptedValue=true"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                result.setParameters(parameters)*/
                result;
            }
            return futureTask;

        }

        client.client.getParametersAsync(_) >> { GetParametersRequest getRequest->

            FutureTask<GetParametersResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersResult result = new GetParametersResult()

                ArrayList<Parameter> parameters = new ArrayList<Parameter>()

                if (getRequest.names.contains("/config/application")) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "some.aws.value-1=6,some.aws.value-2=5,some.aws.value-3=4,some.aws.value-4=3,some.aws.value-5=2,some.aws.value-6=1"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/amazon-test")) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/amazon-test"
                    parameter.value = "some.aws.value-1=5,some.aws.value-2=4,some.aws.value-3=3,some.aws.value-4=2,some.aws.value-5=1"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/application_first")) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application_first"
                    parameter.value = "some.aws.value-1=4,some.aws.value-2=3,some.aws.value-3=2,some.aws.value-4=1"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/amazon-test_first")) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/amazon-test_first"
                    parameter.value = "some.aws.value-1=3,some.aws.value-2=2,some.aws.value-3=1"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/application_second")) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application_second"
                    parameter.value = "some.aws.value-1=2,some.aws.value-2=1"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/amazon-test_second")) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/amazon-test_second"
                    parameter.value = "some.aws.value-1=1"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                result.setParameters(parameters)
                result
            }
            return futureTask
        }


        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['first', 'second'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(client.getPropertySources(env)).toList().blockingGet()
        propertySources.sort(OrderUtil.COMPARATOR)
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(propertySources as PropertySource[])

        then: "verify property source characteristics"
        propertySources.size() == 6
        propertySources[0].name == "route53-application"
        propertySources[1].name == "route53-amazon-test"
        propertySources[2].name == "route53-application[first]"
        propertySources[3].name == "route53-amazon-test[first]"
        propertySources[4].name == "route53-application[second]"
        propertySources[5].name == "route53-amazon-test[second]"

        propertySources[0].order > EnvironmentPropertySource.POSITION

        resolver.getRequiredProperty("some.aws.value-1", String) == "1"
        resolver.getRequiredProperty("some.aws.value-2", String) == "1"
        resolver.getRequiredProperty("some.aws.value-3", String) == "1"
        resolver.getRequiredProperty("some.aws.value-4", String) == "1"
        resolver.getRequiredProperty("some.aws.value-5", String) == "1"
        resolver.getRequiredProperty("some.aws.value-6", String) == "1"
    }


    void "test discovery property sources from AWS Systems Manager Parameter Store - String"() {

        given:

        client.client.getParametersByPathAsync(_) >> { GetParametersByPathRequest getRequest ->

            FutureTask<GetParametersByPathResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersByPathResult result = new GetParametersByPathResult()
                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.path == "/config/application") {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "encryptedValue=true"
                    parameter.type = "String"
                    parameters.add(parameter)
                }
                result.setParameters(parameters)
                result;
            }
            return futureTask;

        }

        client.client.getParametersAsync(_) >> {  GetParametersRequest getRequest->

            FutureTask<GetParametersResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersResult result = new GetParametersResult()
                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.names.contains("/config/application")) {

                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "datasource.url=mysql://blah"
                    parameter.type = "String"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/application_test")) {
                    Parameter parameter1 = new Parameter()
                    parameter1.name = "/config/application_test"
                    parameter1.value = "foo=bar"
                    parameter1.type = "String"
                    parameters.add(parameter1)
                }

                result.setParameters(parameters)
                result;
            }
            return futureTask;
        }



        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(client.getPropertySources(env)).toList().blockingGet()

        then: "verify property source characteristics"
        propertySources.size() == 2
        propertySources[0].order > EnvironmentPropertySource.POSITION
        propertySources[0].name == 'route53-application'
        propertySources[0].get('datasource.url') == "mysql://blah"
        propertySources[0].size() == 2
        propertySources[1].name == 'route53-application[test]'
        propertySources[1].get("foo") == "bar"
        propertySources[1].order > propertySources[0].order
        propertySources[1].toList().size() == 1
    }


    void "test discovery property sources from AWS Systems Manager Parameter Store - SecureString"() {

        given:

        client.client.getParametersByPathAsync(_) >> { GetParametersByPathRequest getRequest ->

            FutureTask<GetParametersByPathResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersByPathResult result = new GetParametersByPathResult()
                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.path == "/config/application") {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "encryptedValue=true"
                    parameter.type = "SecureString"
                    parameters.add(parameter)
                }
                result.setParameters(parameters)
                result;
            }
            return futureTask;

        }

        client.client.getParametersAsync(_) >> {  GetParametersRequest getRequest->

            FutureTask<GetParametersResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersResult result = new GetParametersResult()
                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.names.contains("/config/application")) {

                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "datasource.url=mysql://blah"
                    parameter.type = "SecureString"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/application_test")) {
                    Parameter parameter1 = new Parameter()
                    parameter1.name = "/config/application_test"
                    parameter1.value = "foo=bar"
                    parameter1.type = "SecureString"
                    parameters.add(parameter1)
                }

                result.setParameters(parameters)
                result;
            }
            return futureTask;
        }


        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(client.getPropertySources(env)).toList().blockingGet()

        then: "verify property source characteristics"
        propertySources.size() == 2
        propertySources[0].order > EnvironmentPropertySource.POSITION
        propertySources[0].name == 'route53-application'
        propertySources[0].get('datasource.url') == "mysql://blah"
        propertySources[0].size() == 2
        propertySources[1].name == 'route53-application[test]'
        propertySources[1].get("foo") == "bar"
        propertySources[1].order > propertySources[0].order
        propertySources[1].toList().size() == 1
    }

}
