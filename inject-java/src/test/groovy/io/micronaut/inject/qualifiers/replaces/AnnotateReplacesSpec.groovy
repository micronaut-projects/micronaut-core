package io.micronaut.inject.qualifiers.replaces

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import javax.annotation.processing.SupportedAnnotationTypes
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class AnnotateReplacesSpec extends AbstractTypeElementSpec {
    void 'test that replaces can be applied at compile time to a factory method'() {
        given:
        def context = buildContext('''
package annreplaces;

import io.micronaut.inject.qualifiers.replaces.TestProduces;
import io.micronaut.inject.qualifiers.replaces.TestSpecializes;
import jakarta.inject.*;
import io.micronaut.context.annotation.Factory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
class Catalog {
    @Inject
    public PaymentProcessor paymentProcessor;
}
@Singleton
@Factory
class Shop {
    @TestProduces
    PaymentProcessor getPaymentProcessor() {
        return new CreditCardProcessor();
    }
    @TestProduces
    List<Product> getProducts() {
        return Arrays.asList(
                new Product("Apple"),
                new Product("Orange")
        );
    }
}

@TestSpecializes
@Factory
class MockShop extends Shop {
    @Override @TestSpecializes
    @TestProduces
    PaymentProcessor getPaymentProcessor() {
        return new MockPaymentProcessor();
    }

    @Override @TestSpecializes
    @TestProduces
    List<Product> getProducts() {
        return Collections.singletonList(new Product("Mocked"));
    }
}

class MockPaymentProcessor implements PaymentProcessor {}

interface PaymentProcessor {
}
class CreditCardProcessor implements PaymentProcessor {}
class Product {
    final String name;

    Product(String name) {
        this.name = name;
    }
}

''')
        def bean = getBean(context, 'annreplaces.Catalog')

        expect:
        bean
        getBean(context, 'annreplaces.MockShop')
        bean.paymentProcessor.getClass().name.contains("Mock")

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
            return [new MySpecializesVisitor(), new MyProducesVisitor()]
        }
    }
    static class MySpecializesVisitor implements TypeElementVisitor<Object, TestSpecializes> {
        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            element.annotate(Replaces.class, (builder) -> {
                builder.member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(element.getGenericReturnType().getName()));
                builder.member("factory", new AnnotationClassValue<>(element.getDeclaringType().getSuperType().get().getName()));
            });
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
    static class MyProducesVisitor implements TypeElementVisitor<Object, TestProduces> {
        ClassElement currentClass

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            this.currentClass = element;
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (!this.currentClass.hasAnnotation(Factory.class)) {
                this.currentClass.annotate(Factory.class);
            }
            element.annotate(Bean)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface TestSpecializes {}
@Retention(RetentionPolicy.RUNTIME)
@interface TestProduces {}
