package io.micronaut.test.lombok;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Issue12015LongLombokBuilderIntrospectionTest {

    @Test
    void testLongLombokBuilderIntrospectionUsesShortDescriptorName() {
        BeanIntrospection<MyLovelyOtherServiceVeryBeautifulEndpointVeryImportantResponseDto> introspection = BeanIntrospection.getIntrospection(MyLovelyOtherServiceVeryBeautifulEndpointVeryImportantResponseDto.class);

        assertTrue(introspection.hasBuilder());
        MyLovelyOtherServiceVeryBeautifulEndpointVeryImportantResponseDto bean = introspection.builder()
            .with("result", "ok")
            .build();
        assertEquals("ok", bean.getResult());

        Path descriptorsDirectory = Path.of("build", "classes", "java", "test", "META-INF", "micronaut", "io.micronaut.core.beans.BeanIntrospectionReference");
        String builderDescriptorName = findDescriptorName(descriptorsDirectory, "MyLovelyOtherServiceVeryBeautifulEndpointVeryImportantResponseDtoBuilder");
        assertTrue(builderDescriptorName.length() < 240, () -> "Expected shortened builder descriptor name, got length " + builderDescriptorName.length() + ": " + builderDescriptorName);
        assertTrue(builderDescriptorName.contains("$MyLovelyOtherServiceVeryBeautifulEndpointVeryImportantResponseDtoBuilder$Introspection"));
        assertTrue(!builderDescriptorName.contains("$io_micronaut_test_lombok_"), () -> "Descriptor should not contain underscored package prefix: " + builderDescriptorName);
    }

    @Introspected
    @lombok.Builder
    @lombok.Value
    @lombok.NoArgsConstructor(force = true, access = lombok.AccessLevel.PRIVATE)
    @lombok.AllArgsConstructor
    static class MyLovelyOtherServiceVeryBeautifulEndpointVeryImportantResponseDto {
        String result;
    }

    private static String findDescriptorName(Path descriptorsDirectory, String contains) {
        try (var stream = Files.list(descriptorsDirectory)) {
            List<String> names = stream
                .map(path -> path.getFileName().toString())
                .filter(name -> name.contains(contains))
                .toList();
            assertEquals(1, names.size(), () -> "Expected exactly one descriptor containing '" + contains + "' but found: " + names);
            return names.getFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to inspect generated introspection descriptors", e);
        }
    }
}
