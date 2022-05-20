package io.micronaut.build.internal;

import groovy.xml.XmlSlurper;
import groovy.xml.slurpersupport.GPathResult;
import groovy.xml.slurpersupport.NodeChild;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@CacheableTask
public abstract class WriteMicronautVersionInfoTask extends DefaultTask {

    public static final String MICRONAUT_VERSIONS_PROPERTIES_FILE_NAME = "micronaut-versions.properties";

    @Input
    public abstract Property<String> getVersion();

    @Input
    public ListProperty<String> dependencies;

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    public WriteMicronautVersionInfoTask(Project project) {
        dependencies = project.getObjects().listProperty(String.class);
    }

    @TaskAction
    public void writeVersionInfo() throws IOException {
        Map<String, String> props = new TreeMap<>();
        for (String dependency : dependencies.get()) {
            String[] groups = dependency.split(":", 3);
            getLogger().lifecycle("Scanning {}:{}:{}", groups[0], groups[1], groups[2]);
            Map<String, String> bomProperties = bomProperties(groups[0], groups[1], groups[2]);
            for (Map.Entry<String, String> entry : bomProperties.entrySet()) {
                if (entry.getKey().startsWith("micronaut.")) {
                    getLogger().lifecycle("Skipping {} from {}", entry.getKey(), dependency);
                } else {
                    if (props.containsKey(entry.getKey())) {
                        getLogger().warn("Property {} from {} already exists ({}). Replacing with {}", entry.getKey(), dependency, props.get(entry.getKey()), entry.getValue());
                    }
                    props.put(entry.getKey(), entry.getValue());
                }
            }
        }
        try (OutputStream out = Files.newOutputStream(getOutputDirectory().file(MICRONAUT_VERSIONS_PROPERTIES_FILE_NAME).get().getAsFile().toPath())) {
            for (Map.Entry<String, String> entry : props.entrySet()) {
                String line = entry.getKey() + "=" + entry.getValue() + "\n";
                out.write(line.getBytes(StandardCharsets.ISO_8859_1));
            }
        }
    }

    private Map<String, String> bomProperties(String groupId, String artifactId, String version) {
        ArtifactResolutionResult result = getProject().getDependencies().createArtifactResolutionQuery()
                .forModule(groupId, artifactId, version)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute();
        Map<String, String> props = new TreeMap<>();
        for (ComponentArtifactsResult component : result.getResolvedComponents()) {
            component.getArtifacts(MavenPomArtifact.class).forEach(artifact -> {
                if (artifact instanceof ResolvedArtifactResult) {
                    ResolvedArtifactResult resolved = (ResolvedArtifactResult) artifact;
                    GPathResult pom = null;
                    try {
                        pom = new XmlSlurper().parse(resolved.getFile());
                    } catch (IOException | SAXException | ParserConfigurationException e) {
                        // ignore
                    }
                    ((GPathResult) pom.getProperty("properties")).children().forEach(child -> {
                        NodeChild node = (NodeChild) child;
                        props.put(node.name(), node.text());
                    });
                }
            });
        }
        return props;
    }

    public ListProperty<String> getDependencies() {
        return dependencies;
    }
}
