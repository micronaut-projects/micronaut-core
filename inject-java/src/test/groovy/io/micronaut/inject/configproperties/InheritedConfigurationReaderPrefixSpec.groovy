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
package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Property
import io.micronaut.inject.BeanDefinition

class InheritedConfigurationReaderPrefixSpec extends AbstractTypeElementSpec {

    void "property path is broken because alias is pointing to another alias"() {
        given:
            BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.configproperties.MyBean', """
package io.micronaut.inject.configproperties;

@TestEndpoint1("simple")
class MyBean  {
    String myValue;

    public String getMyValue() {
        return myValue;
    }

    public void setMyValue(String myValue) {
        this.myValue = myValue;
    }
}

""")

        expect:
            beanDefinition.getInjectedMethods()[0].name == 'setMyValue'
            def metadata = beanDefinition.getInjectedMethods()[0].getAnnotationMetadata()
            metadata.hasAnnotation(Property)
            metadata.getValue(Property, "name", String).get() == 'endpoints.my-value'
    }

    void "property path is overriding the existing one without base prefix"() {
        given:
            BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.configproperties.MyBean', """
package io.micronaut.inject.configproperties;

@TestEndpoint2("simple")
class MyBean  {
    String myValue;

    public String getMyValue() {
        return myValue;
    }

    public void setMyValue(String myValue) {
        this.myValue = myValue;
    }
}

""")

        expect:
            beanDefinition.getInjectedMethods()[0].name == 'setMyValue'
            def metadata = beanDefinition.getInjectedMethods()[0].getAnnotationMetadata()
            metadata.hasAnnotation(Property)
            metadata.getValue(Property, "name", String).get() == 'simple.my-value'
    }

    void "property path is broken because alias is pointing to another alias 2"() {
        given:
            BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.configproperties.MyBean', """
package io.micronaut.inject.configproperties;

@TestEndpoint3("simple")
class MyBean  {
    String myValue;

    public String getMyValue() {
        return myValue;
    }

    public void setMyValue(String myValue) {
        this.myValue = myValue;
    }
}

""")

        expect:
            beanDefinition.getInjectedMethods()[0].name == 'setMyValue'
            def metadata = beanDefinition.getInjectedMethods()[0].getAnnotationMetadata()
            metadata.hasAnnotation(Property)
            metadata.getValue(Property, "name", String).get() == 'endpoints.my-value'
    }

    void "property path is overriding the existing one"() {
        given:
            BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.configproperties.MyBean', """
package io.micronaut.inject.configproperties;

@TestEndpoint4("simple")
class MyBean  {
    String myValue;

    public String getMyValue() {
        return myValue;
    }

    public void setMyValue(String myValue) {
        this.myValue = myValue;
    }
}

""")

        expect:
            beanDefinition.getInjectedMethods()[0].name == 'setMyValue'
            def metadata = beanDefinition.getInjectedMethods()[0].getAnnotationMetadata()
            metadata.hasAnnotation(Property)
            metadata.getValue(Property, "name", String).get() == 'endpoints.simple.my-value'
    }
}
