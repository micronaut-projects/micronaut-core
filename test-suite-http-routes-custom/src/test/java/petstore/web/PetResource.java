package petstore.web;

import io.micronaut.test.routes.custom.annotation.Read;
import io.micronaut.test.routes.custom.annotation.Resource;
import io.micronaut.test.routes.custom.annotation.Write;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A resource of the made-up web framework: the annotation processor declares its routes, the
 * {@link PetRoutes} implement them.
 */
@Singleton
@Resource("/pets")
public class PetResource {
    private final Map<Long, String> pets = new ConcurrentHashMap<>(Map.of(1L, "Rex"));
    private final AtomicLong ids = new AtomicLong(1);

    @Read("/{id}")
    String name(long id) {
        return pets.getOrDefault(id, "unknown");
    }

    @Read("/{id}/owners/{owner}")
    String owned(long id, UUID owner) {
        return pets.get(id) + " of " + owner;
    }

    @Read("/{id}/photo")
    byte[] photo(long id) {
        return new byte[0];
    }

    @Read("/{id}/files/{+file}")
    String file(long id, String file) {
        return pets.get(id) + ": " + file;
    }

    @Write
    String add(String name, int age) {
        long id = ids.incrementAndGet();
        pets.put(id, name + " (" + age + ")");
        return String.valueOf(id);
    }

    @Write("/{id}/rename")
    void rename(long id, String name) {
        pets.put(id, name);
    }
}
