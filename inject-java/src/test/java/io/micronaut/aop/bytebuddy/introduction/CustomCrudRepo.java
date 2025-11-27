package io.micronaut.aop.bytebuddy.introduction;

import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.aop.ByteBuddyRuntimeProxy;
import io.micronaut.aop.bytebuddy.ByteBuddyStacktraceVerified;
import io.micronaut.aop.introduction.CrudRepo;
import io.micronaut.aop.introduction.RepoDef;

import java.util.Optional;

@ByteBuddyStacktraceVerified
@RepoDef
@RuntimeProxy(ByteBuddyRuntimeProxy.class)
public interface CustomCrudRepo extends CrudRepo<String, Long> {

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
