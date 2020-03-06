package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Specification

class EnvironmentPropertySourceSpec extends Specification {

    @Rule
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables()

    void "test disabling environment properties"() {
        environmentVariables.set("A_B_C_D", "abcd")
        ApplicationContext context = ApplicationContext.builder().start()

        expect:
        context.getRequiredProperty("a.b.c.d", String) == "abcd"

        when:
        context.stop()
        context = ApplicationContext.builder().environmentPropertySource(false).start()

        then:
        !context.getProperty("a.b.c.d", String).isPresent()

        cleanup:
        context.close()
    }

    void "test include and exclude environment properties"() {
        environmentVariables.set("A_B_C_D", "abcd")
        environmentVariables.set("A_B_C_E", "abce")
        environmentVariables.set("A_B_C_F", "abcf")
        environmentVariables.set("A_B_C_G", "abcg")
        environmentVariables.set("A_B_C_H", "abch")
        ApplicationContext context = ApplicationContext.builder().environmentVariableIncludes("A_B_C_G", "A_B_C_E").start()

        expect:
        !context.getProperty("a.b.c.d", String).isPresent()
        context.getProperty("a.b.c.e", String).isPresent()
        !context.getProperty("a.b.c.f", String).isPresent()
        context.getProperty("a.b.c.g", String).isPresent()
        !context.getProperty("a.b.c.h", String).isPresent()

        when:
        context.stop()
        context = ApplicationContext.builder()
                .environmentVariableIncludes("A_B_C_D", "A_B_C_F", "A_B_C_H")
                .environmentVariableExcludes("A_B_C_H").start()

        then:
        context.getProperty("a.b.c.d", String).isPresent()
        !context.getProperty("a.b.c.e", String).isPresent()
        context.getProperty("a.b.c.f", String).isPresent()
        !context.getProperty("a.b.c.g", String).isPresent()
        !context.getProperty("a.b.c.h", String).isPresent()

        when:
        context.stop()
        context = ApplicationContext.builder()
                .environmentVariableExcludes("A_B_C_G", "A_B_C_H").start()

        then:
        context.getProperty("a.b.c.d", String).isPresent()
        context.getProperty("a.b.c.e", String).isPresent()
        context.getProperty("a.b.c.f", String).isPresent()
        !context.getProperty("a.b.c.g", String).isPresent()
        !context.getProperty("a.b.c.h", String).isPresent()

        cleanup:
        context.close()
    }

    void "test a very large environment variable"() {
        environmentVariables.set("A_B_C_D_E_F_G_H_I_J_K_L_M_N", "alphabet")
        ApplicationContext context = ApplicationContext.builder().start()

        expect:
        context.getProperty("a.b.c.d.e.f.g.h.i.j.k.l.m.n", String).isPresent()

        cleanup:
        context.close()
    }

}
