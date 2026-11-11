package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared introspector sees the introspections of the reloadable tier: a module that lists them, as Micronaut
 * Data's schema generation lists the entities, finds the application's.
 */
class DevIntrospectionTest {

    @TempDir
    Path project;

    @Test
    void theSharedIntrospectorListsTheIntrospectionsOfTheReloadableTier() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevIntrospectionTest", "micronaut.server.port", "-1"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("Book.java"), """
            package app;
            @io.micronaut.core.annotation.Introspected
            public class Book {
                private String title;
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
            }
            """);
        // lists the introspections as it starts, on the application's thread, as a schema generator does
        Files.writeString(src.resolve("Catalogue.java"), """
            package app;
            @io.micronaut.context.annotation.Context
            public class Catalogue {
                public final java.util.List<String> listed = io.micronaut.core.beans.BeanIntrospector.SHARED
                    .findIntrospectedTypes(reference -> reference.getName().startsWith("app."))
                    .stream().map(Class::getName).toList();
            }
            """);
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext context = runtime.context().orElseThrow();
            Class<?> catalogue = context.getClassLoader().loadClass("app.Catalogue");
            assertEquals(List.of("app.Book"), catalogue.getField("listed").get(context.getBean(catalogue)));
        } finally {
            runtime.close();
        }
    }
}
