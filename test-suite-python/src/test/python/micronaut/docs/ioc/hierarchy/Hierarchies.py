# tag::class[]
from java.util import ArrayList, HashMap
from micronaut.core.annotation import TypeHierarchyImport

@TypeHierarchyImport(classes=[ArrayList, HashMap])  # <2>
class Hierarchies:
    pass
# end::class[]
