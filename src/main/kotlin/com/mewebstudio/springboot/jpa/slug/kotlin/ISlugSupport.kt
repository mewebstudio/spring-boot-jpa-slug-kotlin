package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Interface to be implemented by entities that support slugs.
 *
 * This interface defines the contract for entities that require a slug field,
 * typically for use in SEO-friendly URLs or human-readable identifiers.
 *
 * Implementing this interface allows the slug generation mechanism
 * (e.g., via [ISlugGenerator]) to interact with the entity's
 * identifier and slug field in a consistent way.
 *
 * **Usage example:**
 * <pre>{@code
 * @Entity
 * class Article : ISlugSupport<Long> {
 *     var id: Long? = null
 *     var title: String? = null
 *     var slug: String? = null
 * }
 * }</pre>
 *
 * @param ID the type of the entity's identifier (e.g., [Long], [String], [java.util.UUID])
 * @see ISlugGenerator
 * @see EnableSlug
 */
interface ISlugSupport<ID> {
    /**
     * The unique identifier of the entity.
     *
     * @return the entity ID
     */
    val id: ID

    /**
     * The current slug value of the entity.
     *
     * @return the slug
     */
    var slug: String?
}
