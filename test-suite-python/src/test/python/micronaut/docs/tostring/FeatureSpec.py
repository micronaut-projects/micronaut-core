from java.lang import String
from java.util import HashMap
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Feature import Feature


@MicronautTest
class FeatureSpec:

    @Test
    def javaFormatsThePythonObjectWithStr(self):
        assert String.valueOf(Feature("Tree")) == "Tree"

    @Test
    def aJavaMapKeyIsFormattedWithStr(self):
        features = HashMap()
        features.put(Feature("Tree"), 1)
        assert features.toString() == "{Tree=1}"
