package io.micronaut.validation.validator

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.annotation.processing.SupportedAnnotationTypes
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

class ValidatorGroupsSpec extends AbstractTypeElementSpec {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()
    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test validate groups"() {
        given:
        def address = new Address(street: "")

        when:
        def violations = validator.validate(address, GroupThree)

        then:
        violations.size() == 1
        violations.iterator().next().message == 'different message'

        when:
        violations = validator.validate(address, GroupThree, GroupTwo)

        then:
        violations.size() == 2
    }

    void "test build introspection"() {
        given:
        def introspection = buildBeanIntrospection('test.Address', '''
package test;

import javax.validation.constraints.*;


@io.micronaut.core.annotation.Introspected
class Address {
    @NotBlank(groups = GroupOne.class)
    @NotBlank(groups = GroupThree.class, message = "different message")
    @Size(min = 5, max = 20, groups = GroupTwo.class)
    private String street;
    
    public String getStreet() {
        return this.street;
    }
}

interface GroupOne {}
interface GroupTwo {}
interface GroupThree {}
''')
        expect:
        introspection != null
    }


    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor()]
        }
    }
}

@Introspected
class Address {
    @NotBlank(groups = GroupOne)
    @NotBlank(groups = GroupThree, message = "different message")
    @Size(min = 5, max = 20, groups = GroupTwo)
    String street
}

interface GroupOne {}
interface GroupTwo {}
interface GroupThree {}