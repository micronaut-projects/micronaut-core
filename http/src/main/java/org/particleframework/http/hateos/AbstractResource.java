/*
 * Copyright 2017 original authors
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
package org.particleframework.http.hateos;

import org.particleframework.core.util.StringUtils;
import org.particleframework.core.value.OptionalMultiValues;

import javax.annotation.Nullable;
import java.util.*;

/**
 * An abstract implementation of {@link Resource}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractResource<Impl extends AbstractResource> implements Resource {
    private final Map<CharSequence, List<Link>> links = new LinkedHashMap<>(1);
    private final Map<CharSequence, List<Resource>> embedded = new LinkedHashMap<>(1);

    /**
     * Add a link with the given reference
     *
     * @param ref The reference
     * @param link The link
     * @return This VndError
     */
    public Impl link(@Nullable CharSequence ref, @Nullable Link link) {
        if(StringUtils.isNotEmpty(ref) && link != null) {
            List<Link> links = this.links.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            links.add(link);
        }
        return (Impl) this;
    }

    /**
     * Add an embedded resource with the given reference
     *
     * @param ref The reference
     * @param resource The resource
     * @return This VndError
     */
    public Impl embedded(CharSequence ref, Resource resource) {
        if(StringUtils.isNotEmpty(ref) && resource != null) {
            List<Resource> resources = this.embedded.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            resources.add(resource);
        }
        return (Impl) this;
    }


    /**
     * Add an embedded resource with the given reference
     *
     * @param ref The reference
     * @param resource The resource
     * @return This VndError
     */
    public Impl embedded(CharSequence ref, Resource... resource) {
        if(StringUtils.isNotEmpty(ref) && resource != null) {
            List<Resource> resources = this.embedded.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            resources.addAll(Arrays.asList(resource));
        }
        return (Impl) this;
    }

    /**
     * Add an embedded resource with the given reference
     *
     * @param ref The reference
     * @param resourceList The resources
     * @return This VndError
     */
    public Impl embedded(CharSequence ref, List<Resource> resourceList) {
        if(StringUtils.isNotEmpty(ref) && resourceList != null) {
            List<Resource> resources = this.embedded.computeIfAbsent(ref, charSequence -> new ArrayList<>());
            resources.addAll(resourceList);
        }
        return (Impl) this;
    }

    @Override
    public OptionalMultiValues<Link> getLinks() {
        return OptionalMultiValues.of( links);
    }

    @Override
    public OptionalMultiValues<Resource> getEmbedded() {
        return OptionalMultiValues.of(embedded);
    }
}
