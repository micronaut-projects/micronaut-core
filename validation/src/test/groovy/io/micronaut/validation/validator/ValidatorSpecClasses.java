package io.micronaut.validation.validator;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;

import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;


@Introspected
public class ValidatorSpecClasses {
    // test validate property argument
    @Introspected
    public static class ListOfNames {
        @NotNull
        private List<@Size(min=2, max=8) String> names;

        public ListOfNames(List<String> names) {
            this.names = names;
        }

        public List<String> getNames() {
            return names;
        }
    }

    // test validate property argument of map
    @Introspected
    public static class PhoneBook {
        private Map<@NotBlank String, @Min(100) Integer> numbers;

        public PhoneBook(Map<String, Integer> numbers) {
            this.numbers = numbers;
        }

        public Map<String, Integer> getNumbers() {
            return numbers;
        }
    }

    // test validate property argument cascade
    @Introspected
    public static class Author {
        @NotBlank
        private String name;

        private List<@Valid Book> books;

        public Author(String name) {
            this.name = name;
            this.books = new ArrayList<Book>();
        }

        public Author(String name, List<Book> books) {
            this.name = name;
            this.books = books;
        }

        public String getName() {
            return name;
        }

        public List<Book> getBooks() {
            return books;
        }
    }

    @Introspected
    public static class Book {
        @Size(min=2)
        private String name;

        private List<@Valid Author> authors;

        public Book(String name) {
            this.name = name;
            this.authors = null;
        }

        public Book(String name, List<Author> authors) {
            this.name = name;
            this.authors = authors;
        }

        public List<Author> getAuthors() {
            return authors;
        }

        public String getName() {
            return name;
        }
    }

    // test validate property argument cascade - nested
    @Introspected
    public static class Library {
        final private Set<@Valid Book> books;

        public Library(Set<@Valid Book> books) {
            this.books = books;
        }

        public Set<Book> getBooks() {
            return books;
        }
    }

    // test validate property argument cascade
    @Introspected
    public static class Email {
        final private @Size(max=2) List<@NotBlank String> recoveryEmails;

        public Email(@Size(max=2) List<String> recoveryEmails) {
            this.recoveryEmails = recoveryEmails;
        }

        public List<String> getRecoveryEmails() {
            return recoveryEmails;
        }
    }

    // test validate Optional property type argument
    @Introspected
    public static class ClassWithOptional {
        final private Optional<@Min(100) Integer> number;

        public ClassWithOptional(Integer number) {
            this.number = Optional.of(number);
        }

        public Optional<Integer> getNumber() {
            return number;
        }
    }

    // test executable validator - cascade
    @Singleton
    public static class BookService {
        @Executable
        void saveBook(@Valid Book book) {

        }
    }

    // test validate property argument cascade to non-introspected - map
    @Introspected
    public static class ApartmentBuilding {
        private Map<Integer, @Valid Person> apartmentLivers;

        public ApartmentBuilding(Map<Integer, Person> apartmentLivers) {
            this.apartmentLivers = apartmentLivers;
        }

        public Map<Integer, Person> getApartmentLivers() {
            return apartmentLivers;
        }
    }

    //not-introspected
    public static class Person {
        @NotBlank
        private final String name;

        public Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // test validate property argument cascade - enum
    public enum BookCondition {
        NEW,
        USED
    }

    @Introspected
    public static class BooksInventory {
        List<@NotNull BookCondition> items;

        public BooksInventory(List<BookCondition> items) {
            this.items = items;
        }

        public List<BookCondition> getItems() {
            return items;
        }
    }

    // test validate property argument cascade - nested iterables
    @Introspected
    public static class PositiveMatrix {
        List<List<@Min(value=0) Integer>> matrix;

        public PositiveMatrix(List<List<Integer>> matrix) {
            this.matrix = matrix;
        }

        public List<List<Integer>> getMatrix() {
            return matrix;
        }
    }

    // test validate method argument generic annotations
    // test validate method argument generic annotations cascade
    // test validate return type
    // test validate return type annotations cascade
    // test validate return type annotations cascade - nested iterables
    @Introspected
    public static class Client {
        @Size(min=3, max=10)
        private final String name;

        public Client(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Singleton
    public static class Bank {
        @Executable
        public void deposit(List<@Min(value=1) Integer> banknotes) {

        }

        @Executable
        public void createAccount(
                @Valid Client client,
                Map<@NotBlank String, @Valid Client> clientsWithAccess)
        {

        }

        @Executable
        public @Min(0) Float getBalance() {
            return -100.0f;
        }

        @Executable
        public Map<@NotBlank String, @Valid Client> getClientsWithAccess(String accountName) {
            return null;
        }

        // returns all accounts with a list of authorized clients
        @Executable
        public Map<Integer, @Size(min=1) List<@Valid Client>> getAllAccounts() {
            return null;
        }
    }

    // test validate Optional method argument type argument
    @Singleton
    public static class ClassWithOptionalMethod {
        @Executable
        void optionalMethod(Optional<@Min(100) Integer> number) {

        }
    }
}


