package io.micronaut.validation.validator.constraints

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.validation.validator.Validator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes
import javax.validation.constraints.AssertFalse
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Digits
import javax.validation.constraints.Email
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.validation.constraints.Null
import javax.validation.constraints.Size

class ConstraintMessagesSpec extends AbstractTypeElementSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    Validator validator = context.getBean(Validator)


    @Unroll
    void "test validation message for #annotation"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', """
package test;

@io.micronaut.core.annotation.Introspected
class Test {
    @${annotation.name}(${
            attributes ? attributes.entrySet().collect { it.key + '=' + getValString(it) }.join(',') : ''
        })
    private ${type.name} field;

    public ${type.name} getField() {
        return field;
    }
    
    public void setField(${type.name} f) {
        this.field = f;
    } 
}
""")
        def instance = introspection.instantiate()
        def prop = introspection.getProperty("field", type).get()
        prop.set(instance, value)
        def constraintViolations = validator.validate(introspection, instance)

        expect:
        constraintViolations.size() == 1
        constraintViolations.iterator().next().message == message


        where:
        annotation  | attributes                                                        | type    | value    | message
        AssertFalse | null                                                              | Boolean | true     | "must be false"
        AssertFalse | [message: "should be false!!"]                                    | Boolean | true     | "should be false!!"
        AssertTrue  | null                                                              | Boolean | false    | "must be true"
        DecimalMax  | [value: '1.0']                                                    | String  | '1.1'    | "must be less than or equal to 1.0"
        DecimalMax  | [value: '1.0', message: "{validatedValue} exceeds max: {value}"]  | String  | '1.1'    | "1.1 exceeds max: 1.0"
        DecimalMin  | [value: '1.0']                                                    | String  | '0.9'    | "must be greater than or equal to 1.0"
        Digits      | [integer: 2, fraction: 2]                                         | String  | '110.20' | "numeric value out of bounds (<2 digits>.<2 digits> expected)"
        Email       | null                                                              | String  | 'junk'   | "must be a well-formed email address"
        Max         | [value: 10]                                                       | Integer | 20       | "must be less than or equal to 10"
        Max         | [value: 10, message: "{validatedValue} is too big! max: {value}"] | Integer | 20       | "20 is too big! max: 10"
        Min         | [value: 10]                                                       | Integer | 5        | "must be greater than or equal to 10"
        Size        | [min: 10, max: 20]                                                | List    | [1]      | "size must be between 10 and 20"
        NotBlank    | null                                                              | String  | ''       | "must not be blank"
        NotEmpty    | null                                                              | List    | []       | "must not be empty"
        NotNull     | null                                                              | List    | null     | "must not be null"
        Null        | null                                                              | List    | []       | "must be null"
    }

    private String getValString(Map.Entry<String, Serializable> it) {
        def v = it.value
        if (v instanceof String) {
            return "\"$v\""
        }
        return v.inspect()
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
