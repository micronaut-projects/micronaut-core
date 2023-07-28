package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class InheritedNullableAnnotationSpec extends AbstractTypeElementSpec {

    def "setting inherited on nullable annotation"() {
        given:
        def introspection = buildBeanDefinition('test.ControllerImplementation', '''
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Controller
class ControllerImplementation implements ControllerInterface {
    @Get("/defined")
    public String defined(@Nullable @Header(value = "header1") String header1) {
        return header1;
    }

    @Override
    public String inherited(String header1) {
        return header1;
    }

     @Override
    public String inherited2(String header1) {
        return header1;
    }
}

@Controller
interface ControllerInterface {

    @Get(value = "/inherited", produces = { "application/json" })
    default String inherited(@Nullable(inherited=true) @Header(value = "header1") String header1) {
        return header1;
    }

     @Get(value = "/inherited", produces = { "application/json" })
    default String inherited2(@NonNull(inherited=true) @Header(value = "header1") String header1) {
        return header1;
    }
}
''')
        def definedArgument = introspection.findMethod('defined', String).get().arguments.first()
        def inheritedArgument = introspection.findMethod('inherited', String).get().arguments.first()
        def inheritedArgument2 = introspection.findMethod('inherited2', String).get().arguments.first()

        expect:
        definedArgument.isNullable()
        inheritedArgument.isNullable()
        inheritedArgument2.isNonNull()
    }

    def "custom inherited nullable annotations can be defined and used"() {
        given:
        def introspection = buildBeanDefinition('test.ControllerImplementation', '''
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.core.annotation.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Controller
class ControllerImplementation implements ControllerInterface {
    @Get("/defined")
    public String defined(@Nullable @Header(value = "header1") String header1) {
        return header1;
    }

    @Override
    public String inherited(String header1) {
        return header1;
    }
}

@Controller
interface ControllerInterface {

    @Get(value = "/inherited", produces = { "application/json" })
    default String inherited(@InheritedNullable @Header(value = "header1") String header1) {
        return header1;
    }
}

@Inherited
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Nullable
@interface InheritedNullable {
}
''')
        def definedArgument = introspection.findMethod('defined', String).get().arguments.first()
        def inheritedArgument = introspection.findMethod('inherited', String).get().arguments.first()

        expect:
        definedArgument.isNullable()
        inheritedArgument.isNullable()
    }
}
