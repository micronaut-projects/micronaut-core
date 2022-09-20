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
package io.micronaut.inject.configuration


import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
/**
 * @author graemerocher
 * @since 1.0
 */
class GroovyConfigurationMetadataBuilderSpec extends AbstractBeanDefinitionSpec {

//    void "test build configuration metadata with annotation aliases"() {
//        given:
//        ClassNode element = buildClassNode('''
//package test;
//
//import io.micronaut.context.annotation.*;
//import io.micronaut.inject.annotation.*;
//
//@MultipleAlias("foo")
//class MyProperties {
//    protected String fieldTest = "unconfigured";
//    private String internalField = "unconfigured";
//
//    public void setSetterTest(String s) {
//        this.internalField = s;
//    }
//
//    public String getSetter() { return this.internalField; }
//}
//''', "test.MyProperties")
//
//        when:
//        ConfigurationMetadataBuilder builder = new ConfigurationMetadataBuilder()
//        def configurationMetadata = builder.visitProperties(element, "some description")
//        def propertyMetadata = builder.visitProperty(element, element, "java.lang.String", "setterTest", "some description", null)
//
//        then:
//        builder.configurations.size() == 1
//        configurationMetadata.name == 'foo'
//        configurationMetadata.description == 'some description'
//        configurationMetadata.type == 'test.MyProperties'
//
//        builder.properties.size() == 1
//        propertyMetadata.name == 'setterTest'
//        propertyMetadata.path == 'foo.setter-test'
//        propertyMetadata.type == 'java.lang.String'
//        propertyMetadata.declaringType == 'test.MyProperties'
//        propertyMetadata.description == 'some description'
//
//
//        when:"the config metadata is converted to JSON"
//        def sw = new StringWriter()
//        configurationMetadata.writeTo(sw)
//        def text = sw.toString()
//        def json = new JsonSlurper().parseText(text)
//
//        then:"the json is correct"
//        json.type == configurationMetadata.type
//        json.name == configurationMetadata.name
//        json.description == configurationMetadata.description
//
//
//        when:"the property metadata is converted to JSON "
//
//        sw = new StringWriter()
//        propertyMetadata.writeTo(sw)
//        text = sw.toString()
//        println "text = $text"
//        json = new JsonSlurper().parseText(text)
//
//        then:"the json is correct"
//        json.type == propertyMetadata.type
//        json.name == propertyMetadata.path
//        json.sourceType == propertyMetadata.declaringType
//        json.description == propertyMetadata.description
//    }
}
