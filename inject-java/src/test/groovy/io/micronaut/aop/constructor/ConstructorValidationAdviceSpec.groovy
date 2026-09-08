package io.micronaut.aop.constructor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.validation.RequiresValidation
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.validation.ConstraintViolationException

/**
 * Jakarta Validation 5.1.2 requires an integration to validate a constrained constructor the container
 * invokes. A library does that by declaring construct advice on the constructor from its own
 * {@link TypeElementVisitor}; core supplies the mechanism and does not need to know the library's annotation.
 *
 * <p>These specs stand in for micronaut-validation with the smallest thing shaped like it:
 * {@code ConstraintVisitor} plays the part of its {@code ValidationVisitor}, which already visits every
 * constructor and already marks a constrained one with {@link RequiresValidation}, and
 * {@code ValidatingConstructorInterceptor} plays the part of a validating {@code ConstructorInterceptor},
 * reading the constraints off the invocation context and rejecting the construction the way a real validator
 * would.</p>
 */
class ConstructorValidationAdviceSpec extends AbstractTypeElementSpec {

    /** The advice annotation and the validating interceptor micronaut-validation would ship. */
    private static final String VALIDATION_STACK = '''
package test;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR})
@AroundConstruct
@interface ValidatedConstructor {}

/** A constraint on the constructor itself: the instance it returns must have a title. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
@jakarta.validation.Constraint(validatedBy = {})
@interface ValidEvent {
    String message() default "must have a title";
    Class<?>[] groups() default {};
    Class<? extends jakarta.validation.Payload>[] payload() default {};
}

interface Titled {
    String getTitle();
}

@Singleton
@InterceptorBinding(value = ValidatedConstructor.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class ValidatingConstructorInterceptor implements ConstructorInterceptor<Object> {

    public static final List<String> VALIDATED = new ArrayList<>();

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        VALIDATED.add(context.getDeclaringType().getSimpleName());

        Argument<?>[] arguments = context.getArguments();
        Object[] values = context.getParameterValues();
        for (int i = 0; i < arguments.length; i++) {
            int min = arguments[i].getAnnotationMetadata().intValue(Size.class, "min").orElse(-1);
            if (min >= 0 && values[i] instanceof String s && s.length() < min) {
                throw new ConstraintViolationException(
                    arguments[i].getName() + ": size must be at least " + min, Collections.emptySet());
            }
        }

        Object instance = context.proceed();

        if (context.getConstructor().getAnnotationMetadata().hasAnnotation(ValidEvent.class)
                && ((Titled) instance).getTitle().isEmpty()) {
            throw new ConstraintViolationException("<return value>: must have a title", Collections.emptySet());
        }
        return instance;
    }
}
'''

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new ConstraintVisitor()]
    }

    void "a constrained constructor parameter is validated when the container creates the bean"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Factory
class Titles {
    @Singleton
    String title() { return "Hi"; }
}

@Singleton
class Event implements Titled {
    private final String title;
    Event(@Size(min = 5) String title) { this.title = title; }
    @Override public String getTitle() { return title; }
}
''')

        when: 'the container resolves a title that violates the constraint'
        context.getBean(context.classLoader.loadClass('test.Event'))

        then: 'the violation reaches the caller as itself, not wrapped in a BeanInstantiationException'
        ConstraintViolationException e = thrown()
        e.message == 'title: size must be at least 5'

        cleanup:
        context.close()
    }

    void "a constructor whose arguments satisfy their constraints creates the bean"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Factory
class Titles {
    @Singleton
    String title() { return "Release party"; }
}

@Singleton
class Event implements Titled {
    private final String title;
    Event(@Size(min = 5) String title) { this.title = title; }
    @Override public String getTitle() { return title; }
}
''')

        when:
        def event = context.getBean(context.classLoader.loadClass('test.Event'))

        then:
        event.title == 'Release party'
        context.classLoader.loadClass('test.ValidatingConstructorInterceptor').VALIDATED == ['Event']

        cleanup:
        context.close()
    }

    void "a constraint declared on the constructor validates the instance it returns"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Factory
class Titles {
    @Singleton
    String title() { return ""; }
}

