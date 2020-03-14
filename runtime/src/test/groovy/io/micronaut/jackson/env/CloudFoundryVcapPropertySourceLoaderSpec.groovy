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
package io.micronaut.jackson.env

import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.ConfigurationException
import spock.lang.Specification

class CloudFoundryVcapPropertySourceLoaderSpec extends Specification {

    void "test properties are read from VCAP_APPLICATION"() {

        given:
        def loader = new CloudFoundryVcapApplicationPropertySourceLoader() {
            @Override
            protected String getEnvValue() {
                return '''\
{
    "application_users":null,
    "instance_id":"2ce0ac627a6c8e47e936d829a3a47b5b",
    "instance_index":0,
    "version":"0138c4a6-2a73-416b-aca0-572c09f7ca53",
    "name":"foo",
    "uris":["foo.cfapps.io"]
}   
'''
            }
        }

        when:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> ([] as Set)

        def result = loader.load(env)

        then:
        result.isPresent()

        when:
        PropertySource propertySource = result.get()

        then:
        propertySource.get("vcap.application.instance_id") == '2ce0ac627a6c8e47e936d829a3a47b5b'
        propertySource.get("vcap.application.instance_index") == 0
        propertySource.get("vcap.application.version") == '0138c4a6-2a73-416b-aca0-572c09f7ca53'
        propertySource.get("vcap.application.name") == 'foo'
        propertySource.get("vcap.application.uris")[0] == 'foo.cfapps.io'
    }

    void "test exception is thrown when VCAP_APPLICATION cannot be parsed"() {
        given:
        def loader = new CloudFoundryVcapApplicationPropertySourceLoader() {
            @Override
            protected String getEnvValue() {
                return 'UNPARSABLE:'
            }
        }

        when:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> ([] as Set)

        def result = loader.load(env)

        then:
        def e = thrown(ConfigurationException)
        e.getMessage().contains("Could not parse 'VCAP_APPLICATION'")
    }

    void "test properties are read from VCAP_SERVICES"() {

        given:
        def loader = new CloudFoundryVcapServicesPropertySourceLoader() {
            @Override
            protected String getEnvValue() {
                return '''\
{
    "rds-mysql-n/a": [{
        "name":"mysql",
        "label":"rds-mysql-n/a",
        "plan":"10mb",
        "credentials": {
            "name":"d04fb13d27d964c62b267bbba1cffb9da",
            "hostname":"mysql-service-public.clqg2e2w3ecf.us-east-1.rds.amazonaws.com",
            "ssl":true,
            "location":null,
            "host":"mysql-service-public.clqg2e2w3ecf.us-east-1.rds.amazonaws.com",
            "port":3306,
            "user":"urpRuqTf8Cpe6",
            "username":"urpRuqTf8Cpe6",
            "password":"pxLsGVpsC9A5S"
        }
    }],
    "service-without-name": [{
        "label":"service-without-name", 
        "credentials": {
            "foo": "bar"
        }
    }]
}'''
            }
        }

        when:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> ([] as Set)

        def result = loader.load(env)

        then:
        result.isPresent()

        when:
        PropertySource propertySource = result.get()

        then:
        propertySource.get("vcap.services.mysql.name") == "mysql"
        propertySource.get("vcap.services.mysql.credentials.port") == 3306
        propertySource.get("vcap.services.mysql.credentials.ssl") == true
        propertySource.get("vcap.services.mysql.credentials.location") == null

        propertySource.get("vcap.services.service-without-name.label") == 'service-without-name'
        propertySource.get("vcap.services.service-without-name.credentials.foo") == 'bar'
    }

    void "test exception is thrown when VCAP_SERVICES cannot be parsed"() {
        given:
        def loader = new CloudFoundryVcapServicesPropertySourceLoader() {
            @Override
            protected String getEnvValue() {
                return 'UNPARSABLE:'
            }
        }

        when:
        Environment env = Mock(Environment)
        env.isPresent(_) >> true
        env.getActiveNames() >> ([] as Set)

        def result = loader.load(env)

        then:
        def e = thrown(ConfigurationException)
        e.getMessage().contains("Could not parse 'VCAP_SERVICES'")
    }
}
