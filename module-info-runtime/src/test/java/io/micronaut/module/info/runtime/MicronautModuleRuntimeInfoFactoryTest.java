package io.micronaut.module.info.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.module.info.AbstractMicronautModuleInfo;
import io.micronaut.module.info.MavenCoordinates;
import io.micronaut.module.info.MicronautModuleInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicronautModuleRuntimeInfoFactoryTest {
    @Test
    @DisplayName("Can fetch loaded modules at runtime")
    void testsRuntimeModuleInfo() {
        var ctx = ApplicationContext.run();
        var modules = ctx.getBean(MicronautRuntimeModules.class);
        var root = modules.getRoot();
        assertEquals(2, root.getChildren().size());
        var topLevel = root.getChildren().stream().map(MicronautRuntimeModule::getId).toList();
        assertEquals(List.of("io.micronaut:micronaut-core", "io.micronaut:micronaut-dummy"), topLevel);
        var core = root.getChildren().stream().filter(m -> "io.micronaut:micronaut-core".equals(m.getId())).findFirst().orElseThrow();
        var dummy = root.getChildren().stream().filter(m -> "io.micronaut:micronaut-dummy".equals(m.getId())).findFirst().orElseThrow();
        assertEquals(1, core.getChildren().size());
        assertEquals(0, dummy.getChildren().size());
        assertEquals("io.micronaut:micronaut-http", core.getChildren().getFirst().getId());
    }

    @Test
    @DisplayName("Can get the Maven coordinates of Micronaut modules found on classpath")
    void testMavenCoordinates() {
        var ctx = ApplicationContext.run();
        var modules = ctx.getBean(MicronautRuntimeModules.class);
        var root = modules.getRoot();
        var core = root.getChildren().stream().filter(m -> "io.micronaut:micronaut-core".equals(m.getId())).findFirst().orElseThrow();
        var coreModule = new MavenCoordinates("io.micronaut", "micronaut-core", "1.0");
        assertEquals(coreModule, core.getMavenCoordinates().orElseThrow());
    }

    /**
     * A dummy factory which adds new modules at runtime for tests.
     * A user wouldn't typically do this, but rather use auto-generated
     * module descriptors at build time. However since this requires
     * service loader classes, it's cumbersome for testing so this
     * factory adds new modules programmatically.
     */
    @Factory
    public static class DummyModulesFactory {

        @Bean
        public MicronautModuleInfo root() {
            return module("io.micronaut", "micronaut-core", "1.0");
        }

        @Bean
        public MicronautModuleInfo child() {
            return module("io.micronaut", "micronaut-http", "1.0", "io.micronaut:micronaut-core");
        }

        private DummyModule module(String groupId, String artifactId, String version) {
            return module(groupId, artifactId, version, null);
        }

        private DummyModule module(String groupId, String artifactId, String version, String parent) {
            return new DummyModule(
                groupId + ":" + artifactId,
                artifactId,
                null,
                version,
                new MavenCoordinates(groupId, artifactId, version),
                parent,
                Set.of()
            );
        }
    }

    private static class DummyModule extends AbstractMicronautModuleInfo {

        public DummyModule(String id,
                           String name,
                           String description,
                           String version,
                           MavenCoordinates mavenCoordinates,
                           String parentModuleId,
                           Set<String> tags) {
            super(id, name, description, version, mavenCoordinates, parentModuleId, tags);
        }
    }
}
