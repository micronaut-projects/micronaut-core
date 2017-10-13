package example

import grails.gorm.services.Service

@Service(Book)
interface BookService {
    List<Book> list()
    Book save(String title)
}
