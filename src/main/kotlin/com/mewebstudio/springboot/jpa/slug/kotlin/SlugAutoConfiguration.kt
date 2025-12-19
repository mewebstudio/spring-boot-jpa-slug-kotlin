package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

/**
 * Autoconfiguration class for enabling slug generation in JPA entities.
 *
 * This configuration is activated automatically when a bean annotated with [EnableSlug]
 * is present in the application context and when JPA is available.
 *
 * It initializes and registers the [ISlugGenerator] implementation defined in the
 * `@EnableSlug(generator = ...)` annotation and sets up a [ISlugProvider]
 * for managing unique slug generation with collision handling.
 *
 * The configuration ensures slugs are unique per entity type by checking the database
 * using the current EntityManager session.
 *
 * Slug creation logic is executed during application startup, leveraging a [PostConstruct]
 * lifecycle method.
 *
 * @see EnableSlug
 * @see ISlugGenerator
 * @see ISlugSupport
 * @see SlugRegistry
 * @see SlugUtil
 */
@Configuration
@ConditionalOnClass(EntityManager::class)
class SlugAutoConfiguration(
    private val context: ApplicationContext
) {
    companion object {
        /**
         * Maximum number of attempts to generate a unique slug.
         * If exceeded, an exception is thrown.
         */
        private const val MAX_ATTEMPTS = 100
    }

    /**
     * The entity manager used for database operations.
     * This is injected by the Spring container.
     */
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    /**
     * Initializes slug generation support after the application context is loaded.
     *
     * This method scans for beans annotated with [EnableSlug], retrieves the
     * configured [ISlugGenerator], and registers a [ISlugProvider]
     * responsible for generating unique slugs for entities implementing [ISlugSupport].
     *
     * @throws Exception if the slug generator cannot be instantiated
     */
    @PostConstruct
    @Transactional
    fun configureSlugSupport() {
        val beans = context.getBeansWithAnnotation<EnableSlug>()
        if (beans.isEmpty()) {
            return
        }

        val generatorClass = resolveGeneratorClass()
        val generator = generatorClass.java.getDeclaredConstructor().newInstance()
        SlugUtil.setGenerator(generator)

        SlugRegistry.setSlugProvider(object : ISlugProvider {
            override fun generateSlug(
                entity: ISlugSupport<*>,
                slug: String,
                compositeConstraintFields: Map<String, Any?>
            ): String {
                try {
                    if (slug.isBlank()) {
                        throw SlugOperationException("Base slug cannot be blank")
                    }

                    val base = SlugUtil.generate(slug)
                        ?: throw SlugOperationException("Slugified base is null or blank: $slug")

                    var candidateSlug = base
                    var i = 2

                    val entityId = entity.id

                    var attempt = 0
                    while (slugExists(entity.javaClass.kotlin, candidateSlug, entityId, compositeConstraintFields)) {
                        if (attempt++ >= MAX_ATTEMPTS) {
                            throw SlugOperationException(
                                "Unable to generate unique slug for: $base, after $MAX_ATTEMPTS attempts"
                            )
                        }
                        candidateSlug = "$base-$i"
                        i++
                    }

                    return candidateSlug
                } catch (e: Exception) {
                    throw SlugOperationException("ISlugProvider failed: ${e.message}", e)
                }
            }
        })
    }

    /**
     * Checks whether a given slug already exists in the database for the specified entity type.
     *
     * @param entityClass the entity class to check for slug collisions
     * @param slug the slug candidate to test
     * @param entityId the ID of the current entity (to exclude itself during updates)
     * @param compositeConstraintFields Map of column names to values that are part of composite unique constraints.
     *                                   For example, if there's a unique constraint on (locale, slug), this map will contain
     *                                   {"locale": "en-US"}. The slug existence check will be scoped to these values.
     * @return `true` if the slug already exists for another entity, `false` otherwise
     */
    fun slugExists(
        entityClass: KClass<*>,
        slug: String?,
        entityId: Any?,
        compositeConstraintFields: Map<String, Any?> = emptyMap()
    ): Boolean {
        return try {
            if (slug.isNullOrBlank()) {
                return false
            }

            val cb: CriteriaBuilder = entityManager.criteriaBuilder
            val query: CriteriaQuery<Long> = cb.createQuery(Long::class.java)
            val root: Root<*> = query.from(entityClass.java)

            // Build predicates list
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            // Add slug equality predicate
            predicates.add(cb.equal(cb.lower(root.get("slug")), slug.lowercase()))

            // Add entity ID exclusion predicate (for updates)
            if (entityId != null) {
                predicates.add(cb.notEqual(root.get<Any>("id"), entityId))
            }

            // Add composite constraint field predicates
            for ((columnName, value) in compositeConstraintFields) {
                if (value != null) {
                    // Find the field name by column name (handle both snake_case and camelCase)
                    val fieldName = findFieldNameByColumnName(entityClass.java, columnName)
                    if (fieldName != null) {
                        predicates.add(cb.equal(root.get<Any>(fieldName), value))
                    }
                }
            }

            query.select(cb.count(root)).where(*predicates.toTypedArray())

            val count = try {
                // Disable an auto-flush to prevent "detached entity passed to persist" errors
                // This happens when the entity has ID but is not yet managed
                val typedQuery = entityManager.createQuery(query)
                typedQuery.flushMode = jakarta.persistence.FlushModeType.COMMIT
                typedQuery.singleResult
            } catch (_: Exception) {
                // Query failed, return 0 to indicate no existing slug found
                0L
            }
            count > 0
        } catch (_: Exception) {
            // Any other error, assume slug doesn't exist to allow the operation to proceed
            false
        }
    }

    /**
     * Finds the field name in an entity class by its column name.
     *
     * @param entityClass The entity class to inspect
     * @param columnName The database column name
     * @return The field name, or null if not found
     */
    private fun findFieldNameByColumnName(entityClass: Class<*>, columnName: String): String? {
        for (field in entityClass.declaredFields) {
            // Check @Column annotation
            val columnAnnotation = field.getAnnotation(jakarta.persistence.Column::class.java)
            if (columnAnnotation != null && columnAnnotation.name == columnName) {
                return field.name
            }

            // Fallback: if field name matches column name (handling snake_case conversion)
            if (field.name.equals(columnName, ignoreCase = true) ||
                toSnakeCase(field.name) == columnName
            ) {
                return field.name
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

    /**
     * Resolves the [ISlugGenerator] implementation class from the [EnableSlug] annotation.
     *
     * @return the class of the slug generator to use
     * @throws SlugOperationException if no valid generator is defined
     */
    private fun resolveGeneratorClass(): KClass<out ISlugGenerator> {
        val beans = context.getBeansWithAnnotation<EnableSlug>()
        for (bean in beans.values) {
            val enableSlug = bean.javaClass.getAnnotation(EnableSlug::class.java)
            if (enableSlug != null && enableSlug.generator != ISlugGenerator::class) {
                return enableSlug.generator
            }
        }

        throw SlugOperationException("No slug generator defined in @EnableSlug annotation.")
    }
}
