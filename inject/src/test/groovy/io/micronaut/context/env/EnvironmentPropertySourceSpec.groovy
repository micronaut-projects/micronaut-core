package io.micronaut.context.env

import com.github.stefanbirkner.systemlambda.SystemLambda
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EnvironmentPropertySourceSpec extends Specification {

    void "test disabling environment properties"() {
        def envs = SystemLambda.withEnvironmentVariable("A_B_C_D", "abcd")
                .set()
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
        envs.restore()
    }

    void "test include and exclude environment properties"() {
        def envs = SystemLambda.withEnvironmentVariable("A_B_C_D", "abcd")
                .and("A_B_C_E", "abce")
                .and("A_B_C_F", "abcf")
                .and("A_B_C_G", "abcg")
                .and("A_B_C_H", "abch")
                .set()
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
        envs.restore()
    }

    void "test a very large environment variable"() {
        def envs = SystemLambda.withEnvironmentVariable("A_B_C_D_E_F_G_H_I_J_K_L_M_N", "alphabet").set()
        ApplicationContext context = ApplicationContext.builder().start()

        expect:
        context.getProperty("a.b.c.d.e.f.g.h.i.j.k.l.m.n", String).isPresent()

        cleanup:
        context.close()
        envs.restore()
    }

}
