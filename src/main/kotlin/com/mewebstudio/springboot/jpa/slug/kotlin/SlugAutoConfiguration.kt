package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.annotation.PostConstruct
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import jakarta.persistence.PersistenceUnit
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

/**
 * Autoconfiguration class for enabling slug generation in JPA entities.
 *
 * Activated when a bean annotated with [EnableSlug] is present and JPA is on the classpath.
 *
 * Responsibilities:
 * - Registers the [ISlugGenerator] and [ISlugProvider] for unique slug generation.
 * - Scans JPA entity metadata to build a cascade dependency map for dot-notation `@SlugField` paths.
 * - Registers [SlugCascadeListener] with Hibernate so that updating an intermediate entity
 *   (e.g. `Category`) automatically refreshes the slug of dependent entities (e.g. `Article`).
 *
 * @see EnableSlug
 * @see ISlugGenerator
 * @see ISlugSupport
 * @see SlugRegistry
 */
@Configuration
@ConditionalOnClass(EntityManager::class)
class SlugAutoConfiguration(
    private val context: ApplicationContext
) {
    companion object {
        private const val MAX_ATTEMPTS = 100
    }

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @PersistenceUnit
    private lateinit var entityManagerFactory: EntityManagerFactory

    @PostConstruct
    @Transactional
    fun configureSlugSupport() {
        val beans = context.getBeansWithAnnotation<EnableSlug>()
        if (beans.isEmpty()) return

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
                    if (slug.isBlank()) throw SlugOperationException("Base slug cannot be blank")

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

        buildCascadeMap()
        registerCascadeListener()
    }

    /**
     * Checks whether a given slug already exists in the database for the specified entity type.
     *
     * @param entityClass The entity class to check.
     * @param slug The slug to check for existence.
     * @param entityId The ID of the entity to exclude from the check (useful for updates).
     * @param compositeConstraintFields A map of additional field names and values to apply as constraints
     * when checking for slug existence (e.g., for composite uniqueness).
     * @return True if the slug exists for another entity, false otherwise.
     */
    fun slugExists(
        entityClass: KClass<*>,
        slug: String?,
        entityId: Any?,
        compositeConstraintFields: Map<String, Any?> = emptyMap()
    ): Boolean {
        return try {
            if (slug.isNullOrBlank()) return false

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
     * Scans the JPA metamodel for [ISlugSupport] entities that have class-level `@SlugField`
     * with dot-notation paths and builds a reverse cascade dependency map in [SlugRegistry].
     *
     * Example: `@SlugField("category.title", "title")` on `Article` registers:
     * `Category → CascadeDependency(Article, "category")`
     */
    private fun buildCascadeMap() {
        SlugRegistry.clearCascadeDependents()
        val entityClasses = entityManagerFactory.metamodel.entities.map { it.javaType }

        for (entityClass in entityClasses) {
            if (!ISlugSupport::class.java.isAssignableFrom(entityClass)) continue

            val classAnnotation = entityClass.getAnnotation(SlugField::class.java) ?: continue

            for (fieldPath in classAnnotation.fields) {
                if (!fieldPath.contains(".")) continue

                val firstSegment = fieldPath.substringBefore(".")
                val intermediateType = try {
                    val field = SlugListener.findFieldInHierarchy(entityClass, firstSegment) ?: continue
                    field.type
                } catch (_: Exception) {
                    continue
                }

                // Only register cascade for actual JPA entities, not embeddable
                if (entityClasses.none { it == intermediateType }) continue

                val dep = SlugRegistry.CascadeDependency(
                    fieldName = firstSegment,
                    entityName = resolveEntityName(entityClass)
                )
                SlugRegistry.registerCascadeDependent(intermediateType, dep)
            }
        }
    }

    /**
     * Registers [SlugCascadeListener] with Hibernate's [EventListenerRegistry] so it fires
     * on every entity PostUpdate without requiring `@EntityListeners` on each entity class.
     */
    private fun registerCascadeListener() {
        try {
            val sessionFactory = entityManagerFactory
                .unwrap(SessionFactoryImplementor::class.java)
            val registry = sessionFactory.serviceRegistry
                .getService(EventListenerRegistry::class.java)
            val transactionManager = context.getBean(PlatformTransactionManager::class.java)
            registry?.appendListeners(EventType.POST_UPDATE, SlugCascadeListener(transactionManager, entityManager))
        } catch (_: Exception) {
            // If Hibernate is not the JPA provider, cascade slug updates are silently skipped
        }
    }

    /**
     * Returns the JPQL entity name for a class — respects `@Entity(name = "...")` if set.
     *
     * @param clazz The entity class to resolve.
     * @return The JPQL entity name.
     */
    private fun resolveEntityName(clazz: Class<*>): String {
        val entityAnnotation = clazz.getAnnotation(Entity::class.java)
        return if (entityAnnotation?.name?.isNotBlank() == true) entityAnnotation.name else clazz.simpleName
    }

    /**
     * Finds the field name in the entity class that corresponds to the given column name.
     *
     * @param entityClass The entity class to search.
     * @param columnName The column name to match.
     * @return The field name if found, or null if not found.
     */
    private fun findFieldNameByColumnName(entityClass: Class<*>, columnName: String): String? {
        for (field in entityClass.declaredFields) {
            val columnAnnotation = field.getAnnotation(Column::class.java)
            if (columnAnnotation != null && columnAnnotation.name == columnName) return field.name
            if (field.name.equals(columnName, ignoreCase = true) || toSnakeCase(field.name) == columnName) {
                return field.name
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

    /**
     * Resolves the [ISlugGenerator] class from the [EnableSlug] annotation on any bean in the context.
     *
     * @return The [KClass] of the slug generator.
     * @throws SlugOperationException if no slug generator is defined in [EnableSlug].
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
