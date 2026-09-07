package io.micronaut.reflection

/**
 * The interface {@link MemberParityBase} implements: a getter and a setter declared away from any class, so
 * that the member an interface declares can be compared between the two descriptions.
 */
interface MemberParityContract {

    @Tag("contract-getter")
    String getNote()

    @Tag("contract-setter")
    void setNote(String note)
}
