package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.persistence.Column
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table

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

            // Extract composite unique constraint fields (e.g., locale for locale+slug uniqueness)
            val constraintFields = getCompositeUniqueConstraintFields(entity)

            val generatedSlug = provider.generateSlug(entity, slug, constraintFields)
            if (generatedSlug.isBlank()) {
                throw SlugOperationException("Generated slug is blank for base: $slug")
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

    /**
     * Extracts composite unique constraint field values from the entity.
     * For example, if there's a unique constraint on (locale, slug), this will return {"locale": "en-US"}.
     *
     * @param entity The entity to inspect.
     * @return Map of field names to their current values that are part of composite unique constraints with slug.
     */
    private fun getCompositeUniqueConstraintFields(entity: Any): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        try {
            val tableAnnotation = entity::class.java.getAnnotation(Table::class.java)

            if (tableAnnotation != null && tableAnnotation.uniqueConstraints.isNotEmpty()) {
                // Find unique constraints that include "slug"
                for (constraint in tableAnnotation.uniqueConstraints) {
                    val columnNames = constraint.columnNames

                    if (columnNames.contains("slug") && columnNames.size > 1) {
                        // This is a composite constraint with slug, extract other field values
                        for (columnName in columnNames) {
                            if (columnName != "slug") {
                                // Find the field with this column name
                                val fieldValue = findFieldValueByColumnName(entity, columnName)
                                if (fieldValue != null) {
                                    result[columnName] = fieldValue
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // If we can't determine composite constraints, return an empty map (fallback to old behavior)
        }

        return result
    }

    /**
     * Finds a field value by its column name from @Column annotation.
     *
     * @param entity The entity to inspect.
     * @param columnName The database column name.
     * @return The field value, or null if not found.
     */
    private fun findFieldValueByColumnName(entity: Any, columnName: String): Any? {
        for (field in entity::class.java.declaredFields) {
            field.isAccessible = true

            // Check @Column annotation
            val columnAnnotation = field.getAnnotation(Column::class.java)
            if (columnAnnotation != null && columnAnnotation.name == columnName) {
                return try {
                    field.get(entity)
                } catch (_: Exception) {
                    null
                }
            }

            // Fallback: if field name matches column name (snake_case vs. camelCase)
            if (field.name.equals(columnName, ignoreCase = true) ||
                toSnakeCase(field.name) == columnName
            ) {
                return try {
                    field.get(entity)
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    /**
     * Converts camelCase to snake_case.
     */
    private fun toSnakeCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }
}
