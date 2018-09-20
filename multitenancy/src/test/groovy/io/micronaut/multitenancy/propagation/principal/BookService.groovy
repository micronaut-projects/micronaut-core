package io.micronaut.multitenancy.propagation.principal

import io.micronaut.context.annotation.Requires
import io.micronaut.multitenancy.tenantresolver.TenantResolver
import io.micronaut.security.utils.SecurityService

import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Requires(property = 'spec.name', value = 'multitenancy.principal.gorm')
@Singleton
class BookService {
    private final TenantResolver tenantResolver

    BookService(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver
    }

    private final Map<String, List<Book>> books = new ConcurrentHashMap<>()

    Book save(String username, String title) {
        if (!books.containsKey(username)) {
            books.put(username, new ArrayList<>())
        }
        Book b = new Book(title: title)
        books.get(username).add(b)
        return b
    }

    List<Book> list() {
        return books.get(tenantResolver.resolveTenantIdentifier() as String)
    }
}



