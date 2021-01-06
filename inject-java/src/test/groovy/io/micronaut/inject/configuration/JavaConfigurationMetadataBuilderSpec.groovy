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

import com.sun.tools.javac.model.JavacElements
import com.sun.tools.javac.model.JavacTypes
import com.sun.tools.javac.processing.JavacProcessingEnvironment
import com.sun.tools.javac.util.Context
import groovy.json.JsonSlurper
import io.micronaut.annotation.processing.AnnotationUtils
import io.micronaut.annotation.processing.GenericUtils
import io.micronaut.annotation.processing.JavaConfigurationMetadataBuilder
import io.micronaut.annotation.processing.ModelUtils
import io.micronaut.inject.AbstractTypeElementSpec

import javax.lang.model.element.TypeElement
import javax.tools.*

import static java.nio.charset.StandardCharsets.UTF_8

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JavaConfigurationMetadataBuilderSpec extends AbstractTypeElementSpec {

    void "test build configuration metadata with annotation aliases"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.annotation.*;

@MultipleAlias("foo")
class MyProperties {
    protected String fieldTest = "unconfigured";
    private String internalField = "unconfigured";
    
    public void setSetterTest(String s) {
        this.internalField = s;
    }
    
    public String getSetter() { return this.internalField; } 
}
''')

        when:
        def builder = createBuilder()
        def configurationMetadata = builder.visitProperties(element, "some description")
        def propertyMetadata = builder.visitProperty(element, element, "java.lang.String", "setterTest", "some description", null)

        then:
        builder.configurations.size() == 1
        configurationMetadata.name == 'foo'
        configurationMetadata.description == 'some description'
        configurationMetadata.type == 'test.MyProperties'

        builder.properties.size() == 1
        propertyMetadata.name == 'setterTest'
        propertyMetadata.path == 'foo.setter-test'
        propertyMetadata.type == 'java.lang.String'
        propertyMetadata.declaringType == 'test.MyProperties'
        propertyMetadata.description == 'some description'


        when:"the config metadata is converted to JSON"
        def sw = new StringWriter()
        configurationMetadata.writeTo(sw)
        def text = sw.toString()
        def json = new JsonSlurper().parseText(text)

        then:"the json is correct"
        json.type == configurationMetadata.type
        json.name == configurationMetadata.name
        json.description == configurationMetadata.description


        when:"the property metadata is converted to JSON "

        sw = new StringWriter()
        propertyMetadata.writeTo(sw)
        text = sw.toString()
        println "text = $text"
        json = new JsonSlurper().parseText(text)

        then:"the json is correct"
        json.type == propertyMetadata.type
        json.name == propertyMetadata.path
        json.sourceType == propertyMetadata.declaringType
        json.description == propertyMetadata.description
    }

    void "test build configuration metadata for simple properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties {
    protected String fieldTest = "unconfigured";
    private String internalField = "unconfigured";
    
    public void setSetterTest(String s) {
        this.internalField = s;
    }
    
    public String getSetter() { return this.internalField; } 
}
''')

        when:
        def builder = createBuilder()
        def configurationMetadata = builder.visitProperties(element, "some description")
        def propertyMetadata = builder.visitProperty(element, element, "java.lang.String", "setterTest", "some description", null)

        then:
        builder.configurations.size() == 1
        configurationMetadata.name == 'foo'
        configurationMetadata.description == 'some description'
        configurationMetadata.type == 'test.MyProperties'

        builder.properties.size() == 1
        propertyMetadata.name == 'setterTest'
        propertyMetadata.path == 'foo.setter-test'
        propertyMetadata.type == 'java.lang.String'
        propertyMetadata.declaringType == 'test.MyProperties'
        propertyMetadata.description == 'some description'
        
        
        when:"the config metadata is converted to JSON"
        def sw = new StringWriter()
        configurationMetadata.writeTo(sw)
        def text = sw.toString()
        def json = new JsonSlurper().parseText(text)
        
        then:"the json is correct"
        json.type == configurationMetadata.type
        json.name == configurationMetadata.name
        json.description == configurationMetadata.description


        when:"the property metadata is converted to JSON "

        sw = new StringWriter()
        propertyMetadata.writeTo(sw)
        text = sw.toString()
        println "text = $text"
        json = new JsonSlurper().parseText(text)

        then:"the json is correct"
        json.type == propertyMetadata.type
        json.name == propertyMetadata.path
        json.sourceType == propertyMetadata.declaringType
        json.description == propertyMetadata.description
    }


    void "test build configuration metadata for inner class properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties {
    protected String fieldTest = "unconfigured";
    private String internalField = "unconfigured";
    
    public void setSetterTest(String s) {
        this.internalField = s;
    }
    
    public String getSetter() { return this.internalField; } 
    
    
    @ConfigurationProperties("inner")
    static class InnerProperties {
        protected String foo;
    }
}
''')


        when:


        JavaConfigurationMetadataBuilder builder = createBuilder()
        element = element.enclosedElements[0]
        builder.visitProperties(element, "some description")
        builder.visitProperty(element, element, "java.lang.String", "foo", "some description", null)

        then:
        builder.configurations.size() == 1
        builder.configurations[0].name == 'foo.inner'
        builder.configurations[0].description == 'some description'
        builder.configurations[0].type == 'test.MyProperties.InnerProperties'

        builder.properties.size() == 1
        builder.properties[0].name == 'foo'
        builder.properties[0].path == 'foo.inner.foo'
        builder.properties[0].type == 'java.lang.String'
        builder.properties[0].declaringType == 'test.MyProperties.InnerProperties'
        builder.properties[0].description == 'some description'
    }


    void "test build configuration metadata for multi level inner class properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties {
    protected String fieldTest = "unconfigured";
    private String internalField = "unconfigured";
    
    public void setSetterTest(String s) {
        this.internalField = s;
    }
    
    public String getSetter() { return this.internalField; } 
    
    
    @ConfigurationProperties("inner")
    static class InnerProperties {
        protected String foo;
        
        @ConfigurationProperties("nested")
        static class NestedProperties {
            protected String foo;
        }
    }
}
''')


        when:


        JavaConfigurationMetadataBuilder builder = createBuilder()
        element = element.enclosedElements[0].enclosedElements[0]
        builder.visitProperties(element, "some description")
        builder.visitProperty(element, element, "java.lang.String", "foo", "some description", null)

        then:
        builder.configurations.size() == 1
        builder.configurations[0].name == 'foo.inner.nested'
        builder.configurations[0].description == 'some description'
        builder.configurations[0].type == 'test.MyProperties.InnerProperties.NestedProperties'

        builder.properties.size() == 1
        builder.properties[0].name == 'foo'
        builder.properties[0].path == 'foo.inner.nested.foo'
        builder.properties[0].type == 'java.lang.String'
        builder.properties[0].declaringType == 'test.MyProperties.InnerProperties.NestedProperties'
        builder.properties[0].description == 'some description'
    }

    void "test build configuration metadata for single level inheritance properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("child")
