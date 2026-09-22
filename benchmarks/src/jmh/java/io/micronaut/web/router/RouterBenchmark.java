package io.micronaut.web.router;

import io.micronaut.annotation.processing.test.JavaParser;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds a router for generated controllers, and matches requests with it. Every controller has
 * ten routes (fourteen with the implicit {@code HEAD} routes).
 *
 * <p>{@code RUNTIME} derives the routes from the bean definitions, as without
 * {@code @PrecompiledHttpRoutes}. {@code PRECOMPILED} uses the routes generated at compile time.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RouterBenchmark {

    @Param({"1", "10", "100"})
    int controllers;

    @Param({"RUNTIME", "PRECOMPILED"})
    String mode;

    private ApplicationContext context;
    private Collection<BeanDefinition<Object>> definitions;
    private List<PrecompiledHttpRoutesDefinition> precompiled;
    private DefaultRouter router;
    private HttpRequest<?>[] requests;
    private int next;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        List<JavaFileObject> sources = new ArrayList<>();
        for (int i = 0; i < controllers; i++) {
            sources.add(source("test.Controller" + i, controller(i)));
        }
        sources.add(source("test.Application", """
            package test;

            @io.micronaut.web.router.annotation.PrecompiledHttpRoutes
            class Application {
            }
            """));
        List<JavaFileObject> files = new ArrayList<>();
        try (JavaParser parser = new JavaParser()) {
            // the output is cleared when the parser is closed
            parser.generate(sources.toArray(JavaFileObject[]::new)).forEach(files::add);
        }
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String fileName = name.replace('.', '/') + ".class";
                for (JavaFileObject file : files) {
                    if (file.getName().endsWith(fileName)) {
                        try (InputStream in = file.openInputStream()) {
                            byte[] bytes = in.readAllBytes();
                            return defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                }
                return super.findClass(name);
            }
        };
        List<BeanDefinitionReference<?>> references = new ArrayList<>();
        for (JavaFileObject file : files) {
            String name = file.toUri().toString();
            if (file.getKind() == JavaFileObject.Kind.CLASS && (name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + ".class") || name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + "$Reference.class"))) {
                String className = name.substring("mem:///CLASS_OUTPUT/".length(), name.length() - ".class".length()).replace('/', '.');
                references.add((BeanDefinitionReference<?>) classLoader.loadClass(className).getDeclaredConstructor().newInstance());
            }
        }
        ApplicationContextBuilder builder = ApplicationContext.builder().classLoader(classLoader);
        builder.beanDefinitionsProvider(cl -> {
            List<BeanDefinitionReference<?>> all = new ArrayList<>(references);
            all.addAll(new DefaultBeanDefinitionsProvider().provide(cl));
            return all;
        });
        context = builder.build().start();
        definitions = context.getBeanDefinitions(Qualifiers.byStereotype(Controller.class)).stream()
            .filter(definition -> definition.getBeanType().getPackageName().equals("test"))
            .toList();
        precompiled = mode.equals("PRECOMPILED")
            ? List.of((PrecompiledHttpRoutesDefinition) classLoader.loadClass("test.$Application$PrecompiledHttpRoutes").getDeclaredConstructor().newInstance())
            : List.of();
        router = buildRouter();

        requests = new HttpRequest<?>[64];
        for (int i = 0; i < requests.length; i++) {
            int c = i % controllers;
            requests[i] = switch (i % 4) {
                case 0 -> HttpRequest.GET("/c" + c + "/items/" + i);
                case 1 -> HttpRequest.GET("/c" + c + "/items/" + i + "/details");
                case 2 -> HttpRequest.GET("/c" + c + "/files/a/b");
                default -> HttpRequest.DELETE("/c" + c + "/items/" + i);
            };
        }
        // materialize the routes that are matched
        for (HttpRequest<?> request : requests) {
            if (router.findClosest(request) == null) {
                throw new IllegalStateException("No route for " + request);
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        context.close();
    }

    @Benchmark
    public DefaultRouter buildRouter() {
        AnnotatedMethodRouteBuilder builder = new AnnotatedMethodRouteBuilder(context, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, precompiled);
        for (BeanDefinition<Object> definition : definitions) {
            builder.process(definition, context);
        }
        return new DefaultRouter(builder);
    }

    @Benchmark
    public void findClosest(Blackhole blackhole) {
        HttpRequest<?> request = requests[next++ & 63];
        blackhole.consume(router.findClosest(request));
    }

    private static JavaFileObject source(String className, String code) {
        return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    private static String controller(int i) {
        return """
            package test;

            import io.micronaut.http.annotation.*;

            @Controller("/c%d")
            class Controller%d {
                @Get("/items")
                String list() { return ""; }

                @Get("/items/{id}")
                String show(Long id) { return ""; }

                @Post("/items")
                String save(@Body String body) { return ""; }

                @Put("/items/{id}")
                String update(Long id, @Body String body) { return ""; }

                @Patch("/items/{id}")
                String patch(Long id, @Body String body) { return ""; }

                @Delete("/items/{id}")
                void delete(Long id) { }

                @Get("/items/{id}/details")
                String details(Long id) { return ""; }

                @Get("/search{?q}")
                String search(@jakarta.annotation.Nullable String q) { return ""; }

                @Get("/files/{+path}")
                String files(String path) { return ""; }

                @Get("/status")
                String status() { return ""; }
            }
            """.formatted(i, i);
    }
}
