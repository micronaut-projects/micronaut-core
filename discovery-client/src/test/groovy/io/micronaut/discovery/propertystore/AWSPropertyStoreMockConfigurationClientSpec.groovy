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
                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.path == "/config/application") {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application"
                    parameter.value = "encryptedValue=true"
                    parameter.type = "StringList"
                    parameters.add(parameter)
                }
                result.setParameters(parameters)
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
                int end = 0
                if (getRequest.names.contains("/config/application")) {
                    end = 6
                }
                if (getRequest.names.contains("/config/amazon-test")) {
                    end = 5
                }
                if (getRequest.names.contains("/config/application_first")) {
                    end = 4
                }
                if (getRequest.names.contains("/config/amazon-test_first")) {
                    end = 3
                }
                if (getRequest.names.contains("/config/application_second")) {
                    end = 2
                }
                if (getRequest.names.contains("/config/amazon-test_second")) {
                    end = 1
                }
                int start = 1
                while (end > 0) {
                    Parameter parameter = new Parameter()
                    parameter.name = getRequest.names[0] + "/some/aws/value-" + start
                    parameter.value = end.toString()
                    parameter.type = "String"
                    parameters.add(parameter)
                    start++
                    end--
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
                    parameter.name = "/config/application/encryptedValue"
                    parameter.value = "true"
                    parameter.type = "String"
                    parameters.add(parameter)
                }
                result.setParameters(parameters)
                result;
            }
            return futureTask;


            when:
            def env = Mock(Environment)
            env.getActiveNames() >> (['test'] as Set)
            List<PropertySource> propertySources = Flowable.fromPublisher(client.getPropertySources(env)).toList().blockingGet()

            then: "verify property source characteristics"
            propertySources.size() == 2
            propertySources[0].order > EnvironmentPropertySource.POSITION
            propertySources[0].name == 'route53-application'
            propertySources[0].get('datasource.url') == "mysql://blah"
            propertySources[0].size() == 21
            propertySources[1].name == 'route53-application[test]'
            propertySources[1].get("foo") == "bar"
            propertySources[1].order > propertySources[0].order
            propertySources[1].toList().size() == 1
        }
    }

    void "given a nextToken from AWS, client should paginate to retrieve all properties"() {
        given:
        String paramPath = "/config/application"
        client.client.getParametersByPathAsync({ req -> req.nextToken == null} as GetParametersByPathRequest) >> { GetParametersByPathRequest req ->
            setupParamByPathResultMock(paramPath, req, 1..10)
        }
        client.client.getParametersByPathAsync({ req -> req.nextToken != null} as GetParametersByPathRequest) >> { GetParametersByPathRequest req ->
            setupParamByPathResultMock(paramPath, req, 11..20)
        }

        client.client.getParametersAsync(_) >> {  GetParametersRequest getRequest->
            FutureTask<GetParametersResult> futureTask = Mock(FutureTask)
            futureTask.isDone() >> { return true }
            futureTask.get() >> {
                GetParametersResult result = new GetParametersResult()
                ArrayList<Parameter> parameters = new ArrayList<Parameter>()
                if (getRequest.names.contains(paramPath)) {
                    Parameter parameter = new Parameter()
                    parameter.name = "/config/application/datasource/url"
                    parameter.value = "mysql://blah"
                    parameter.type = "String"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/application_test")) {
                    Parameter parameter1 = new Parameter()
                    parameter1.name = "/config/application_test/foo"
                    parameter1.value = "bar"
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
        propertySources[0].get('parameter-1') == "parameter-value-1"
        propertySources[0].get('parameter-15') == "parameter-value-15"
        propertySources[0].size() == 21
        propertySources[1].name == 'route53-application[test]'
        propertySources[1].get("foo") == "bar"
        propertySources[1].order > propertySources[0].order
        propertySources[1].toList().size() == 1

    }

    FutureTask setupParamByPathResultMock(String paramPath, GetParametersByPathRequest req, IntRange paramRange) {
        FutureTask<GetParametersByPathResult> futureTask = Mock(FutureTask)
        futureTask.isDone() >> { return true }
        futureTask.get() >> {
            GetParametersByPathResult result = new GetParametersByPathResult()
            ArrayList<Parameter> parameters = new ArrayList<Parameter>()
            if(req.path == paramPath) {
                (paramRange).each {
                    Parameter parameter = new Parameter()
                    parameter.name = "${paramPath}/parameter-${it}"
                    parameter.value = "parameter-value-${it}"
                    parameter.type = "String"
                    parameters.add(parameter)
                }

                result.nextToken = req.nextToken == null ? "nextPage" : null
            }

            result.setParameters(parameters)
            result
        }
        return futureTask
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
                    parameter.name = "/config/application/encryptedValue"
                    parameter.value = "true"
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
                    parameter.name = "/config/application/datasource/url"
                    parameter.value = "mysql://blah"
                    parameter.type = "SecureString"
                    parameters.add(parameter)
                }
                if (getRequest.names.contains("/config/application_test")) {
                    Parameter parameter1 = new Parameter()
                    parameter1.name = "/config/application_test/foo"
                    parameter1.value = "bar"
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
