package io.micronaut.build.internal.pom

import groovy.transform.Canonical
import io.micronaut.build.catalogs.internal.LenientVersionCatalogParser
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.plugins.catalog.CatalogPluginExtension
import org.gradle.api.tasks.TaskAction
/**
 * This is an internal task which responsibility is to parse
 * the Micronaut version catalog used internally, extract components
 * which belong to the BOM, in order to populate the version catalog
 * model for Gradle.
 *
 * In the end, this model is used to generate a version catalog in
 * addition to the bom. This will let users choose between importing
 * a BOM or using the Micronaut version catalog.
 *
 */
@Canonical
class VersionCatalogConverter {
    final File catalogFile
    final CatalogPluginExtension catalogExtension
    final Map<String, String> extraVersions = [:]
    final Map<String, Library> extraLibraries = [:]

    @TaskAction
    void populateModel() {
        def parser = new LenientVersionCatalogParser()
        parser.parse(catalogFile.newInputStream())
        def model = parser.model
        catalogExtension.versionCatalog {builder ->
            extraVersions.forEach { alias, version ->
                builder.version(alias, version)
            }
            extraLibraries.forEach { alias, library ->
                builder.alias(alias)
                    .to(library.group, library.name)
                    .versionRef(library.versionRef)
            }
            model.versionsTable.each { version ->
                if (version.reference.startsWith('managed-')) {
                    builder.version(version.reference.substring(8), version.version.require)
                }
            }
            model.librariesTable.each { library ->
                if (library.alias.startsWith("managed-") && library.version.reference) {
                    if (!library.version.reference.startsWith("managed-")) {
                        throw new InvalidUserCodeException("Version catalog declares a managed library '${library.alias}' referencing a non managed version '${library.version.reference}'. Make sure to use a managed version.")
                    }
                    builder.alias(library.alias.substring(8))
                        .to(library.group, library.name)
                        .versionRef(library.version.reference.substring(8))
                }
            }
        }
    }

    static Library library(String group, String name, String versionRef) {
        new Library(group, name, versionRef)
    }

    @Canonical
    static class Library {
        final String group
        final String name
        final String versionRef
    }
}
