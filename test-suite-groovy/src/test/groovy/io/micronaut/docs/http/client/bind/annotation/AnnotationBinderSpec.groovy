package io.micronaut.docs.http.client.bind.annotation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification;

import java.util.LinkedHashMap;
import java.util.Map;

class AnnotationBinderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer)


    void "test binding to the request"() {
        when:
        MetadataClient client = server.getApplicationContext().getBean(MetadataClient.class);

        then:
        client.get([version: 3.6, deploymentId: 42L]) == "3.6"
    }
}
