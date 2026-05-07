# tag::test[]
from micronaut.context import ApplicationContext
from micronaut.context.env import PropertySource
from micronaut.core.util import ConnectionString
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
import java

from .DemoPropertySourceImporter import DemoPropertySourceImporter


@MicronautTest
class DemoPropertySourceImporterTest:
    @Test
    def imports_demo_defaults(self) -> None:
        context = ApplicationContext.run()
        try:
            importer = DemoPropertySourceImporter()
            declaration = importer.newImportDeclaration(ConnectionString.parse("demo://defaults"))

            class DemoImportContext:
                def environment(self):
                    return context.getEnvironment()

                def connectionString(self):
                    return ConnectionString.parse("demo://defaults")

                def importDeclaration(self):
                    return declaration

                def parentOrigin(self):
                    return PropertySource.Origin.of("classpath:application.yml")

                def importPropertySource(self, *args):
                    return java.type("java.util.Optional").empty()

                def importClasspathPropertySource(self, *args):
                    return java.type("java.util.Optional").empty()

            property_source = importer.importPropertySource(DemoImportContext())

            assert property_source.isPresent()
            assert property_source.get().get("demo.message") == "hello-from-demo-importer"
        finally:
            context.close()
# end::test[]
