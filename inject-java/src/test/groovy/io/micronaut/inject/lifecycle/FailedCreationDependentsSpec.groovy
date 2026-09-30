package io.micronaut.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanCreationException

class FailedCreationDependentsSpec extends AbstractTypeElementSpec {

    void 'test the dependents of a bean whose constructor throws are destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package failed.constructor;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Inner {
    @PreDestroy
    void destroy() {
        Events.LOG.add("Inner");
    }
}

@Prototype
class Dependent {
    final Inner inner;

    Dependent(Inner inner) {
        this.inner = inner;
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent");
    }
}

@Singleton
class Failing {
    Failing(Dependent dependent) {
        throw new IllegalStateException("constructor failed");
    }
}
''')
        List<String> events = context.classLoader.loadClass('failed.constructor.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('failed.constructor.Failing'))

        then:
        def e = thrown(BeanCreationException)
        e.cause.message == 'constructor failed'

        and: 'the dependent is destroyed, and its own dependent with it'
        events == ['Dependent', 'Inner']

        cleanup:
        context.close()
    }

    void 'test the dependents of a bean whose post construct method throws are destroyed in reverse order'() {
        given:
        ApplicationContext context = buildContext('''
package failed.postconstruct;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Dependent {
    private static int count;
    final int id = ++count;

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent " + id);
    }
}

@Singleton
class Failing {
    @Inject
    Dependent field;

    Failing(Dependent constructor) {
    }

