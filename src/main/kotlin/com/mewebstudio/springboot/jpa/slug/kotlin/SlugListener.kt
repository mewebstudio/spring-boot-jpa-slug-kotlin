package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate

/**
 * JPA entity listener for generating and updating slugs on entities
 * that implement the [ISlugSupport] interface.
 *
 * Uses the field annotated with [SlugField] as the source for slug generation.
 * Slug will only be updated if the source field has changed or is initially empty.
 */
class SlugListener {
    /**
     * The entity manager used for database operations.
     * This is injected by the Spring container.
     */
    @PersistenceContext
    lateinit var entityManager: EntityManager

    /**
     * JPA callback method invoked before persist or update operations.
     * Handles slug generation and assignment for entities that support slugs.
     *
     * @param entity The entity object being persisted or updated.
     */
    @PrePersist
    @PreUpdate
    fun handle(entity: Any) {
        if (entity !is ISlugSupport<*>) {
            return
        }

        try {
            val sourceValue = findSlugFieldValue(entity)
            if (sourceValue.isNullOrBlank()) {
                return
            }

            if (entity.slug != null && !isSlugSourceChanged(entity, sourceValue)) {
                return
            }

            val slug = SlugUtil.generate(sourceValue)
            if (slug.isNullOrBlank()) {
                throw SlugOperationException("Generated base slug is null or blank for value: $sourceValue")
            }

            val provider = SlugRegistry.getSlugProvider()

            val generatedSlug = provider.generateSlug(entity, slug)
            if (generatedSlug.isNullOrBlank()) {
                throw SlugOperationException("Generated slug is null or blank for base: $slug")
            }

            entity.slug = generatedSlug
        } catch (e: Exception) {
            throw SlugOperationException("SlugListener failed: ${e.message}", e)
        }
    }

    /**
     * Checks whether the slug source field value has changed compared to the original persisted entity.
     *
     * @param entity The current entity instance.
     * @param newSourceValue The current value of the slug source field.
     * @return true if the source value has changed, false otherwise.
     */
    private fun isSlugSourceChanged(entity: Any, newSourceValue: String): Boolean {
        return try {
            val originalEntity = entityManager.find(entity::class.java, (entity as ISlugSupport<*>).id)
            for (field in originalEntity::class.java.declaredFields) {
                if (field.isAnnotationPresent(SlugField::class.java)) {
                    field.isAccessible = true
                    val originalValue = field.get(originalEntity)
                    return newSourceValue != originalValue
                }
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Finds the value of the field annotated with [SlugField] on the given entity.
     *
     * @param entity The entity to inspect.
     * @return The string value of the slug source field, or null if not found or empty.
     */
    private fun findSlugFieldValue(entity: Any): String? {
        for (field in entity::class.java.declaredFields) {
            if (field.isAnnotationPresent(SlugField::class.java)) {
                field.isAccessible = true
                try {
                    val value = field.get(entity)
                    if (value is String && value.isNotBlank()) {
                        return value
                    }
                } catch (e: IllegalAccessException) {
                    throw SlugOperationException("Unable to access @SlugField: ${field.name}", e)
                }
            }
        }

        return null
    }
}
