package io.micronaut.core.bind

import io.micronaut.http.HttpRequest
import org.junit.jupiter.api.BeforeEach
import spock.lang.Specification

class TestValueAnnotationBinderSpec extends Specification {

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
    }
}
