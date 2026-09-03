/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class FactoryConfigurationPropertiesSpec extends AbstractTypeElementSpec {

    void "test a factory produced configuration properties bean is bound to configuration"() {
        given:
        ApplicationContext context = buildContext('test.MyFactory', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

@ConfigurationProperties("test.config")
class MyConfig {
    private String host;
    private int port = 1;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

@Factory
class MyFactory {

    @Singleton
    @Replaces(MyConfig.class)
    MyConfig myConfig() {
        MyConfig config = new MyConfig();
        config.setHost("from-factory");
        config.setPort(9999);
        return config;
    }
}
''', false, ['test.config.host': 'from-config'])

        when:
        def config = getBean(context, 'test.MyConfig')

        then: "the configured property overrides the value set by the factory"
        config.host == 'from-config'

        and: "a property with no configuration keeps the value set by the factory"
        config.port == 9999

        cleanup:
        context.close()
    }

    void "test a factory produced bean without configuration metadata is untouched"() {
        given:
        ApplicationContext context = buildContext('test.MyFactory', '''
package test;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

class Plain {
    private String host;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}

@Factory
class MyFactory {

    @Singleton
    Plain plain() {
        Plain plain = new Plain();
        plain.setHost("from-factory");
        return plain;
    }
}
''', false, ['test.config.host': 'from-config', 'plain.host': 'from-config'])

        when:
        def plain = getBean(context, 'test.Plain')

        then:
        plain.host == 'from-factory'

        cleanup:
        context.close()
    }
}
