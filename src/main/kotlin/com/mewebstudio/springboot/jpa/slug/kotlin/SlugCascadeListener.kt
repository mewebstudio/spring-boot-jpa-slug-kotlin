package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.persistence.EntityManager
import jakarta.persistence.FlushModeType
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Hibernate [PostUpdateEventListener] that cascades slug regeneration to dependent entities.
 *
 * When an entity is updated (e.g. a `Category`), this listener finds all [ISlugSupport] entities
 * that reference it via a dot-notation `@SlugField` path (e.g. `@SlugField(fields = ["category.title", "title"])`)
 * and refreshes their slugs via a JPQL bulk `UPDATE`.
 *
 * The refresh is intentionally **deferred until after the surrounding transaction commits** and then
 * runs in a fresh transaction. `onPostUpdate` fires while Hibernate is executing the flush action
 * queue; issuing DML there would mutate that queue mid-iteration and throw
 * [java.util.ConcurrentModificationException] at commit. Running it post-commit — via the injected
 * [EntityManager] and [PlatformTransactionManager] rather than the event's own session — also keeps
 * the listener free of the Hibernate `PostUpdateEvent.getSession()` API whose return type differs
 * between Hibernate 6 and 7.
 *
 * Registered programmatically by [SlugAutoConfiguration] — no `@EntityListeners` needed on the entity.
 */
class SlugCascadeListener(
    private val transactionManager: PlatformTransactionManager,
    private val entityManager: EntityManager
) : PostUpdateEventListener {
    override fun onPostUpdate(event: PostUpdateEvent) {
        val updatedEntity = event.entity
        val dependents = SlugRegistry.getCascadeDependents(updatedEntity::class.java)
        if (dependents.isEmpty()) return

        // Cannot defer without an active synchronization; skip rather than risk running DML now.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    refreshDependentSlugs(updatedEntity, dependents)
                }
            }
        })
    }

    /**
     * Refresh the slugs of the entities that depend on [updatedEntity], in a fresh transaction.
     * Runs after the original transaction has committed, so it is clear of the flush action queue
     * and the parent's row locks have been released.
     */
    private fun refreshDependentSlugs(updatedEntity: Any, dependents: List<SlugRegistry.CascadeDependency>) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            for ((fieldName, entityName) in dependents) {
                // Both values are JPA identifiers validated at registration time (alphanumeric + underscore).
                // String interpolation into JPQL is intentional — parameterized entity/field names are not
                // supported by JPA, and these values are derived from class metadata, never from user input.
                if (!SAFE_IDENTIFIER.matches(entityName) || !SAFE_IDENTIFIER.matches(fieldName)) continue

                try {
                    val selectJpql = "SELECT e FROM $entityName e WHERE e.$fieldName = :ref"
                    val results = entityManager.createQuery(selectJpql)
                        .setParameter("ref", updatedEntity)
                        .apply { flushMode = FlushModeType.COMMIT }
                        .resultList

                    for (obj in results) {
                        val dependent = obj as? ISlugSupport<*> ?: continue
                        val sourceValue = SlugListener.findSlugFieldValue(dependent) ?: continue
                        val base = SlugUtil.generate(sourceValue) ?: continue
                        val newSlug = SlugRegistry.getSlugProvider().generateSlug(dependent, base)

                        entityManager.createQuery("UPDATE $entityName e SET e.slug = :slug WHERE e.id = :id")
                            .setParameter("slug", newSlug)
                            .setParameter("id", dependent.id)
                            .executeUpdate()
                    }
                } catch (_: Exception) {
                    // Cascade failure must not affect the (already committed) original update
                }
            }
        }
    }
}
