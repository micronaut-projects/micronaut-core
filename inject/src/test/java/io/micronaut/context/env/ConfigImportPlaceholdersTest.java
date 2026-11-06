package io.micronaut.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.core.value.PropertyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigImportPlaceholdersTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesPlaceholdersAcrossScalarAndStructuredDeclarations() {
        RecordingImporter importer = new RecordingImporter();
        List<PropertySource> sources = List.of(
            source("scalar", Map.of(
                "micronaut.config.import", "${scalar.provider}://${scalar.host}/${scalar.path}?q=${scalar.query}&home=${HOME:home-fallback}&missing=${placeholder.not.set:default-value}",
                "scalar.provider", "test",
                "scalar.host", "localhost",
                "scalar.path", "scalar-value",
                "scalar.query", "query-value"
            )),
            source("root-list", Map.of(
                "micronaut.config.import", List.of(
                    "${list.provider}://${list.host}/${list.first}",
                    "${list.provider}://${list.host}/${list.second}"
                ),
                "list.provider", "test",
                "list.host", "localhost",
                "list.first", "list-one",
                "list.second", "list-two"
            )),
            source("indexed-scalars", Map.of(
                "micronaut.config.import[0]", "${indexed.provider}://${indexed.host}/${indexed.first}",
                "micronaut.config.import[1]", "${indexed.provider}://${indexed.host}/${indexed.second}",
                "indexed.provider", "test",
                "indexed.host", "localhost",
                "indexed.first", "indexed-one",
                "indexed.second", "indexed-two"
            )),
            source("root-map", Map.of(
                "micronaut.config.import", Map.of("provider", "${rootmap.provider}", "resource-path", "/${rootmap.path}"),
                "rootmap.provider", "test",
                "rootmap.path", "root-map"
            )),
            source("root-list-maps", Map.of(
                "micronaut.config.import", List.of(
                    Map.of("provider", "${listmap.provider}", "resource-path", "/${listmap.first}"),
                    Map.of("provider", "${listmap.provider}", "resource-path", "/${listmap.second}")
                ),
                "listmap.provider", "test",
                "listmap.first", "list-map-one",
                "listmap.second", "list-map-two"
            )),
            source("indexed-maps", Map.of(
                "micronaut.config.import[0].provider", "${indexedmap.provider}",
                "micronaut.config.import[0].resource-path", "/${indexedmap.path}",
                "indexedmap.provider", "test",
                "indexedmap.path", "indexed-map"
            )),
            source("root-dotted-map", Map.of(
                "micronaut.config.import.provider", "${dotted.provider}",
                "micronaut.config.import.resource-path", "/${dotted.path}",
                "dotted.provider", "test",
                "dotted.path", "root-dotted"
            ))
        );

        for (PropertySource declaration : sources) {
            try (ApplicationContext context = start(importer, null, declaration)) {
                assertTrue(context.isRunning());
            }
        }
        assertEquals(10, importer.imports.size());
        assertEquals(Set.of(
            "scalar-value", "list-one", "list-two", "indexed-one", "indexed-two",
            "/root-map", "/list-map-one", "/list-map-two", "/indexed-map", "/root-dotted"
        ), importer.imports.stream().map(ConnectionString::getPath).collect(Collectors.toSet()));

        ConnectionString scalar = importer.imports.stream()
            .filter(declaration -> declaration.getOptions().containsKey("home"))
            .findFirst()
            .orElseThrow();
        assertEquals("test", scalar.getProtocol());
        assertEquals("localhost", scalar.getHosts().getFirst().host());
        assertEquals("query-value", scalar.getOptions().get("q"));
        assertEquals(System.getenv().getOrDefault("HOME", "home-fallback"), scalar.getOptions().get("home"));
        assertEquals("default-value", scalar.getOptions().get("missing"));
        assertTrue(sources.getFirst().get("micronaut.config.import").toString().contains("${scalar.provider}"));
    }

    @Test
    void resolvesWholeStructuredPlaceholderAndNestedCollectionValues() {
        RecordingImporter importer = new RecordingImporter();
        Map<String, Object> declaration = Map.of(
            "provider", "${provider.name}",
            "resource-path", "/${resource.name}",
            "includes", new String[]{"${include.pattern}"},
            "excludes", List.of("${exclude.pattern}"),
            "nested", Map.of("attempts", "${retry.attempts:2}")
        );
        try (ApplicationContext context = start(importer, null, source("structured", Map.of(
            "micronaut.config.import", "${import.declaration}",
            "import.declaration", declaration,
            "provider.name", "test",
            "resource.name", "structured",
            "include.pattern", "DB_.*",
            "exclude.pattern", "DB_LEGACY_PASSWORD"
        )))) {
            assertEquals("/structured", importer.imports.getFirst().getPath());
            assertArrayEquals(new String[]{"DB_.*"}, (String[]) importer.structuredValues.get("includes"));
            assertEquals(List.of("DB_LEGACY_PASSWORD"), importer.structuredValues.get("excludes"));
            assertEquals(Map.of("attempts", "2"), importer.structuredValues.get("nested"));
            assertEquals("/${resource.name}", declaration.get("resource-path"));
        }
    }

    @Test
    void resolvesNestedImportedFilePlaceholdersAndUsesFreshValuesOnRefresh() throws IOException {
        writeProperties("root-first.properties", """
            nested.host=localhost
            nested.path=child-first.properties
            nested.version=first
            micronaut.config.import=test://${nested.host}/${nested.path}?version=${nested.version}
            """);
        writeProperties("child-first.properties", "loaded.value=first\nprecedence.value=imported\n");
        writeProperties("root-second.properties", """
            nested.host=localhost
            nested.path=child-second.properties
            nested.version=second
            micronaut.config.import=test://${nested.host}/${nested.path}?version=${nested.version}
            """);
        writeProperties("child-second.properties", "loaded.value=second\nprecedence.value=imported\n");

        Map<String, Object> lowPriorityValues = new HashMap<>(Map.of(
            "micronaut.config.import", "test://localhost/${root.path}",
            "root.path", "root-first.properties"
        ));
        FileImporter importer = new FileImporter(tempDir);
        try (ApplicationContext context = start(importer, null,
            source("low-priority-import", lowPriorityValues),
            PropertySource.of("high-priority-root", Map.of("precedence.value", "root", "nested.version", "higher-priority"), 1000))) {
            assertEquals("first", context.getEnvironment().getRequiredProperty("loaded.value", String.class));
            assertEquals("root", context.getEnvironment().getRequiredProperty("precedence.value", String.class));

            lowPriorityValues.put("root.path", "root-second.properties");
            context.getEnvironment().refresh();

            assertEquals("second", context.getEnvironment().getRequiredProperty("loaded.value", String.class));
            assertEquals("root", context.getEnvironment().getRequiredProperty("precedence.value", String.class));
            assertEquals(List.of("higher-priority", "higher-priority"), importer.versions);
            assertTrue(importer.importedPaths.containsAll(List.of(
                "root-first.properties", "child-first.properties", "root-second.properties", "child-second.properties"
            )));
        }
    }

    @Test
    void unresolvedRequiredPlaceholderFailsEvenForOptionalImport() {
        RecordingImporter importer = new RecordingImporter();
        ApplicationContext context = newContext(importer, null,
            source("optional-import", Map.of("micronaut.config.import", "optional:test://localhost/${missing.required.path}")));
        try {
            ConfigurationException exception = assertThrows(ConfigurationException.class, context::start);
            assertTrue(exception.getMessage().contains("missing.required.path"));
            assertTrue(importer.imports.isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void detectsCycleAfterResolvingNestedImportPlaceholder() {
        RecordingImporter importer = new RecordingImporter(declaration -> {
            if (declaration.getPath().equals("root")) {
                return Optional.of(PropertySource.of("cycle-source", Map.of(
                    "micronaut.config.import", "test://localhost/${cycle.target}",
                    "cycle.target", "root"
                )));
            }
            return Optional.empty();
        });
        ApplicationContext context = newContext(importer, null,
            source("cycle-root", Map.of("micronaut.config.import", "test://localhost/root")));
        try {
            ConfigurationException exception = assertThrows(ConfigurationException.class, context::start);
            assertTrue(exception.getMessage().contains("Cycle detected"));
            assertTrue(exception.getMessage().contains("test://localhost/root"));
        } finally {
            context.close();
        }
    }

    @Test
    void deduplicatesStructuredImportsByArrayContents() {
        Map<String, Object> database = Map.of(
            "provider", "test", "resource-path", "/shared", "includes", new String[]{"${database.pattern}"});
        Map<String, Object> equivalent = Map.of(
            "provider", "test", "resource-path", "/shared", "includes", new String[]{"DB_.*"});
        Map<String, Object> different = Map.of(
            "provider", "test", "resource-path", "/shared", "includes", new String[]{"API_.*"});
        RecordingImporter importer = new RecordingImporter();
        try (ApplicationContext context = start(importer, null, source("structured-arrays", Map.of(
            "micronaut.config.import", List.of(database, equivalent, different),
            "database.pattern", "DB_.*"
        )))) {
            assertEquals(2, importer.imports.size());
            assertArrayEquals(new String[]{"API_.*"}, (String[]) importer.structuredValues.get("includes"));
            assertArrayEquals(new String[]{"${database.pattern}"}, (String[]) database.get("includes"));
        }
    }

    @Test
    void detectsStructuredImportCycleWithNestedArrays() {
        Map<String, Object> declaration = Map.of(
            "provider", "test", "resource-path", "/cycle",
            "options", List.of(Map.of("includes", new Object[]{new String[]{"DB_.*"}})));
        AtomicInteger calls = new AtomicInteger();
        RecordingImporter importer = new RecordingImporter(ignored -> {
            if (calls.incrementAndGet() > 8) {
                throw new IllegalStateException("Cycle detection did not stop a repeated structured import");
            }
            return Optional.of(source("structured-cycle", Map.of("micronaut.config.import", declaration)));
        });
        try (ApplicationContext context = newContext(importer, null,
            source("structured-cycle-root", Map.of("micronaut.config.import", declaration)))) {
            ConfigurationException exception = assertThrows(ConfigurationException.class, context::start);
            assertTrue(exception.getMessage().contains("Cycle detected"));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void discoversExpressionResolverFromApplicationClassLoaderAndSeedsItWithCurrentSources() throws IOException {
        TestExpressionResolver.OBSERVED_SEED.set(null);
        Path serviceFile = tempDir.resolve("META-INF/services/" + PropertyExpressionResolver.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, TestExpressionResolver.class.getName());

        RecordingImporter importer = new RecordingImporter();
        try (URLClassLoader appClassLoader = new URLClassLoader(
            new java.net.URL[]{tempDir.toUri().toURL()}, getClass().getClassLoader());
             ApplicationContext context = start(importer, appClassLoader,
                 source("spi-import", Map.of(
                     "micronaut.config.import[0]", "test://secret-service-ce.${test.expression}:443/secret/${SECRET_SERVICE_NAMESPACE_NAME}/tls/cert/latest?retry-attempts=${attempts:2}",
                     "SECRET_SERVICE_NAMESPACE_NAME", "sample-service",
                     "seed.value", "from-current-source"
                 )))) {
            assertEquals("test://secret-service-ce.resolved-from-current-source:443/secret/sample-service/tls/cert/latest?retry-attempts=2",
                importer.imports.getFirst().getRawValue());
            assertEquals("from-current-source", TestExpressionResolver.OBSERVED_SEED.get());
        }
    }

    private ApplicationContext start(RecordingImporter importer, ClassLoader classLoader, PropertySource... sources) {
        ApplicationContext context = newContext(importer, classLoader, sources);
        return context.start();
    }

    private ApplicationContext newContext(RecordingImporter importer, ClassLoader classLoader, PropertySource... sources) {
        DefaultApplicationContextBuilder builder = (DefaultApplicationContextBuilder) ApplicationContext.builder();
        builder.configImport(false)
            .propertySourcesLocator(new ConfigImportPropertySourcesLocator(() -> List.of(importer)));
        if (classLoader != null) {
            builder.classLoader(classLoader);
        }
        ApplicationContext context = builder.build();
        for (PropertySource source : sources) {
            context.getEnvironment().addPropertySource(source);
        }
        return context;
    }

    private void writeProperties(String name, String contents) throws IOException {
        Files.writeString(tempDir.resolve(name), contents);
    }

    private static MapPropertySource source(String name, Map<String, ?> values) {
        return new MapPropertySource(name, values);
    }

    private static class RecordingImporter implements PropertySourceImporter<ConnectionString> {
        private final List<ConnectionString> imports = new ArrayList<>();
        private Map<String, Object> structuredValues;
        private final Function<ConnectionString, Optional<PropertySource>> sourceFactory;

        private RecordingImporter() {
            this(declaration -> Optional.of(PropertySource.of("imported:" + declaration.getPath(), Map.of("imported.path", declaration.getPath()))));
        }

        private RecordingImporter(Function<ConnectionString, Optional<PropertySource>> sourceFactory) {
            this.sourceFactory = sourceFactory;
        }

        @Override
        public String getProvider() {
            return "test";
        }

        @Override
        public ConnectionString newImportDeclaration(ConnectionString connectionString) {
            URI.create(connectionString.getRawValue());
            return connectionString;
        }

        @Override
        public ConnectionString newImportDeclaration(ConvertibleValues<Object> values) {
            structuredValues = values.asMap();
            String provider = values.get("provider", String.class).orElseThrow();
            String resourcePath = values.get("resource-path", String.class).orElseThrow();
            String query = values.get("query", String.class).map(value -> "?" + value).orElse("");
            return ConnectionString.parse(provider + "://" + resourcePath + query);
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            ConnectionString declaration = context.importDeclaration();
            imports.add(declaration);
            return sourceFactory.apply(declaration);
        }
    }

    private static final class FileImporter extends RecordingImporter {
        private final Path directory;
        private final List<String> importedPaths = new ArrayList<>();
        private final List<String> versions = new ArrayList<>();

        private FileImporter(Path directory) {
            this.directory = directory;
        }

        @Override
        public Optional<PropertySource> importPropertySource(ImportContext<ConnectionString> context) {
            ConnectionString declaration = context.importDeclaration();
            String path = declaration.getPath();
            importedPaths.add(path);
            if (declaration.getOptions().containsKey("version")) {
                versions.add(declaration.getOptions().get("version"));
            }
            try {
                String contents = Files.readString(directory.resolve(path));
                String extension = path.substring(path.lastIndexOf('.') + 1);
                return context.importPropertySource(contents, path, extension, PropertySource.Origin.of("test://localhost/" + path));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static final class TestExpressionResolver implements PropertyExpressionResolver {
        private static final AtomicReference<String> OBSERVED_SEED = new AtomicReference<>();

        @Override
        public <T> Optional<T> resolve(PropertyResolver propertyResolver,
                                       ConversionService conversionService,
                                       String expression,
                                       Class<T> requiredType) {
            if (!"test.expression".equals(expression)) {
                return Optional.empty();
            }
            String seed = propertyResolver.getProperty("seed.value", String.class).orElseThrow();
            OBSERVED_SEED.set(seed);
            return conversionService.convert("resolved-" + seed, requiredType);
        }
    }
}
