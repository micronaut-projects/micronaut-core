package io.micronaut.dev;

import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicronautDevMainTest {

    @TempDir
    Path project;

    @Test
    void theManifestIsReadFromEitherSpellingAndTheRestGoesToTheApplication() {
        MicronautDevMain.LaunchArguments separate = MicronautDevMain.LaunchArguments.parse(new String[] {"a", "--manifest", "dev.properties", "b"}, null);
        assertEquals("dev.properties", separate.manifestPath());
        assertEquals(List.of("a", "b"), separate.applicationArguments());

        MicronautDevMain.LaunchArguments joined = MicronautDevMain.LaunchArguments.parse(new String[] {"--manifest=other.properties", "-x"}, "ignored.properties");
        assertEquals("other.properties", joined.manifestPath());
        assertEquals(List.of("-x"), joined.applicationArguments());
    }

    @Test
    void theSystemPropertyNamesTheManifestWhenTheArgumentsDoNot() {
        MicronautDevMain.LaunchArguments arguments = MicronautDevMain.LaunchArguments.parse(new String[] {"a"}, "property.properties");
        assertEquals("property.properties", arguments.manifestPath());
        assertEquals(List.of("a"), arguments.applicationArguments());
        // the option without a value is the application's
        assertEquals(List.of("--manifest"), MicronautDevMain.LaunchArguments.parse(new String[] {"--manifest"}, "property.properties").applicationArguments());
    }

    @Test
    void withoutAManifestTheLauncherRefusesToRun() {
        String previous = System.clearProperty(DevManifest.MANIFEST_PROPERTY);
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new MicronautDevMain().run(new String[] {"a"}));
            assertTrue(e.getMessage().contains(MicronautDevMain.MANIFEST_OPTION));
        } finally {
            if (previous != null) {
                System.setProperty(DevManifest.MANIFEST_PROPERTY, previous);
            }
        }
    }

    @Test
    void theLoaderReadsTheLiveResourceRootsAheadOfTheReloadableRoots() throws IOException {
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.createDirectories(project.resolve("build/classes"));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.resources.static=src/main/resources/missing
            """);
        DevManifest manifest = DevManifest.load(manifestFile);
        MicronautDevMain main = new MicronautDevMain();
        assertNotNull(main.parentClassLoader());
        DevClassLoader loader = main.createClassLoader(manifest);
        assertSame(main.parentClassLoader(), loader.getParent());
        // a resource root that does not exist is left out
        assertEquals(List.of(resources), loader.liveRoots());
        assertEquals(1, loader.generation());
        // the compilers are the available ones registered as services: Java is always there
        assertTrue(main.createCompilers(manifest).containsKey(SourceKind.JAVA));
    }

    @Test
    void theApplicationsMainRunsThroughTheGivenLoader() throws Exception {
        MicronautDevMain main = new MicronautDevMain();
        ClassLoader loader = MicronautDevMainTest.class.getClassLoader();
        RecordingMain.received = null;
        main.launchApplication(loader, RecordingMain.class.getName(), new String[] {"x", "y"});
        assertArrayEquals(new String[] {"x", "y"}, RecordingMain.received);
    }

    @Test
    void whatMainThrowsIsWhatTheLaunchThrows() {
        MicronautDevMain main = new MicronautDevMain();
        ClassLoader loader = MicronautDevMainTest.class.getClassLoader();
        // a checked exception is rethrown as main threw it
        IOException checked = assertThrows(IOException.class, () -> main.launchApplication(loader, CheckedFailureMain.class.getName(), new String[0]));
        assertEquals("checked", checked.getMessage());
        // an error stays wrapped in the invocation's exception
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> main.launchApplication(loader, ErrorMain.class.getName(), new String[0]));
        assertInstanceOf(AssertionError.class, error.getCause());
        assertThrows(ClassNotFoundException.class, () -> main.launchApplication(loader, "app.Missing", new String[0]));
    }

    public static final class RecordingMain {
        static String[] received;

        public static void main(String[] args) {
            received = args;
        }
    }

    public static final class CheckedFailureMain {
        public static void main(String[] args) throws IOException {
            throw new IOException("checked");
        }
    }

    public static final class ErrorMain {
        public static void main(String[] args) {
            throw new AssertionError("error");
        }
    }
}