    @PostConstruct
    void init() {
        throw new IllegalStateException("post construct failed");
    }
}
''')
        List<String> events = context.classLoader.loadClass('failed.postconstruct.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('failed.postconstruct.Failing'))

        then:
        thrown(BeanCreationException)

        and: 'the field dependent was created after the constructor dependent, and is destroyed before it'
        events == ['Dependent 2', 'Dependent 1']

        cleanup:
        context.close()
    }

    void 'test the dependents of a prototype bean whose creation fails are destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package failed.prototype;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Dependent {
    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent");
    }
}

@Prototype
class Failing {
    Failing(Dependent dependent) {
    }

    @PostConstruct
    void init() {
        throw new IllegalStateException("post construct failed");
    }
}
''')
        Class<?> failingType = context.classLoader.loadClass('failed.prototype.Failing')
        List<String> events = context.classLoader.loadClass('failed.prototype.Events').LOG

        when:
        context.getBean(failingType)

        then:
        thrown(BeanCreationException)
        events == ['Dependent']

        when:
        context.getBean(failingType)

        then:
        thrown(BeanCreationException)
        events == ['Dependent', 'Dependent']

        cleanup:
        context.close()
    }

    void 'test the dependents of a bean are destroyed when the creation of a bean it depends on fails'() {
        given:
        ApplicationContext context = buildContext('''
package failed.nested;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Dependent {
    private static int count;
    final int id = ++count;

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent " + id);
    }
}

@Prototype
class Failing {
    Failing(Dependent dependent) {
        throw new IllegalStateException("constructor failed");
    }
}

@Singleton
class Parent {
    Parent(Dependent dependent, Failing failing) {
    }
}
''')
        List<String> events = context.classLoader.loadClass('failed.nested.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('failed.nested.Parent'))

        then:
        thrown(BeanCreationException)

        and: 'the dependent of the failed bean goes first, then the dependent the parent had created before it'
        events == ['Dependent 2', 'Dependent 1']

        cleanup:
        context.close()
    }

    void 'test a non-singleton factory is destroyed when its factory method throws'() {
        given:
        ApplicationContext context = buildContext('''
package failed.factory;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

class Product {
}

@Prototype
@Factory
class ProductFactory {
    @Prototype
    Product product() {
        throw new IllegalStateException("factory method failed");
    }

    @PreDestroy
    void destroy() {
        Events.LOG.add("ProductFactory");
    }
}
''')
        List<String> events = context.classLoader.loadClass('failed.factory.Events').LOG

        when:
        context.getBean(context.classLoader.loadClass('failed.factory.Product'))

        then:
        thrown(BeanCreationException)
        events == ['ProductFactory']

        cleanup:
        context.close()
    }

    void 'test a bean keeps its dependents when the creation of an optional bean it injects is disabled'() {
        given:
        ApplicationContext context = buildContext('''
package failed.optional;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.DisabledBeanException;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

class Disabled {
}

@Prototype
class Dependent {
    private static int count;
    final int id = ++count;

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent " + id);
    }
}

@Factory
class DisabledFactory {
    @Prototype
    Disabled disabled(Dependent dependent) {
        throw new DisabledBeanException("disabled");
    }
}

@Singleton
class Parent {
    final Dependent dependent;
    final Optional<Disabled> disabled;

    Parent(Dependent dependent, Optional<Disabled> disabled) {
        this.dependent = dependent;
        this.disabled = disabled;
    }
}
''')
        Class<?> parentType = context.classLoader.loadClass('failed.optional.Parent')
        List<String> events = context.classLoader.loadClass('failed.optional.Events').LOG

        when:
        def parent = context.getBean(parentType)

        then: 'the dependent of the disabled bean is destroyed'
        parent.dependent.id == 1
        !parent.disabled.present
        events == ['Dependent 2']

        when:
        context.destroyBean(parentType)

        then: 'the dependent created before the disabled bean is still owned by the parent'
        events == ['Dependent 2', 'Dependent 1']

        cleanup:
        context.close()
    }

    void 'test a bean keeps its dependents when the creation of a bean of a collection it injects is disabled'() {
        given:
        ApplicationContext context = buildContext('''
package failed.collection;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.exceptions.DisabledBeanException;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

class Element {
    final String name;

    Element(String name) {
        this.name = name;
    }
}

@Prototype
class Dependent {
    private static int count;
    final int id = ++count;

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent " + id);
    }
}

@Factory
class ElementFactory {
    @Prototype
    @Named("disabled")
    Element disabled(Dependent dependent) {
        throw new DisabledBeanException("disabled");
    }

    @Prototype
    @Named("enabled")
    Element enabled() {
        return new Element("enabled");
    }
}

@Singleton
class Parent {
    final Dependent dependent;
    final List<Element> elements;

    Parent(Dependent dependent, List<Element> elements) {
        this.dependent = dependent;
        this.elements = elements;
    }
}
''')
        Class<?> parentType = context.classLoader.loadClass('failed.collection.Parent')
        List<String> events = context.classLoader.loadClass('failed.collection.Events').LOG

        when:
        def parent = context.getBean(parentType)

        then: 'the dependent of the disabled bean is destroyed'
        parent.dependent.id == 1
        parent.elements*.name == ['enabled']
        events == ['Dependent 2']

        when:
        context.destroyBean(parentType)

        then: 'the dependent created before the disabled bean is still owned by the parent'
        events == ['Dependent 2', 'Dependent 1']

        cleanup:
        context.close()
    }

    void 'test the dependents of a bean whose creation through a provider fails are destroyed'() {
        given:
        ApplicationContext context = buildContext('''
package failed.provider;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Prototype
class Dependent {
    private static int count;
    final int id = ++count;

    @PreDestroy
    void destroy() {
        Events.LOG.add("Dependent " + id);
    }
}

@Prototype
class Failing {
    Failing(Dependent dependent) {
        throw new IllegalStateException("constructor failed");
    }
}

@Singleton
class Parent {
    final Dependent dependent;
    final List<String> failures = new ArrayList<>();

    Parent(Dependent dependent, Provider<Failing> provider, BeanProvider<Failing> beanProvider) {
        this.dependent = dependent;
        try {
            provider.get();
        } catch (RuntimeException e) {
            failures.add("Provider");
        }
        try {
            beanProvider.get();
        } catch (RuntimeException e) {
            failures.add("BeanProvider");
        }
    }
}
''')
        Class<?> parentType = context.classLoader.loadClass('failed.provider.Parent')
        List<String> events = context.classLoader.loadClass('failed.provider.Events').LOG

        when:
        def parent = context.getBean(parentType)

        then: 'the dependents of the failed beans are destroyed'
        parent.failures == ['Provider', 'BeanProvider']
        parent.dependent.id == 1
        events == ['Dependent 2', 'Dependent 3']

        when:
        context.destroyBean(parentType)

        then: 'the dependent created before them is still owned by the parent'
        events == ['Dependent 2', 'Dependent 3', 'Dependent 1']

        cleanup:
        context.close()
    }
}