class ChildProperties extends ParentProperties {
    protected String prop1 = "unconfigured";
}

@ConfigurationProperties("parent")
class ParentProperties {
    protected String prop2 = "test";
}
''')

        when:
        def builder = createBuilder()
        builder.visitProperties(element, "some description")
        builder.visitProperty(element, element, "java.lang.String", "setterTest", "some description", null)

        then:
        builder.configurations.size() == 1
        builder.configurations[0].name == 'parent.child'
        builder.configurations[0].description == 'some description'
        builder.configurations[0].type == 'test.ChildProperties'

        builder.properties.size() == 1
        builder.properties[0].name == 'setterTest'
        builder.properties[0].path == 'parent.child.setter-test'
        builder.properties[0].type == 'java.lang.String'
        builder.properties[0].declaringType == 'test.ChildProperties'
        builder.properties[0].description == 'some description'
    }

    void "test build configuration metadata for multi level inheritance properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("child")
class ChildProperties extends ParentProperties {
    protected String prop1 = "unconfigured";
}

@ConfigurationProperties("parent")
class ParentProperties extends GrandParentProperties {
    protected String prop2 = "test";
}

@ConfigurationProperties("grand")
class GrandParentProperties {
    protected String prop3 = "test";
}
''')

        when:
        def builder = createBuilder()
        builder.visitProperties(element, "some description")
        builder.visitProperty(element, element, "java.lang.String", "setterTest", "some description", null)

        then:
        builder.configurations.size() == 1
        builder.configurations[0].name == 'grand.parent.child'
        builder.configurations[0].description == 'some description'
        builder.configurations[0].type == 'test.ChildProperties'

        builder.properties.size() == 1
        builder.properties[0].name == 'setterTest'
        builder.properties[0].path == 'grand.parent.child.setter-test'
        builder.properties[0].type == 'java.lang.String'
        builder.properties[0].declaringType == 'test.ChildProperties'
        builder.properties[0].description == 'some description'
    }



    void "test build configuration metadata for multi level inheritance inner properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("child")
