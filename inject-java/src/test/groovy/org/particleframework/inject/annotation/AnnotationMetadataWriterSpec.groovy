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

import org.particleframework.aop.Around
import org.particleframework.context.annotation.Primary
import org.particleframework.core.annotation.AnnotationMetadata

import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationMetadataWriterSpec extends AbstractTypeElementSpec {

    void "test write first level stereotype data"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@org.particleframework.context.annotation.Primary
class Test {
}
''')


        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        !metadata.hasStereotype(Documented) // ignore internal annotations
        !metadata.hasStereotype(Retention) // ignore internal annotations
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test write inherited stereotype data attributes"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@org.particleframework.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.Test'
        metadata.getValue(Trace, "types").get() == ['test.Test'] as Object[]
        metadata.hasStereotype(Trace)
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

    void "test write super class inherited interface stereotype data attributes"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;


class Test extends SuperTest{
}

class SuperTest implements ITest {}

@org.particleframework.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
interface ITest {

}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == 'test.Test'
        metadata.getValue(Trace, "types").get() == ['test.Test'] as Object[]
        metadata.hasStereotype(Trace)
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
        metadata.getAnnotationNamesByStereotype(Around.name) == [Trace.name] as Set
    }

}
