package io.micronaut.core.bind

<<<<<<< HEAD
import io.micronaut.http.HttpRequest
import org.junit.jupiter.api.BeforeEach
=======
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Executable
>>>>>>> 3c2dd527111cc27b75929d7ae092ba7f19bee707
import spock.lang.Specification

class TestValueAnnotationBinderSpec extends Specification {

<<<<<<< HEAD
    TestValueJavaAnnotationBinder binder = new TestValueJavaAnnotationBinder();

    @BeforeEach
    public void setup() throws Exception {
        bindingResult = new TestValueJavaAnnotationBinder(new Object(), "attr").getBindingResult();
        webRequest = mock(HttpRequest.class);
    }

    public void bindingResult() throws Exception {
        ATest aTest = new ATest();
        aTest.name = "new Name"
        aTest.value = "new Value"

        Object actual = resolver.resolveArgument(paramErrors, mavContainer, webRequest, null);
        assertThat(bindingResult).isSameAs(actual);
=======

    void "test valid TestValueAnnotation binder"() {
        given:
        Executable testValue = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo")] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }
        }

        TestValueAnnotationBinder binder = new TestValueAnnotationBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return { Optional.of(args[1].get(args[0].argument.name)) } as ArgumentBinder.BindingResult
        } )

        registry.findArgumentBinder(_,_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(testValue, registry, [foo:"bar"])

        then:
        bound != null
        bound.invoke(this) == 'bar'
>>>>>>> 3c2dd527111cc27b75929d7ae092ba7f19bee707
    }
}