class ChildProperties extends ParentProperties {
    protected String prop1 = "unconfigured";
    
        
    @ConfigurationProperties("inner")
    static class InnerProperties {
        protected String foo;
    }
}

@ConfigurationProperties("parent")
class ParentProperties extends GrandParentProperties {
    protected String prop2 = "test";
}

@ConfigurationProperties("grand")
class GrandParentProperties {
    protected String prop3 = "test";
}
''')

        when:
        def builder = createBuilder()
        element = element.enclosedElements[0]
        builder.visitProperties(element, "some description")
        builder.visitProperty(element, element, "java.lang.String", "foo", "some description", null)

        then:
        builder.configurations.size() == 1
        builder.configurations[0].name == 'grand.parent.child.inner'
        builder.configurations[0].description == 'some description'
        builder.configurations[0].type == 'test.ChildProperties.InnerProperties'

        builder.properties.size() == 1
        builder.properties[0].name == 'foo'
        builder.properties[0].path == 'grand.parent.child.inner.foo'
        builder.properties[0].type == 'java.lang.String'
        builder.properties[0].declaringType == 'test.ChildProperties.InnerProperties'
        builder.properties[0].description == 'some description'
    }



    void "test build configuration metadata for multi level inheritance inner inheritance properties"() {
        given:
        TypeElement element = buildTypeElement('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("child")
class ChildProperties extends ParentProperties {
    protected String prop1 = "unconfigured";
    
        

    
    @ConfigurationProperties("inner")
    static class InnerProperties extends InnerParentProperties {
        protected String foo;
    }
    
    @ConfigurationProperties("innerParent")
    static class InnerParentProperties {
        protected String innerParent;
    }
}

@ConfigurationProperties("parent")
class ParentProperties extends GrandParentProperties {
    protected String prop2 = "test";
}

@ConfigurationProperties("grand")
class GrandParentProperties {
    protected String prop3 = "test";
}
''')

        when:
        def builder = createBuilder()
        element = element.enclosedElements[0]
        builder.visitProperties(element, "some description")
        builder.visitProperty(element, element, "java.lang.String", "foo", "some description", null)

        then:
        builder.configurations.size() == 1
        builder.configurations[0].name == 'grand.parent.child.inner-parent.inner'
        builder.configurations[0].description == 'some description'
        builder.configurations[0].type == 'test.ChildProperties.InnerProperties'

        builder.properties.size() == 1
        builder.properties[0].name == 'foo'
        builder.properties[0].path == 'grand.parent.child.inner-parent.inner.foo'
        builder.properties[0].type == 'java.lang.String'
        builder.properties[0].declaringType == 'test.ChildProperties.InnerProperties'
        builder.properties[0].description == 'some description'
    }

    protected JavaConfigurationMetadataBuilder createBuilder() {
        def context = new Context()
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>()
        context.put(JavaFileManager, compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8))
        def elements = JavacElements.instance(context)
        def types = JavacTypes.instance(context)
        def env = JavacProcessingEnvironment.instance(context)
        try {
            final com.sun.tools.javac.main.JavaCompiler jc = com.sun.tools.javac.main.JavaCompiler.instance(context);
            jc?.initModules(com.sun.tools.javac.util.List.nil())
        } catch (e) {
            // ignore, must be JDK 8
        }
        ModelUtils modelUtils = new ModelUtils(elements, env.typeUtils) {}
        GenericUtils genericUtils = new GenericUtils(elements, env.typeUtils, modelUtils) {}
        AnnotationUtils annotationUtils = new AnnotationUtils(env, elements, env.messager, env.typeUtils, modelUtils,genericUtils, env.filer) {
        }

        JavaConfigurationMetadataBuilder builder = new JavaConfigurationMetadataBuilder(
                elements,
                types,
                new AnnotationUtils(env, elements, env.messager, env.typeUtils, modelUtils, genericUtils, env.filer) {}
        )
        builder
    }
}
