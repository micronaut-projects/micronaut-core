# test-suite-jdt

A test suite that compiles with the **Eclipse JDT compiler (ECJ)** instead of javac, so that the
Micronaut annotation processors are exercised against JDT's `javax.lang.model` implementation.

Both compilers are standards compliant, but the specification deliberately leaves things open —
most notably the order of `TypeElement.getEnclosedElements()` and the shape of compiler synthesised
members — and processors that assume javac's behaviour produce wrong metadata under JDT. See
[micronaut-core#12658](https://github.com/micronaut-projects/micronaut-core/issues/12658) for an
example of a bug that only reproduced when building from Eclipse.

## Layout

| Path | What it does |
| --- | --- |
| `src/main/java` | `JdtParser`, a [`JavaParser`](../inject-java-test/src/main/java/io/micronaut/annotation/processing/test/JavaParser.java) that compiles in memory with ECJ, plus a class loader over the result |
| `src/main/groovy` | `AbstractJdtTypeElementSpec` and `AbstractCompilerParitySpec`, the two base classes the tests use |
| `src/test/groovy` | The specs: differential (javac vs JDT) comparisons and JDT-only runtime tests |
| `src/jdt/java` | Beans compiled by the ECJ **batch** compiler through the `compileJdt` Gradle task |
| `src/test/java` | JUnit tests that run the beans in `src/jdt/java` inside a real `ApplicationContext` |

## The two kinds of test

**Differential** (`AbstractCompilerParitySpec`) compiles the same sources twice, once with javac and
once with ECJ, then renders every generated bean definition, introspection and metadata resource as
a stable string and asserts the two are identical. Because javac is the reference implementation
that Micronaut is developed against, any difference is a Micronaut bug rather than a compiler bug.
Adding coverage is just adding a source snippet.

Two things are normalised before comparing, because they are not properties of the generated
metadata: the order in which entries contributed by *different* classes appear in a metadata JSON
file (that follows the compiler's visit order), and the index in the name of a generated evaluated
expression class (that comes from a counter that is static for the JVM).

**Runtime** (`AbstractJdtTypeElementSpec` and `JdtCompiledBeansTest`) compiles only with JDT and then
starts an `ApplicationContext` over the result, so that the metadata is proven to work and not only
to match.

## Running

```bash
./gradlew :test-suite-jdt:test
```
