package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.FieldInjectionPoint
import io.micronaut.inject.MethodInjectionPoint
import spock.lang.Requires

import javax.inject.Singleton
import javax.validation.Valid
import javax.validation.constraints.Min

class AnnotationsOnGenericTypesSpec extends AbstractTypeElementSpec {

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on type arguments for executable methods'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics.Test', '''
package io.micronaut.inject.annotationgenerics;

import io.micronaut.context.annotation.Executable;
import javax.inject.Singleton;
import java.util.List;
import java.util.List;
import javax.validation.constraints.Min;

@Singleton
class Test {

    @Executable
    void test(List<@Min(10) Long> values) {
    
    }
}
''')
        def method = definition.getRequiredMethod("test", List)
        def annotationMetadata = method.arguments[0].typeParameters[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Min)
    }

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on type arguments for executable methods exclude type level annotations'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics.Test', '''
package io.micronaut.inject.annotationgenerics;

import io.micronaut.context.annotation.Executable;
import javax.inject.Singleton;
import java.util.List;
import java.util.List;
import javax.validation.Valid;

@Singleton
class Test {

    @Executable
    void test(List<@Valid Foo> values) {
    
    }
}

@javax.inject.Singleton
class Foo {

}
''')
        def method = definition.getRequiredMethod("test", List)
        def annotationMetadata = method.arguments[0].typeParameters[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Valid)
        !annotationMetadata.hasAnnotation(Singleton)
    }

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on parameters exclude type level annotations at runtime'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics.Test', '''
package io.micronaut.inject.annotationgenerics;

import io.micronaut.context.annotation.Executable;
import javax.inject.Singleton;
import java.util.List;
import java.util.List;
import javax.validation.Valid;

@Singleton
class Test {

    @Executable
    void test(@Valid Foo value) {
    
    }
}

@javax.inject.Singleton
class Foo {

}
''')

        def method = definition.getRequiredMethod("test", definition.beanType.classLoader.loadClass('io.micronaut.inject.annotationgenerics.Foo'))
        def annotationMetadata = method.arguments[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Valid)
        !annotationMetadata.hasAnnotation(Singleton)
    }

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on type arguments return types'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics2.Test', '''
package io.micronaut.inject.annotationgenerics2;

import io.micronaut.context.annotation.Executable;
import javax.inject.Singleton;
import java.util.List;
import java.util.List;
import javax.validation.constraints.Min;

@Singleton
class Test {

    @Executable
    List<@Min(10) Long> test(List<@Min(10) Long> values) {
         return values;
    }
}
''')
        def method = definition.getRequiredMethod("test", List)
        def annotationMetadata = method.returnType.asArgument().typeParameters[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Min)
    }

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on generics in field injection points'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics2.Test', '''
package io.micronaut.inject.annotationgenerics2;

import io.micronaut.context.annotation.*;
import javax.inject.Singleton;
import java.util.List;
import java.util.List;
import javax.validation.constraints.Min;

@Singleton
class Test {

    @Value("foo.bar")
    List<@Min(10) Long> values;
}
''')
        FieldInjectionPoint injectionPoint = definition.injectedFields.iterator().next()
        def annotationMetadata = injectionPoint.asArgument().typeParameters[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Min)
    }

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on generics in method argument injection points'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics2.Test', '''
package io.micronaut.inject.annotationgenerics2;

import io.micronaut.context.annotation.*;
import javax.inject.*;
import java.util.List;
import java.util.List;
import javax.validation.constraints.Min;

@Singleton
class Test {

    @Inject
    void test(List<@Min(10) Long> values) {
    
    }
}
''')
        MethodInjectionPoint injectionPoint = definition.injectedMethods.iterator().next()
        def annotationMetadata = injectionPoint.arguments[0].typeParameters[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Min)
    }
}
