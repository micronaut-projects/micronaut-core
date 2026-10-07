import asyncio
import threading

import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

ResourceResolver = java.type("io.micronaut.core.io.ResourceResolver")


# A singleton constructed with a Java bean that keeps request state behind a lock: one object, whatever the
# number of event loops and contexts.
@Requires(property="spec.name", value="PythonAsyncSingletonSpec")
@Singleton
class StatefulCounter:

    def __init__(self, resource_resolver: ResourceResolver):
        self.resource_resolver = resource_resolver
        self.lock = threading.Lock()
        self.count = 0

    def has_resolver(self) -> bool:
        return self.resource_resolver.getSupportingLoader("classpath:").isPresent()

    async def increment(self) -> int:
        await asyncio.sleep(0.001)
        with self.lock:
            self.count += 1
            return self.count

    def current(self) -> int:
        with self.lock:
            return self.count
