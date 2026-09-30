# Crema service loading tests

Tests of `io.micronaut.core.io.service` in a native image built with runtime class loading
(`-H:+RuntimeClassLoading`, Crema).

Crema is experimental, and what it supports changes between GraalVM releases. The native tests are
therefore opt-in. Without the `cremaTests` Gradle property this module has no `nativeTest` task, so the
GraalVM workflows, which run every `nativeTest` task of the build, do not run it.

| Command | What it runs |
|---|---|
| `./gradlew :test-suite-crema-graalvm:test` | The tests on the JVM, where there is no service table. Part of the regular build. |
| `GRAALVM_HOME=<GraalVM> ./gradlew :test-suite-crema-graalvm:nativeTest -PcremaTests=true` | The tests in a Crema image. |

Setting the `ORG_GRADLE_PROJECT_cremaTests=true` environment variable is the same as passing
`-PcremaTests=true`.

Last verified with Oracle GraalVM 25.0.3 on macOS arm64: 4 tests pass and
`classLoadedAtRunTimeRunsInImageCodeAndGetsTheStaticServiceTable` is skipped, because Crema in that
release defines classes at run time but does not run their static initializers. It also cannot define a
class that has an `InnerClasses` attribute, which is why the `cremaRuntime` sources use no lambdas and no
nested classes, and are compiled with `-XDstringConcat=inline`.
