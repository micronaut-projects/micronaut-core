/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A colon language whose selector ranks the routes by the path first, as JAX-RS does: the most
 * specific template wins, then the content type and the accepted types choose among its routes,
 * and the request is not acceptable, or of an unsupported media type, when none of them fits.
 */
public final class PathFirstColonRouteTemplateEngine extends OrderedColonRouteTemplateEngine {

    public static final String ID = "test.colon.path-first";

    public static final PathFirstColonRouteTemplateEngine INSTANCE = new PathFirstColonRouteTemplateEngine();

    private PathFirstColonRouteTemplateEngine() {
        super(ID);
    }

    public static RouteTemplate template(String expression) {
        return INSTANCE.parse(expression);
    }

    @Override
    public boolean selectsMediaTypes() {
        return true;
    }

    @Override
    public List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches) {
        UriRouteInfo<?, ?> best = null;
        for (UriRouteMatch<?, ?> match : matches) {
            if (best == null || ROUTE_ORDER.compare(match.getRouteInfo(), best) < 0) {
                best = match.getRouteInfo();
            }
        }
        List<UriRouteMatch<?, ?>> path = new ArrayList<>();
        for (UriRouteMatch<?, ?> match : matches) {
            if (ROUTE_ORDER.compare(match.getRouteInfo(), best) == 0) {
                path.add(match);
            }
        }
        MediaType contentType = request.getContentType().orElse(null);
        List<UriRouteMatch<?, ?>> consuming = new ArrayList<>();
        for (UriRouteMatch<?, ?> match : path) {
            if (contentType == null || anyCompatible(match.getRouteInfo().getConsumes(), List.of(contentType))) {
                consuming.add(match);
            }
        }
        if (consuming.isEmpty()) {
            return List.of(Selection.unsupportedMediaType(path.get(0)));
        }
        Collection<MediaType> accepted = request.accept();
        for (UriRouteMatch<?, ?> match : consuming) {
            if (accepted.isEmpty() || anyCompatible(match.getRouteInfo().getProduces(), accepted)) {
                return List.of(Selection.of(match));
            }
        }
        return List.of(Selection.notAcceptable(consuming.get(0)));
    }

    private static boolean anyCompatible(List<MediaType> types, Collection<MediaType> requested) {
        if (types.isEmpty()) {
            return true;
        }
        for (MediaType type : types) {
            for (MediaType other : requested) {
                if (type.matches(other) || other.matches(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return ID;
    }
}
