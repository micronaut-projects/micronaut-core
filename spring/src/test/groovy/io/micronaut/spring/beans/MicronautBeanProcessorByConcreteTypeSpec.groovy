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
package io.micronaut.spring.beans

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import javax.inject.Singleton

@ContextConfiguration(classes = [ByConcreteTypeConfig])
class MicronautBeanProcessorByConcreteTypeSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void 'test widget bean'() {
        expect:
        applicationContext.getBean(Widget) instanceof Widget
    }
}

@Configuration
class ByConcreteTypeConfig {

    @Bean
    MicronautBeanProcessor widgetProcessor() {
        new MicronautBeanProcessor(Widget)
    }
}

@Singleton
class Widget {}
