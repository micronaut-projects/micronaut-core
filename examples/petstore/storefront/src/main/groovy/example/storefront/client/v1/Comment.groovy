package example.storefront.client.v1

/**
 * @author zacharyklein
 * @since 1.0
 */
class Comment implements example.api.v1.Comment {
    Long id
    String poster
    String content
    Date dateCreated
}
