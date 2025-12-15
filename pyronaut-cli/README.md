# Pyronaut CLI

This document discusses implementation options for a `pyronaut` command line interpreter. The goal of that interpreter is to perform the heavy lifting that Micronaut needs in order to integrate with Python. As such, it is designed as _Python first_. In other words, users write Python code and the `pyronaut` command line is responsible for "compiling" the application, running it, etc.

## The compiler

The Micronaut Python (aka _Pyronaut_) compiler is based on the classic Java compilation process: it relies on the Java compiler with annotation processing. Obviously, the Java compiler cannot "process" Python files, so the initial trick is to generate a dummy Java class, which will be annotated by a Micronaut application (`@PythonApplication`) which will contain as an annotation value the path to the Python sources that must be processed. An annotation processor is then responsible for scanning that directory for `.py` sources and generate Java sources or classes for these Python sources.

- the code which can create a GraalPy context to evaluate Python scripts and load a Python application lives in `context-python`
- the code which is responsible for parsing the Python scripts and generating classes during annotation processing lives in the `inject-python` module, which itself depends on `inject-java`. It doesn't depend on `context-python` because it creates its own GraalPy context during compilation.

There were several spikes implemented which allowed us to explore different options we have to implement the CLI. Here's a summary:

### Spike 1: "All in One" compiler

The Pyronaut CLI has direct dependencies on `pyronaut-inject` and `pyronaut-context`, but also has direct dependencies on `micronaut-server-http-netty` and other common Micronaut modules. This worked but has the major drawback that the dependencies are fixed, so it's impossible to add new modules, or even remove unwanted ones. On the other hand, this simplified the architecture, since we don't need to care about the processor path vs classpath anymore: the compiler is loaded in the same classloader.

### Spike 2: "standalone" compiler

The idea here was to create a module which basically invokes the Java compiler. It has no dependency on micronaut-context-python, nor anything python related. It's basically an alternative to `javac`, which was to experiment with the idea of compiling the Python compiler as a native image. This let us discover a few issues with native compilation of javac:

   * it still requires a real Java JDK
   * it works relatively well WITHOUT processors
   * it will NOT work without processors, because these need to be loaded dynamically, which means that they use dynamic classloading, which is not supported
   * early experiments with Crema, which adds dynamic classloading support, failed with ClassNotFoundException (s), hinting at the fact that the implementation is not mature enough to deal with our use case

This approach has serious limitations. In particular, it still requires a different `pyronaut` CLI that would invoke that command line compiler, which implies 2 separate process. This would also require a way to tell what dependencies need to be on compile classpath and what needs to be on annotation processor path, which is basically what a build tool is normally doing. In other words, there's almost zero interest in this approach, since it's too limited: as soon as we introduce "top level" dependencies like `context-python` to the CLI, we end up in the situation where we have not enough information to compile a real application.

### Spike 3: "semi-isolated" compiler

The current major spike. It consists of a `pyronaut` command-line with sub-commands:

* `run`: compiles the application, runs it and watches it, restarting on source changes.
* `install`: installs Java dependencies required by the application.
* `native`: compiles the application as a native binary.

The spike is described in more details in the following section.

## Pyronaut CLI architecture

This CLI makes the following design choice:

- the CLI is shipped as a Java application, which runs with a GraalVM JDK (25).
- the CLI is _ran_ as a Java application, which invokes the Java compiler API programmatically
- it makes use of Gradle's Tooling API to provide dependency resolution and native image integration
- running the application using the CLI is done in JVM mode
- native images are a deployment optimization (`pyronaut native` builds a native application)
- Python dependencies (`numpy`, ...) have to be installed using `pip`
- it makes use of `pyproject.toml` to capture Pyronaut specific configuration, in particular the dependencies

The **Pyronaut CLI** uses a **semi‑isolated compiler** driven by the JDK `JavaCompiler` (`javax.tools.JavaCompiler`) together with Micronaut annotation processors. The workflow is organized into the following sections.

### pyproject.toml

The `pyproject.toml` file is a standard modern configuration file for Python projects.
It can be enhanced with tool specific information.
The `[project]` section is mandatory, and we use the `[tool.pyronaut]` section to enrich it with our specific needs.

```toml
[project]
name="pyronaut-demo"

[tool.pyronaut]
version="5.0.0-SNAPSHOT"
repositories = [
    "mavenCentral",
    "mavenLocal",
    "https://repo.gradle.org/gradle/libs-releases"
]

[tool.pyronaut.dependencies]
compile = [ "io.micronaut:micronaut-inject-python",
    "io.micronaut:micronaut-context-python",
    "io.micronaut:micronaut-http-server-netty", "io.micronaut:micronaut-json-core",
    "io.micronaut:micronaut-jackson-databind",
    "ch.qos.logback:logback-classic",
    "org.bouncycastle:bcprov-jdk18on",
    "org.apache.commons:commons-lang3:3.20.0",
]

annotationProcessor = [
    "io.micronaut:micronaut-inject-python",
    "io.micronaut:micronaut-context-python",
]
```

