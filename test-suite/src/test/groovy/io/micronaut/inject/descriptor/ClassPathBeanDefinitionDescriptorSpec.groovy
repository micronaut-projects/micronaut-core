package io.micronaut.inject.descriptor

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.ioc.validation.Person
import io.micronaut.docs.ioc.validation.pojo.PersonService
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.writer.BeanDefinitionDescriptors
import io.micronaut.session.InMemorySessionStore
import io.micronaut.session.SessionStore
import io.micronaut.validation.validator.Validator
import jakarta.validation.ConstraintViolationException
import spock.lang.Shared
import spock.lang.Specification

/**
 * The definitions of this build carry a descriptor in their entry; the ones of a module released before descriptors
 * existed have an empty entry. A class path has both, and a context has to use both as it always did.
 */
class ClassPathBeanDefinitionDescriptorSpec extends Specification {

    private static final String ENTRIES = "META-INF/micronaut/" + BeanDefinitionReference.name + "/"

    // of the modules of this build, which are jars on this class path
    private static final List<String> BUILT = [
        'io.micronaut.http.server.netty.$DefaultNettyEmbeddedServerFactory$BuildDefaultServer0$Definition',
        'io.micronaut.management.endpoint.health.$HealthEndpoint$Definition',
        'io.micronaut.http.client.jdk.$DefaultJdkHttpClientRegistry$Definition',
        'io.micronaut.function.web.$AnnotatedFunctionRouteBuilder$Definition'
    ]

    // of the test classes of this module, a directory
    private static final List<String> COMPILED = [
        'io.micronaut.docs.ioc.validation.pojo.$PersonService$Definition',
        'io.micronaut.docs.ioc.validation.pojo.$PersonService$Definition$Intercepted$Definition'
    ]

    // of micronaut-session and micronaut-validation, from Maven Central at the versions of the catalog, which were
    // built before micronaut-core 5.3; by artifact and not by version, so that only a release with descriptors fails
    private static final Map<String, String> RELEASED = [
        'io.micronaut.session.$InMemorySessionStore$Definition'                  : 'micronaut-session',
        'io.micronaut.validation.validator.$DefaultValidator$Definition'         : 'micronaut-validation',
        'io.micronaut.validation.$ValidatingInterceptor$Definition'              : 'micronaut-validation'
    ]

    @Shared
    private BeanDefinitionDescriptors.Comparison comparison = BeanDefinitionDescriptors.compareAll(getClass().classLoader)

    void "the descriptor of every definition on the class path agrees with the loaded reference"() {
        expect:
        comparison.differences.isEmpty()
        comparison.compared.size() > 700

        and: "the definitions of the modules of this build, in their jars, and of the test classes, in a directory"
        comparison.compared.containsAll(BUILT + COMPILED)
        BUILT.every { entry(it).protocol == 'jar' }
        COMPILED.every { entry(it).protocol == 'file' }
        (BUILT + COMPILED).every { !comparison.notLoaded.contains(it) }
    }

    void "a definition of a module released before descriptors has an empty entry"() {
        expect:
        RELEASED.each { definition, artifact ->
            URL entry = entry(definition)
            assert entry.toString() =~ "/${artifact}-[0-9][^/]*\\.jar!/"
            assert entry.bytes.length == 0 && comparison.withoutDescriptor.contains(definition),
                "$artifact now ships descriptors: pin a release built before micronaut-core 5.3 as the fixture"
            assert !comparison.notLoaded.contains(definition)
        }
    }

    // Nothing reads the content of an entry yet, so this cannot fail because of it: it is what a context that reads
    // the descriptors has to keep doing for a class path that mixes the two
    void "a context uses the definitions with a descriptor and the ones without one together"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        Set<String> references = context.beanDefinitionReferences*.beanDefinitionName as Set

        expect: "the bean of a released module"
        context.getBean(SessionStore) instanceof InMemorySessionStore
        InMemorySessionStore.protectionDomain.codeSource.location.path ==~ /.*\/micronaut-session-[0-9][^\/]*\.jar/
        context.getBean(Validator) != null

        and: "the interceptor of a released module around a bean of this build"
        when:
        context.getBean(PersonService).sayHello(new Person(name: "", age: 10))

        then:
        ConstraintViolationException e = thrown()
        e.constraintViolations.size() == 2

        and: "the references of both, the one of the proxy of PersonService in place of the one of its target"
        references.containsAll(RELEASED.keySet())
        references.containsAll(BUILT + COMPILED.last())

        cleanup:
        context?.close()
    }

    private URL entry(String definition) {
        URL entry = getClass().classLoader.getResource(ENTRIES + definition)
        assert entry != null
        return entry
    }
}
