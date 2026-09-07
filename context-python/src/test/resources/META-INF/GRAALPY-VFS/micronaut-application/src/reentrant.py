# A module whose body re-enters the pool for itself while it is loading: the pool's script cache
# must not run this load inside a map remapping function, which would fail as a recursive update.
import java

_runtime = java.type("io.micronaut.context.python.PythonContextRuntime")
again = _runtime.findPooledScript("python", "reentrant")
value = 1
