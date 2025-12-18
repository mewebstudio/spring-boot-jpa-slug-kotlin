package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Interface to be implemented by custom slug generators.
 *
 * Provides logic for generating unique slugs based on an entity and a base slug string.
 */
interface ISlugProvider {
    /**
     * Generates a slug for the given entity using the provided base slug.
     *
     * Implementations may apply additional rules such as uniqueness checks, suffixes, or normalization.
     *
     * @param entity The entity for which the slug is being generated.
     * @param slug   The base slug string derived from the entity's annotated [SlugField].
     * @param compositeConstraintFields Map of column names to values that are part of composite unique constraints with slug.
     *                                   For example, if there's a unique constraint on (locale, slug), this map will contain
     *                                   {"locale": "en-US"}. This allows the slug generator to check uniqueness within the
     *                                   scope of these constraint fields.
     * @return A valid and unique slug string to be assigned to the entity.
     */
    fun generateSlug(entity: ISlugSupport<*>, slug: String, compositeConstraintFields: Map<String, Any?> = emptyMap()): String
}
