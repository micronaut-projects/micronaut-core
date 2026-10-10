from typing import Annotated

import java
from jakarta.inject import Inject
from docs.devmode import RuntimeDefinitions
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.context.reload import ClassChange, ClassChangeEvent, ReloadStrategy
from micronaut.context.watch import ConfigurationChange
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.web.router import RouteBuilder
from org.junit.jupiter.api import Test

from .CodecRegistry import CodecRegistry
from .RouteTable import RouteTable
from .SerializerRegistry import SerializerRegistry
from .TypeDescriptionCache import TypeDescriptionCache
from .Writer import Writer

ConnectionPool = java.type("micronaut.docs.devmode.watch.ConnectionPool")
Codec = java.type("micronaut.docs.devmode.watch.Codec")
StringSerializer = java.type("micronaut.docs.devmode.watch.StringSerializer")
JavaString = java.type("java.lang.String")
JavaSet = java.type("java.util.Set")
JavaList = java.type("java.util.List")


def retiring_this_loader():
    # every class of the test's loader is a class of the retired generation
    loader = StringSerializer.class_.getClassLoader()
    return ClassChangeEvent(StringSerializer.class_, JavaSet.of(loader), loader,
                            JavaList.of(ClassChange(StringSerializer.class_.getName(), ClassChange.Kind.MODIFIED)),
                            ReloadStrategy.RESTART)


def outcomes(context, key):
    return [outcome.name() for outcome in context.notifyConfigurationChange(ConfigurationChange.ofKeys(JavaSet.of(key)))]


@Property(name="spec.name", value="BeanWatchSnippetsSpec")
@Property(name="pools.default.url", value="jdbc:h2:mem:one")
@Property(name="pools.default.username", value="sa")
@Property(name="pools.default.password", value="one")
@Property(name="micronaut.dev.enabled", value="true")
@MicronautTest
class BeanWatchSnippetsSpec:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_route_table_follows_the_route_builders_registered(self) -> None:
        table = self.context.getBean(RouteTable).asPolyglotValue()
        before = table.size()

        self.context.registerBeanDefinition(RuntimeDefinitions.neverCreated(RouteBuilder, "extra"))

        assert table.size() == before + 1

    @Test
    def test_codec_handlers_see_each_addition(self) -> None:
        registry = self.context.getBean(CodecRegistry).asPolyglotValue()
        before = registry.size()
        assert before >= 1

        self.context.registerBeanDefinition(RuntimeDefinitions.neverCreated(Codec, "text"))

        assert registry.size() == before + 1

    @Test
    def test_pool_applies_new_credentials_and_is_recreated_for_a_new_url(self) -> None:
        pool = self.context.getBean(ConnectionPool, Qualifiers.byName("default"))

        assert outcomes(self.context, "pools.default.password") == ["APPLIED"]
        assert pool.asPolyglotValue().evictions == 1
        assert self.context.getBean(ConnectionPool, Qualifiers.byName("default")) == pool

        assert outcomes(self.context, "pools.default.maximum-pool-size") == ["IGNORED"]

        assert outcomes(self.context, "pools.default.url") == ["RECREATE"]
        assert self.context.getBean(ConnectionPool, Qualifiers.byName("default")) != pool

    @Test
    def test_serializer_registry_builds_its_state_from_the_first_batch(self) -> None:
        registry = self.context.getBean(SerializerRegistry).asPolyglotValue()
        assert registry.names() == ["micronaut.docs.devmode.watch.StringSerializer"]

    @Test
    def test_cache_forgets_the_classes_a_reload_retires(self) -> None:
        cache = self.context.getBean(TypeDescriptionCache).asPolyglotValue()
        cache.describe(StringSerializer.class_)
        cache.describe(JavaString.class_)

        self.context.publishEvent(retiring_this_loader())

        # String comes from the JDK, not from the retired generation
        assert cache.size() == 1

    @Test
    def test_lookup_and_its_dependents_are_recreated_when_a_serializer_class_is_retired(self) -> None:
        writer = self.context.getBean(Writer)
        lookup = writer.asPolyglotValue().lookup
        assert writer.asPolyglotValue().write("a") == '"a"'

        self.context.publishEvent(retiring_this_loader())

        recreated = self.context.getBean(Writer)
        assert recreated != writer
        assert recreated.asPolyglotValue().lookup != lookup
        assert recreated.asPolyglotValue().write("b") == '"b"'
