package io.micronaut.aop.dynamicproxy.introduction;

import io.micronaut.aop.JdkRuntimeProxy;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.aop.introduction.CrudRepo;
import io.micronaut.aop.introduction.RepoDef;

import java.util.Optional;

@RepoDef
@RuntimeProxy(JdkRuntimeProxy.class)
public interface JdkCustomCrudRepo extends CrudRepo<String, Long> {

    @Override
    Optional<String> findById(Long id);

    default String findByIdWithDefault(Long id) {
        Optional<String> result = findById(id);
        if (result == null) {
            return "empty";
        }
        return result.orElse("default");
    }
}
