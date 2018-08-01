package io.micronaut.function.executor

import io.micronaut.function.LocalFunctionRegistry
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class FunctionApplicationSpec extends Specification {


    void "run supplier function"() {
        given:
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "supplier")
        System.out = new PrintStream(out)

        when:
        FunctionApplication.main()

        then:
        new String(out.toByteArray(), StandardCharsets.UTF_8).endsWith 'myvalue'

        cleanup:
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "")
        System.out = oldOut
    }

    void "run regular function"() {
        given:
        def oldOut = System.out
        def oldIn = System.in

        def out = new ByteArrayOutputStream()
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "round")
        System.out = new PrintStream(out)
        System.in = new ByteArrayInputStream("100.4".bytes)

        when:
        FunctionApplication.main()

        then:
        new String(out.toByteArray(), StandardCharsets.UTF_8).endsWith '100'

        cleanup:
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "")
        System.out = oldOut
        System.in = oldIn
    }

    void "run POJO function"() {
        given:
        def oldOut = System.out
        def oldIn = System.in

        def out = new ByteArrayOutputStream()
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, "upper")
        System.out = new PrintStream(out)
        System.in = new ByteArrayInputStream('{"name":"fred"}'.bytes)

        when:
        FunctionApplication.main()

        then:
        new String(out.toByteArray(), StandardCharsets.UTF_8).endsWith '{"name":"FRED"}'

        cleanup:
        System.setProperty(LocalFunctionRegistry.FUNCTION_NAME, '')
        System.out = oldOut
        System.in = oldIn
    }
}
