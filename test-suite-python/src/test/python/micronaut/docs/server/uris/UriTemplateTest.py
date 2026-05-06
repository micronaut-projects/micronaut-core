import java
from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest

UriMatchTemplate = java.type("io.micronaut.http.uri.UriMatchTemplate")
Collections = java.type("java.util.Collections")


@MicronautTest
class UriTemplateTest:
    @Test
    def test_uri_template(self):
        # tag::match[]
        template = UriMatchTemplate.of("/hello/{name}")

        assert template.match("/hello/John").isPresent()  # <1>
        assert template.expand(  # <2>
            Collections.singletonMap("name", "John")
        ) == "/hello/John"
        # end::match[]
