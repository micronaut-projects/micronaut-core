package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.*;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.cli.Option;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

/**
 * <p>An {@link InfoSource} that retrieves info from Git properties. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.git.enabled", notEquals = "false")
public class GitInfoSource implements InfoSource {

    private static final String EXTENSION = ".properties";
    private static final String PREFIX = "classpath:";

    @Value("${endpoints.info.git.location:git.properties}")
    private String gitPropertiesPath;

    private ResourceResolver resourceResolver;
    private final Supplier<Optional<PropertySource>> supplier;

    public GitInfoSource(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.supplier = SupplierUtil.memoized(this::retrieveGitInfo);
    }

    @Override
    public Publisher<PropertySource> getSource() {
        Optional<PropertySource> propertySource = supplier.get();
        return propertySource.map(Flowable::just).orElse(Flowable.empty());
    }

    private Optional<PropertySource> retrieveGitInfo() {
        StringBuilder pathBuilder = new StringBuilder();

        if (!gitPropertiesPath.startsWith(PREFIX)) {
            pathBuilder.append(PREFIX);
        }

        if (gitPropertiesPath.endsWith(EXTENSION)) {
            int index = gitPropertiesPath.indexOf(EXTENSION);
            pathBuilder.append(gitPropertiesPath, 0, index);
        } else {
            pathBuilder.append(gitPropertiesPath);
        }
        String path = pathBuilder.toString();

        Optional<ResourceLoader> resourceLoader = resourceResolver.getSupportingLoader(path);
        if (resourceLoader.isPresent()) {
            PropertiesPropertySourceLoader propertySourceLoader = new PropertiesPropertySourceLoader();
            return propertySourceLoader.load(path, resourceLoader.get(), null);
        }

        return Optional.empty();
    }
}