@Singleton
class Event implements Titled {
    private final String title;
    @ValidEvent
    Event(String title) { this.title = title; }
    @Override public String getTitle() { return title; }
}
''')

        when: 'the constructor runs, and the instance it produced violates the constructor\'s own constraint'
        context.getBean(context.classLoader.loadClass('test.Event'))

        then:
        ConstraintViolationException e = thrown()
        e.message == '<return value>: must have a title'

        cleanup:
        context.close()
    }

    void "an unconstrained constructor is not advised"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Singleton
class Venue {}

@Singleton
class Event implements Titled {
    Event(Venue venue) {}
    @Override public String getTitle() { return "Release party"; }
}
''')

        when:
        def event = context.getBean(context.classLoader.loadClass('test.Event'))

        then: 'construction goes straight to the constructor'
        event != null
        context.classLoader.loadClass('test.ValidatingConstructorInterceptor').VALIDATED.isEmpty()

        cleanup:
        context.close()
    }

    void "an around advised bean is validated once, on the construction of its proxy"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@interface Traced {}

@Singleton
@Traced
class TraceInterceptor implements MethodInterceptor<Object, Object> {
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
}

@Factory
class Titles {
    @Singleton
    String title() { return "Hi"; }
}

@Singleton
@Traced
class Event implements Titled {
    private final String title;
    Event(@Size(min = 5) String title) { this.title = title; }
    @Override public String getTitle() { return title; }
    String work() { return "done"; }
}
''')

        when:
        context.getBean(context.classLoader.loadClass('test.Event'))

        then: 'the proxy is constructed through the advice, which still describes the target constructor'
        ConstraintViolationException e = thrown()
        e.message == 'title: size must be at least 5'
        context.classLoader.loadClass('test.ValidatingConstructorInterceptor').VALIDATED == ['Event']

        cleanup:
        context.close()
    }

    void "an around advised bean that satisfies its constraints is still proxied"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@interface Traced {}

@Singleton
@Traced
class TraceInterceptor implements MethodInterceptor<Object, Object> {
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) { return context.proceed(); }
}

@Factory
class Titles {
    @Singleton
    String title() { return "Release party"; }
}

@Singleton
@Traced
class Event implements Titled {
    private final String title;
    Event(@Size(min = 5) String title) { this.title = title; }
    @Override public String getTitle() { return title; }
    String work() { return "done"; }
}
''')

        when:
        def event = context.getBean(context.classLoader.loadClass('test.Event'))

        then:
        event instanceof Intercepted
        event.work() == 'done'
        event.title == 'Release party'
        context.classLoader.loadClass('test.ValidatingConstructorInterceptor').VALIDATED == ['Event']

        cleanup:
        context.close()
    }

    void "an exception thrown by the constructor body of an advised bean is still wrapped"() {
        given:
        ApplicationContext context = buildContext(VALIDATION_STACK + '''
@Factory
class Titles {
    @Singleton
    String title() { return "Release party"; }
}

@Singleton
class Event implements Titled {
    Event(@Size(min = 5) String title) { throw new IllegalStateException("bad constructor"); }
    @Override public String getTitle() { return ""; }
}
''')

        when:
        context.getBean(context.classLoader.loadClass('test.Event'))

        then: 'the wrapping an unadvised constructor gets, unchanged'
        def e = thrown(io.micronaut.context.exceptions.BeanInstantiationException)
        e.cause instanceof IllegalStateException
        e.cause.message == 'bad constructor'

        cleanup:
        context.close()
    }

    /**
     * Stands in for micronaut-validation's {@code ValidationVisitor}, which already marks a constructor whose
     * parameters or return value carry constraints. Declaring the construct advice alongside that marker is
     * the one step it does not take today.
     */
    static class ConstraintVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitConstructor(ConstructorElement element, VisitorContext context) {
            boolean constrained = element.hasStereotype('jakarta.validation.Constraint')
                || element.getParameters().any { ParameterElement p -> p.hasStereotype('jakarta.validation.Constraint') }
            if (constrained) {
                element.annotate(RequiresValidation)
                element.annotate('test.ValidatedConstructor')
            }
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
