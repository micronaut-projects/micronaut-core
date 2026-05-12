# tag::class[]
from dataclasses import dataclass

import java
from micronaut.context.env import PropertySource, PropertySourceImporter
from micronaut.core.util import ConnectionString


@dataclass
class DemoImport:
    path: str


class DemoPropertySourceImporter(PropertySourceImporter):
    def getProvider(self) -> str:
        return "demo"

    def newImportDeclaration(self, source) -> DemoImport:
        if isinstance(source, ConnectionString):
            return DemoImport(source.getPath())
        return DemoImport(source.get("path", java.type("java.lang.String")).orElse("defaults"))

    def importPropertySource(self, context):
        Optional = java.type("java.util.Optional")
        if context.importDeclaration().path != "defaults":
            return Optional.empty()
        return Optional.of(PropertySource.of(
            "demo:defaults",
            {"demo.message": "hello-from-demo-importer"},
        ))
# end::class[]
