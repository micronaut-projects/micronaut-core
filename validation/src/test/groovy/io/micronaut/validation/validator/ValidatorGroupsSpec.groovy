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
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Size
import javax.validation.groups.Default

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
        List messageTemplates = violations*.messageTemplate

        then:
        violations.size() == 2
        messageTemplates.contains('{javax.validation.constraints.Size.message}')
        messageTemplates.contains('different message')

        when:
        violations = validator.validate(address, InheritedGroup)
        messageTemplates = violations*.messageTemplate

        then:
        violations.size() == 2
        messageTemplates.contains('{javax.validation.constraints.Size.message}')
        messageTemplates.contains('message for default')
    }

    void "test validate with default group"() {
        def address = new AddressTwo(street: "", city: "", zipCode: "")

        when:
        def violations = validator.validate(address)
        List properties = violations*.propertyPath*.toString()

        then:
        violations.size() == 2
        properties.contains("zipCode")
        properties.contains("city")

        when:
        violations = validator.validate(address, GroupOne)
        properties = violations*.propertyPath*.toString()

        then:
        violations.size() == 2
        properties.contains("zipCode")
        properties.contains("street")

        when:
        violations = validator.validate(address, GroupOne, Default)
        properties = violations*.propertyPath*.toString()

        then:
        violations.size() == 3
        properties.contains("zipCode")
        properties.contains("street")
        properties.contains("city")
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
    @NotBlank(message = "message for default")
    @Size(min = 5, max = 20, groups = GroupTwo)
    String street
}

interface GroupOne {}
interface GroupTwo {}
interface GroupThree {}
interface InheritedGroup extends Default, GroupTwo {}

@Introspected
class AddressTwo {

    @NotEmpty(groups = GroupOne.class)
    String street

    @NotEmpty
    String city

    @NotEmpty(groups = [GroupOne.class, Default.class])
    String zipCode
}
