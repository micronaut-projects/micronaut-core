package petstore.web;

import io.micronaut.context.BeanProvider;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteBuilder;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * The functional router: binds a handler function to each declared route it implements, by the
 * constants the annotation processor generated, and calls the resource bean.
 */
@Singleton
public class PetRoutes implements HttpRoutes {
    private final BeanProvider<PetResource> pets;

    PetRoutes(BeanProvider<PetResource> pets) {
        this.pets = pets;
    }

    @Override
    public void routes(RouteBuilder routes) {
        routes.handle(PetResourceRoutes.NAME, (request, path) ->
            HttpResponse.ok(pets.get().name(path.getLong("id"))));
        routes.handle(PetResourceRoutes.OWNED, (request, path) ->
            HttpResponse.ok(pets.get().owned(path.getLong("id"), path.get("owner", UUID.class))));
        routes.handle(PetResourceRoutes.FILE, (request, path) ->
            HttpResponse.ok(pets.get().file(path.getLong("id"), path.getString("file"))));
        routes.handleForm(PetResourceRoutes.ADD, (request, path, form) ->
            HttpResponse.ok(pets.get().add(form.getString("name"), form.getInt("age"))));
        routes.handleForm(PetResourceRoutes.RENAME, (request, path, form) -> {
            pets.get().rename(path.getLong("id"), form.getString("name"));
            return HttpResponse.noContent();
        });
        // PetResourceRoutes.PHOTO is declared but not bound: it is not a route
    }
}
