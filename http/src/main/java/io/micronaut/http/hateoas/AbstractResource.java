/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.hateoas;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An abstract implementation of {@link Resource}.
 *
 * @param <Impl> An Abstract resource implementation
 * @author Graeme Rocher
 * @since 1.1
 */
@Produces(MediaType.APPLICATION_HAL_JSON)
@Introspected
public abstract class AbstractResource<Impl extends AbstractResource> implements Resource {

    private final Map<CharSequence, List<Link>> linkMap = new LinkedHashMap<>(1);
    private final Map<CharSequence, List<Resource>> embeddedMap = new LinkedHashMap<>(1);

    /**
     * Add a link with the given reference.
     *
     * @param ref  The reference
     * @param link The link
     * @return This JsonError
     */
    public Impl link(@Nullable CharSequence ref, @Nullable Link link) {
        if (StringUtils.isNotEmpty(ref) && link != null) {
            List<Link> links = this.linkMap.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            links.add(link);
        }
        return (Impl) this;
    }

    /**
     * Add a link with the given reference.
     *
     * @param ref  The reference
     * @param link The link
     * @return This JsonError
     */
    public Impl link(@Nullable CharSequence ref, @Nullable String link) {
        if (StringUtils.isNotEmpty(ref) && link != null) {
            List<Link> links = this.linkMap.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            links.add(Link.of(link));
        }
        return (Impl) this;
    }

    /**
     * Add an embedded resource with the given reference.
     *
     * @param ref      The reference
     * @param resource The resource
     * @return This JsonError
     */
    public Impl embedded(CharSequence ref, Resource resource) {
        if (StringUtils.isNotEmpty(ref) && resource != null) {
            List<Resource> resources = this.embeddedMap.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            resources.add(resource);
        }
        return (Impl) this;
    }

    /**
     * Add an embedded resource with the given reference.
     *
     * @param ref      The reference
     * @param resource The resource
     * @return This JsonError
     */
    public Impl embedded(CharSequence ref, Resource... resource) {
        if (StringUtils.isNotEmpty(ref) && resource != null) {
            List<Resource> resources = this.embeddedMap.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            resources.addAll(Arrays.asList(resource));
        }
        return (Impl) this;
    }

    /**
     * Add an embedded resource with the given reference.
     *
     * @param ref          The reference
     * @param resourceList The resources
     * @return This JsonError
     */
    public Impl embedded(CharSequence ref, List<Resource> resourceList) {
        if (StringUtils.isNotEmpty(ref) && resourceList != null) {
            List<Resource> resources = this.embeddedMap.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            resources.addAll(resourceList);
        }
        return (Impl) this;
    }

    @Override
    public OptionalMultiValues<Link> getLinks() {
        return OptionalMultiValues.of(linkMap);
    }

    @Override
    public OptionalMultiValues<Resource> getEmbedded() {
        return OptionalMultiValues.of(embeddedMap);
    }

    /**
     * Allows de-serializing of links with Jackson.
     *
     * @param links The links
     */
    @SuppressWarnings("unchecked")
    @Internal
    @ReflectiveAccess
    protected final void setLinks(Map<String, Object> links) {
        for (Map.Entry<String, Object> entry : links.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> linkMap = (Map<String, Object>) value;
                link(name, linkMap);
            }
        }
    }

    private void link(String name, Map<String, Object> linkMap) {
        ConvertibleValues<Object> values = ConvertibleValues.of(linkMap);
        Optional<String> uri = values.get(Link.HREF, String.class);
        uri.ifPresent(uri1 -> {
            Link.Builder link = Link.build(uri1);
            values.get("templated", Boolean.class)
                    .ifPresent(link::templated);
            values.get("hreflang", String.class)
                    .ifPresent(link::hreflang);
            values.get("title", String.class)
                    .ifPresent(link::title);
            values.get("profile", String.class)
                    .ifPresent(link::profile);
            values.get("deprecation", String.class)
                    .ifPresent(link::deprecation);
            link(name, link.build());
        });
    }
}
