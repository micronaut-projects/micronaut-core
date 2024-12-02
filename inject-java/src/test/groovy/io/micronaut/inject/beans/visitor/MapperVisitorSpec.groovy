package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.annotation.Mapper
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter


class MapperVisitorSpec extends AbstractTypeElementSpec {

    void 'test mapper visitor'() {
        given:
        def className = 'test.MyMapper'
        def loader = buildClassLoader(className, '''
package test;

import io.micronaut.context.annotation.Mapper;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Singleton
public abstract class MyMapper {

    @Mapper.Mapping(from = "typeB.a", to = "propA")
    @Mapper.Mapping(from = "b", to = "propB")
    abstract TypeA map(TypeB typeB);

}

@Introspected
record TypeA(
        String propA,
        String propB
) {}

@Introspected
record TypeB(
        String a,
        String b
) {}
        ''')

        def definition = loader.loadClass('test.$MyMapper' +  BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX).newInstance() as BeanDefinition<?>
        definition.annotationMetadata.hasAnnotation(Mapper.class)
        def binding = definition.annotationMetadata.getAnnotation(InterceptorBinding)
        binding != null
        binding.classValue().get() == Mapper.class
        binding.enumValue("kind", InterceptorKind).get() == InterceptorKind.INTRODUCTION
    }

    void 'test mapper visitor fail'() {
        when:
        buildClassLoader('test.MyMapper', """
package test;

import io.micronaut.context.annotation.Mapper;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

@Singleton
public abstract class MyMapper {

    ${annotation}
    abstract TypeA map(TypeB typeB);

}

@Introspected
record TypeA(
        String propA
) {}

@Introspected
record TypeB(
        String a
) {}
        """)

        then:
        var e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        annotation                                                  | message
        '@Mapper.Mapping(from = "typeB.nonExistent", to = "propA")' | '@Mapping(from="typeB.nonExistent") specifies property nonExistent that doesn\'t exist in type test.TypeB'
        '@Mapper.Mapping(from = "nonExistent", to = "propA")'       | '@Mapping(from="nonExistent") specifies property nonExistent that doesn\'t exist in type test.TypeB'
        '@Mapper.Mapping(from = "nonExistent.a", to = "propA")'     | '@Mapping(from="nonExistent.a") specifies argument nonExistent that doesn\'t exist for method'
    }
}