### Compiler invocation

`PyronautCliCompiler` creates a temporary Java source containing only the `@PythonApplication` annotation that points to the Python source directory. It is executed via `JavaCompiler.getTask(...)` with a custom classpath (the compile classpath) and a processor path. The compiler runs with a parent classloader that contains the GraalPy runtime (the “Truffle” parent).

### Annotation processing

The Micronaut `inject‑python` processor scans the annotated source directory, creates a GraalPy context inside the processor, parses each `.py` file and generates Java stubs that delegate to the GraalPy runtime.

### Class‑loading hierarchy

- **Truffle (GraalPy) parent classloader** – built by `PyronautFileWatcher.createTruffleClassLoader`. This is a very important trick. Remember that we compile the CLI against Micronaut, but we cannot ship these at execution time, otherwise the classes would leak into the classes seen by the compiler when we execute it. However, we still need the Micronaut and Truffle classes at compilation time and runtime. Therefore, we need to create a classloader which contains both. That solution doesn't work, because Truffle mandates that the GraalPy engine is only loaded once. Therefore, we have to do a trick: determine which jars belong to the Truffle engine, and put them into a parent classloader, which can then be used as the parent classloader for the compiler (which will need it during Python files parsing) and the runtime of the application (where we will have a child classloader which will be discarded on each application reload).
- **Application classloader** – a child `URLClassLoader` created by `PyronautFileWatcher.buildUrls`. It contains the compiled classes, the `config` folder and all non‑Truffle dependencies. Its parent is the Truffle classloader, giving the application access to GraalPy while keeping GraalPy isolated from the application classes.

### Application launch

The CLI cannot place Micronaut core classes (e.g. `ApplicationContext`, `EmbeddedApplication`, `EmbeddedServer`) on its own classpath at startup because doing so would load them into the root classloader. That would prevent the later compilation of user code, as those classes would then be fixed in the root loader and could not see additional Micronaut dependencies (such as `micronaut-http-server-netty`) that the application may require. This proves to be problematic because we want to be able to call the APIs to start a Micronaut application.

To avoid this, the CLI uses the **ApplicationManager trick**: `ApplicationManagerInvoker` loads `io.micronaut.python.cli.DefaultApplicationManager` reflectively via `MethodHandles` from the **application child classloader** (the one that contains the compiled user classes and all runtime dependencies). This indirection keeps Micronaut classes isolated from the CLI’s root classloader.

`DefaultApplicationManager.startApplication(String[])` then builds a Micronaut `ApplicationContext` using that child classloader and starts the embedded server (`EmbeddedApplication`/`EmbeddedServer`). When the application needs to stop, `DefaultApplicationManager.stopApplication()` shuts down the context, allowing the child classloader to be discarded and recreated on the next recompilation without contaminating the root classloader.

### File‑watcher integration

`PyronautRunCommand` starts a `PyronautFileWatcher` in a separate thread. The watcher registers a `WatchService` on the source directory; on any change it stops the running application, recompiles the sources, rebuilds the application classloader and restarts the application. This provides fast “run‑watch” cycles without restarting the JVM.

### Dependency resolution (`install` command)

`PyronautInstallCommand` reads `pyproject.toml`, creates a temporary Gradle project using `template.build.gradle` and resolves the declared Maven coordinates with the Gradle Tooling API (`GradleConnector`). Resolved JARs are cached and added to both the compiler and application classpaths via `PythonMavenRepository`. Currently, the term “install” is abused. It refers to the semantics of `pip install`, which basically downloads libraries and copies them into an isolated directory (if running in a virtual environment). “install” will therefore copy Maven dependencies (Micronaut Core, Logback, …) into the virtual environment in a pyronaut specific cache directory. A few things worth mentioning:

- we isolate dependency graphs: there are different directories, with different copies of the libraries, for each dependency graph (e.g. compile vs annotation processor)
- we simplified the problem to just 2 dependency graphs: the annotation processing one and the compile one, which also happens to be used at runtime (there’s no separation between compile and runtime dependencies)
- each time a dependency is added, we perform a new dependency resolution and delete from the cache entries which shouldn’t be there anymore. Therefore, we know that the cache contains exactly the runtime dependencies and nothing more. This is important for native compilation.

