package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class MyIsEnumInTypeArgumentSpec extends AbstractKotlinCompilerSpec {

    void 'test generic type argument is enum'() {
        given:
            def definition = KotlinCompiler.buildIntroducedBeanDefinition('test.MyBean', '''
package test

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.kotlin.processing.aop.introduction.Stub

interface MyInterface {

    @Get
    @Produces(MediaType.TEXT_PLAIN)
    fun index(@Nullable @QueryValue("channels") channels: Collection<Channel?>?)

    @Introspected
    enum class Channel {
        @JsonProperty("mysys")
        SYSTEM1,
        SYSTEM2
    }
}

@io.micronaut.kotlin.processing.aop.introduction.RepoDef
interface MyBean: MyInterface
''')
        expect:
            definition.getExecutableMethods().size() == 1
    }

    static class MyControllerVisitor implements TypeElementVisitor<Object, Object> {

        boolean enable

        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            enable = element.name == "test.MyInterface"
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (enable) {
                if (element.name != "index") {
                    return
                }
                var param = element.parameters[0]
                if (param.name != "channels") {
                    return
                }
                if (!param.genericType.firstTypeArgument.get().isEnum()) {
                    throw new ProcessingException(element, "Type argument isEnum() must return true in this case");
                }
                element.annotate(Marker)
            }
        }
    }
}
