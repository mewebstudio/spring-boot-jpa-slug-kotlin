package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.persistence.Column
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.lang.reflect.Field

/**
 * JPA entity listener for generating and updating slugs on entities that implement [ISlugSupport].
 *
 * Supports three slug source modes (evaluated in priority order):
 * 1. Class-level `@SlugField("f1", "f2")` — joins multiple field values (dot-notation supported).
 * 2. Field-level `@SlugField` — backward-compatible single-field mode.
 *
 * Slug is regenerated only when the slug source value changes.
 *
 * @see ISlugSupport
 * @see SlugField
 * @see SlugUtil
 * @see SlugRegistry
 * @see SlugOperationException
 */
class SlugListener {
    @PersistenceContext
    lateinit var entityManager: EntityManager

    /**
     * Handles the slug generation and update logic for entities that implement [ISlugSupport].
     * This method is invoked before persisting or updating an entity.
     *
     * @param entity The entity instance being processed.
     * @throws SlugOperationException If slug generation fails or the generated slug is invalid.
     */
    @PrePersist
    @PreUpdate
    fun handle(entity: Any) {
        if (entity !is ISlugSupport<*>) return

        try {
            val sourceValue = findSlugFieldValue(entity)
            if (sourceValue.isNullOrBlank()) return

            if (entity.slug != null && !isSlugSourceChanged(entity, sourceValue)) return

            val slug = SlugUtil.generate(sourceValue)
            if (slug.isNullOrBlank()) {
                throw SlugOperationException("Generated base slug is null or blank for value: $sourceValue")
            }

            val constraintFields = getCompositeUniqueConstraintFields(entity)
            val generatedSlug = SlugRegistry.getSlugProvider().generateSlug(entity, slug, constraintFields)
            if (generatedSlug.isBlank()) {
                throw SlugOperationException("Generated slug is blank for base: $slug")
            }

            entity.slug = generatedSlug
        } catch (e: Exception) {
            throw SlugOperationException("SlugListener failed: ${e.message}", e)
        }
    }

