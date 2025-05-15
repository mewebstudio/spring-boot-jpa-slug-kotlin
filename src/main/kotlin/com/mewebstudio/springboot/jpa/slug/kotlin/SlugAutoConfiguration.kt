package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import kotlin.jvm.javaClass
import kotlin.jvm.kotlin
import kotlin.reflect.KClass

/**
 * Auto-configuration class for enabling slug generation in JPA entities.
 *
 * This configuration is activated automatically when a bean annotated with [EnableSlug]
 * is present in the application context and when JPA is available.
 *
 * It initializes and registers the [ISlugGenerator] implementation defined in the
 * `@EnableSlug(generator = ...)` annotation and sets up a [ISlugProvider]
 * for managing unique slug generation with collision handling.
 *
 * The configuration ensures slugs are unique per entity type by checking the database
 * using a [TransactionTemplate].
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
@AutoConfigureAfter(JpaBaseConfiguration::class)
open class SlugAutoConfiguration(
    private val context: ApplicationContext,
    private val transactionManager: PlatformTransactionManager
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
    open fun configureSlugSupport() {
        val beans = context.getBeansWithAnnotation(EnableSlug::class.java)
        if (beans.isEmpty()) {
            return
        }

        val generatorClass = resolveGeneratorClass()
        val generator = generatorClass.java.getDeclaredConstructor().newInstance()
        SlugUtil.setGenerator(generator)

        SlugRegistry.setSlugProvider(object : ISlugProvider {
            override fun generateSlug(entity: ISlugSupport<*>, slug: String): String? {
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
                    while (slugExists(entity.javaClass.kotlin, candidateSlug, entityId)) {
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
     * @return `true` if the slug already exists for another entity, `false` otherwise
     */
    fun slugExists(entityClass: KClass<*>, slug: String?, entityId: Any?): Boolean {
        val transactionTemplate = TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

        return transactionTemplate.execute { _ ->
            try {
                if (slug.isNullOrBlank()) {
                    return@execute false
                }

                val cb: CriteriaBuilder = entityManager.criteriaBuilder
                val query: CriteriaQuery<Long> = cb.createQuery(Long::class.java)
                val root: Root<*> = query.from(entityClass.java)

                if (entityId != null) {
                    query.select(cb.count(root))
                        .where(
                            cb.and(
                                cb.equal(cb.lower(root.get("slug")), slug.lowercase()),
                                cb.notEqual(root.get<Any>("id"), entityId)
                            )
                        )
                } else {
                    query.select(cb.count(root))
                        .where(cb.equal(cb.lower(root.get("slug")), slug.lowercase()))
                }

                entityManager.createQuery(query).singleResult > 0
            } catch (_: Exception) {
                false
            }
        } ?: false
    }

    /**
     * Resolves the [ISlugGenerator] implementation class from the [EnableSlug] annotation.
     *
     * @return the class of the slug generator to use
     * @throws SlugOperationException if no valid generator is defined
     */
    private fun resolveGeneratorClass(): KClass<out ISlugGenerator> {
        val beans = context.getBeansWithAnnotation(EnableSlug::class.java)
        for (bean in beans.values) {
            val enableSlug = bean.javaClass.getAnnotation(EnableSlug::class.java)
            if (enableSlug != null && enableSlug.generator != ISlugGenerator::class.java) {
                return enableSlug.generator
            }
        }

        throw SlugOperationException("No slug generator defined in @EnableSlug annotation.")
    }
}