### Native image creation (`native` command)

This command invokes `native-image` in order to generate a native binary of a pyronaut application. To do so, it invokes Gradle’s tooling API using a template similar to the one used for dependency resolution, except that this time we’re adding the Native Build Tools plugin. Therefore, all the heavy lifting work of configuring the build and invoking native image is delegated to Gradle. This greatly simplifies things since we don’t have to handle complicated arguments like `--exclude-config`. This, on the other hand, requires us to structure the dependency cache in such a way that we can “rebuild” the list of dependencies.

### Native image of the CLI

The question is whether we can have a native image version of the CLI.
Currently it’s not possible, because of a few problems:

1. the compiler requires annotation processors, and these annotation processors are loaded dynamically, therefore we need support for dynamic classloading (will require Crema)
2. the application entry point is also loaded dynamically (currently using `MethodHandle` but it could also use reflection)
3. the `install` and `native` commands both make use of Gradle’s Tooling API, which is not compatible with native image

Gradle’s Tooling API is capable of downloading a Gradle distribution (works in native image), but once the distribution is loaded, it will try to load classes from that distribution (the TAPI consists of 2 parts: the client and the server, and the server classes are version specific). So even if we add metadata to be able to compile a native image which uses a particular TAPI version, we are still unable to dynamically load these classes (requires Crema).

It’s worth noting that even with Crema, the execution may not be optimal, because the dynamically loaded classes would not be compiled to native code: they would run in interpreted mode.

### Project setup

A Pyronaut project should be created in a virtual environment.
Currently, it requires **both the GraalPy distribution (Python first) and the GraalVM distribution (Java first)**.

Therefore, you need at least: `pyenv install graalpy-25.0.1` and `sdk install java 25-graal`.

1. Select `graalpy` as your python interpreter

```bash
pyenv shell graalpy-25.0.1
```

2. Create project directory

```bash
mkdir pyronaut-demo && cd pyronaut-demo
```

3. Create a virtual environment

```bash
python -m venv venv
```

4. Activate the virtual environment

```bash
source venv/bin/activate
```

5. Create the application directory

```bash
mkdir app && cd app
```

6. Create a `pyproject.toml`

```bash
cat > pyproject.toml << EOL
[project]
name="pyronaut-demo"

[tool.pyronaut]
version="5.0.0-SNAPSHOT"
repositories = [
    "mavenCentral",
    "mavenLocal",
    "https://repo.gradle.org/gradle/libs-releases"
]

[tool.pyronaut.dependencies]
compile = [ "io.micronaut:micronaut-inject-python",
    "io.micronaut:micronaut-context-python",
    "io.micronaut:micronaut-http-server-netty", "io.micronaut:micronaut-json-core",
    "io.micronaut:micronaut-jackson-databind",
    "ch.qos.logback:logback-classic",
    "org.bouncycastle:bcprov-jdk18on",
    "org.apache.commons:commons-lang3:3.20.0",
]

annotationProcessor = [
    "io.micronaut:micronaut-inject-python",
    "io.micronaut:micronaut-context-python",
]
EOL
```

7. create the sources directories

```
mkdir src
mkdir config
```

8. create a sample controller and a configuration file

```bash
cat > src/controller.py << EOL
from micronaut.http.annotation import Controller, Get

@Controller
class MyController:
    @Get(value="/", produces="text/plain")
    def index(self) -> str:
        return "Hello, world 2!"

    @Get(value="/hello")
    def hello(self) -> dict:
        return { "Hello": "Pyronaut!" }
EOL
cat > config/logback.xml << EOL
<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <!-- encoders are assigned the type
             ch.qos.logback.classic.encoder.PatternLayoutEncoder by default -->
        <encoder>
            <pattern>%msg%n</pattern>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
EOL
at << 'EOL' > config/micronaut-banner.txt
(                                             
 )\ )                                       )  
(()/( (     (                   )    (   ( /(  
 /(_)))\ )  )(    (    (     ( /(   ))\  )\()) 
(_)) (()/( (()\   )\   )\ )  )(_)) /((_)(_))/  
| _ \ )(_)) ((_) ((_) _(_/( ((_)_ (_))( | |_   
|  _/| || || '_|/ _ \| ' \))/ _` || || ||  _|  
|_|   \_, ||_|  \___/|_||_| \__,_| \_,_| \__|  
      |__/                                      
EOL
```

### Running the application

You can now start the application using `pyronaut run`.

### Add a native Python library

Native Python libraries **MUST** be installed using `pip`.
It doesn't matter whether you are using `pyronaut run` or `pyronaut native`, in both cases, the native libraries **MUST** be found in the virtual environment.