    /**
     * Checks if the slug source value has changed compared to the original entity in the database.
     *
     * @param entity         The entity instance being processed.
     * @param newSourceValue The new slug source value derived from the entity's annotated fields.
     * @return True if the slug source has changed, false otherwise.
     */
    private fun isSlugSourceChanged(entity: Any, newSourceValue: String): Boolean {
        return try {
            val originalEntity = entityManager.find(entity::class.java, (entity as ISlugSupport<*>).id)
            val originalValue = findSlugFieldValue(originalEntity)
            newSourceValue != originalValue
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Retrieves the values of fields that are part of composite unique constraints involving the "slug" column.
     *
     * @param entity The entity instance to inspect.
     * @return A map of column names to their corresponding values for fields that are part of
     * composite unique constraints with "slug". If no such constraints exist, returns an empty map.
     */
    private fun getCompositeUniqueConstraintFields(entity: Any): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        try {
            val tableAnnotation = entity::class.java.getAnnotation(Table::class.java) ?: return result
            for (constraint in tableAnnotation.uniqueConstraints) {
                val columnNames = constraint.columnNames
                if (columnNames.contains("slug") && columnNames.size > 1) {
                    for (columnName in columnNames) {
                        if (columnName != "slug") {
                            findFieldValueByColumnName(entity, columnName)?.let { result[columnName] = it }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /**
     * Finds the value of a field in an entity by its column name, considering both `@Column` annotations and naming
     * conventions.
     *
     * @param entity     The entity instance to inspect.
     * @param columnName The name of the column to find the corresponding field value for.
     * @return The value of the field corresponding to the given column name, or null if not found or inaccessible.
     */
    private fun findFieldValueByColumnName(entity: Any, columnName: String): Any? {
        for (field in entity::class.java.declaredFields) {
            field.isAccessible = true
            val columnAnnotation = field.getAnnotation(Column::class.java)
            if (columnAnnotation != null && columnAnnotation.name == columnName) {
                return try { field.get(entity) } catch (_: Exception) { null }
            }
            if (field.name.equals(columnName, ignoreCase = true) || toSnakeCase(field.name) == columnName) {
                return try { field.get(entity) } catch (_: Exception) { null }
            }
        }
        return null
    }

    /**
     * Converts a camelCase string to snake_case.
     *
     * @param str The camelCase string to convert.
     * @return The converted snake_case string.
     */
    private fun toSnakeCase(str: String): String =
        str.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    companion object {
        /**
         * Resolves the slug source string from an entity.
         *
         * Priority:
         * 1. Class-level `@SlugField(fields = [...])` — joins resolved field values.
         * 2. Field-level `@SlugField` (no fields) — returns that field's value.
         *
         * @param entity The entity from which to resolve the slug source.
         * @return The resolved slug source string, or null if not found.
         */
        fun findSlugFieldValue(entity: Any): String? {
            val classAnnotation = entity::class.java.getAnnotation(SlugField::class.java)
            if (classAnnotation != null && classAnnotation.fields.isNotEmpty()) {
                val parts = classAnnotation.fields
                    .mapNotNull { resolveFieldByPath(entity, it) }
                    .filter { it.isNotBlank() }
                return if (parts.isNotEmpty()) parts.joinToString(classAnnotation.separator) else null
            }

            // Backward compat: field-level @SlugField
            var clazz: Class<*>? = entity::class.java
            while (clazz != null && clazz != Any::class.java) {
                for (field in clazz.declaredFields) {
                    if (field.isAnnotationPresent(SlugField::class.java)) {
                        field.isAccessible = true
                        try {
                            val value = field.get(entity)
                            if (value is String && value.isNotBlank()) return value
                        } catch (e: IllegalAccessException) {
                            throw SlugOperationException("Unable to access @SlugField: ${field.name}", e)
                        }
                    }
                }
                clazz = clazz.superclass
            }
            return null
        }

        /**
         * Resolves a dot-notation field path (e.g. `"category.title"`) on an entity via getter/field reflection.
         *
         * - If a segment (getter or field) does not exist on the class, throws [SlugOperationException].
         * - If a segment exists but its runtime value is `null`, returns `null` so the caller can skip this path.
         *
         * @param entity The root entity to start traversal from.
         * @param path   Dot-notation path such as `"title"` or `"category.name"`.
         * @return The resolved string value, or `null` if an intermediate value is `null` at runtime.
         * @throws SlugOperationException if any segment is not found in the class hierarchy.
         */
        fun resolveFieldByPath(entity: Any, path: String): String? {
            var current: Any = entity
            for (part in path.split(".")) {
                current = resolveSegment(current, part, path) ?: return null
            }
            return current as? String
        }

        /**
         * Resolves a single path segment on [obj].
         *
         * Tries the Java-style getter first (safe for Hibernate proxies), then falls back to direct field access.
         * Returns `null` when the property exists but its value is `null` at runtime.
         * Throws [SlugOperationException] when neither a getter nor a field with [name] can be found.
         */
        private fun resolveSegment(obj: Any, name: String, fullPath: String): Any? {
            val getterName = "get${name.replaceFirstChar { it.uppercase() }}"

            // Try getter first — triggers lazy-load initialization on Hibernate proxies
            val getter = try {
                obj::class.java.getMethod(getterName)
            } catch (_: NoSuchMethodException) {
                null
            }

            if (getter != null) {
                return try {
                    getter.invoke(obj)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw SlugOperationException(
                        "@SlugField path '$fullPath': '$getterName' threw an exception on ${obj::class.java.simpleName}: ${e.cause?.message}",
                        e
                    )
                } catch (e: Exception) {
                    throw SlugOperationException(
                        "@SlugField path '$fullPath': error invoking '$getterName' on ${obj::class.java.simpleName}: ${e.message}",
                        e
                    )
                }
            }

            // Fall back to direct field access
            val field = findFieldInHierarchy(obj::class.java, name)
                ?: throw SlugOperationException(
                    "@SlugField path '$fullPath': property '$name' not found on ${obj::class.java.simpleName}"
                )

            field.isAccessible = true
            return try {
                field.get(obj)
            } catch (e: IllegalAccessException) {
                throw SlugOperationException(
                    "@SlugField path '$fullPath': unable to access field '$name' on ${obj::class.java.simpleName}: ${e.message}",
                    e
                )
            }
        }

        /**
         * Finds a field with the given name in the class hierarchy of the specified class.
         *
         * @param clazz The class to start searching from.
         * @param name  The name of the field to find.
         * @return The [Field] if found, or null if not found in the class hierarchy.
         */
        internal fun findFieldInHierarchy(clazz: Class<*>, name: String): Field? {
            var c: Class<*>? = clazz
            while (c != null && c != Any::class.java) {
                try { return c.getDeclaredField(name) } catch (_: NoSuchFieldException) { }
                c = c.superclass
            }
            return null
        }
    }
}
