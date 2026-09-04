package io.micronaut.reflection;

/**
 * An implementation of a generic interface: {@code save(String)} overrides the {@code save(Object)} the
 * interface declares once erased, while {@code save(Long)} overrides the overload.
 */
public class ExecUserRepo implements ExecRepo<String> {

    @Override
    @Tag("user")
    public void save(@Tag("user-param") String item) {
    }

    @Override
    @Tag("user-id")
    public void save(@Tag("user-id-param") Long id) {
    }
}
