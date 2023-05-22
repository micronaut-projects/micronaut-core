package io.micronaut.aop.introduction;

public interface DataCrudRepo<E, I> {

    E findById(I id);

    void save(E entity);

}
