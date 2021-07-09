package io.micronaut.context.env

import com.github.stefanbirkner.systemlambda.SystemLambda
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesEnvironmentPropertySourceSpec extends Specification {

    @Unroll
    void "it filters out variables like V1_SERVICE_XPTO#suffix"(String suffix) {
        given:
        Map<String, String> env = SystemLambda
                .withEnvironmentVariable("V1_SERVICE_XPTO${suffix}", "172.20.232.70")
                .execute { KubernetesEnvironmentPropertySource.getEnvNoK8s() }

        expect:
        !env.get("V1_SERVICE_XPTO${suffix}")

        where:
        suffix << KubernetesEnvironmentPropertySource.VAR_SUFFIXES
    }

    void "it has the same position and convention than the EnvironmentPropertySource"() {
        given:
        KubernetesEnvironmentPropertySource keps = new KubernetesEnvironmentPropertySource()
        EnvironmentPropertySource eps = new EnvironmentPropertySource()

        expect:
        keps.order == eps.order
        keps.convention == eps.convention
    }

}
