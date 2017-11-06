/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.annotation

import com.sun.tools.javac.model.JavacElements
import com.sun.tools.javac.util.Context
import org.particleframework.aop.Around
import org.particleframework.context.annotation.Infrastructure
import org.particleframework.context.annotation.Primary
import org.particleframework.support.Parser
import spock.lang.Specification

import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JavaAnnotationMetadataBuilderSpec extends Specification {

    void "test parse first level stereotype data"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@org.particleframework.context.annotation.Primary
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test parse inherited stereotype data"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@org.particleframework.context.annotation.Infrastructure
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Infrastructure)
        metadata.hasDeclaredAnnotation(Infrastructure)
        metadata.hasStereotype(Singleton)
        metadata.hasStereotype(Scope)
        metadata.hasStereotype(org.particleframework.context.annotation.Context)
    }

    void "test parse inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@org.particleframework.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.Test'
        metadata.getValue(Trace, "types").get() == ['test.Test'] as Object[]
        !metadata.hasStereotype(Trace)
        metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }

    void "test parse interface inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;


class Test implements ITest{
}

@org.particleframework.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
interface ITest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.Test'
        metadata.getValue(Trace, "types").get() == ['test.Test'] as Object[]
        !metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }


    void "test parse super class inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;


class Test extends SuperTest{
}

@org.particleframework.inject.annotation.Trace(type = SuperTest.class, types = {SuperTest.class}, something = true)
class SuperTest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.SuperTest'
        metadata.getValue(Trace, "types").get() == ['test.SuperTest'] as Object[]
        !metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }


    void "test override super class inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@org.particleframework.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = false)
class Test extends SuperTest{
}

@org.particleframework.inject.annotation.Trace(type = SuperTest.class, types = {SuperTest.class}, something = true)
class SuperTest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.Test'
        metadata.getValue(Trace, "types").get() == ['test.Test'] as Object[]
        !metadata.hasStereotype(Trace)
        metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isFalse(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }

    void "test parse super class inherited interface stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;


class Test extends SuperTest{
}

class SuperTest implements ITest {}

@org.particleframework.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
interface ITest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.Test'
        metadata.getValue(Trace, "types").get() == ['test.Test'] as Object[]
        !metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }


    void "test parse first level method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;


class Test {
    @org.particleframework.context.annotation.Primary
    void testMethod() {}
}
''', 'testMethod')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test parse inherited from class method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;


@org.particleframework.context.annotation.Primary
class Test {
    void testMethod() {}
}
''', 'testMethod')

        expect:
        metadata != null
        !metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test parse inherited from interface method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;



class Test implements ITest {
    @Override
    public void testMethod() {}
}

interface ITest {
    @org.particleframework.context.annotation.Primary
    void testMethod(); 
}
''', 'testMethod')

        expect:
        metadata != null
        !metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    private AnnotationMetadata buildTypeAnnotationMetadata(String cls) {
        List<Element> elements = Parser.parseLines( "",
                cls
        ).toList()

        Element element = elements ? elements[0] : null
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(JavacElements.instance(new Context()))
        AnnotationMetadata metadata = element != null ? builder.build(element) : null
        return metadata
    }

    private AnnotationMetadata buildMethodAnnotationMetadata(String cls, String methodName) {
        List<Element> elements = Parser.parseLines( "",
                cls
        ).toList()

        TypeElement element = elements ? elements[0] : null
        Element method = element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(JavacElements.instance(new Context()))
        AnnotationMetadata metadata = method != null ? builder.build(method) : null
        return metadata
    }
}
