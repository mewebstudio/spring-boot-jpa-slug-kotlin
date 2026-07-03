package com.mewebstudio.springboot.jpa.slug.kotlin

import jakarta.persistence.EntityManager
import jakarta.persistence.FlushModeType
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener

/**
 * Hibernate [PostUpdateEventListener] that cascades slug regeneration to dependent entities.
 *
 * When an entity is updated (e.g. a `Category`), this listener finds all [ISlugSupport] entities
 * that reference it via a dot-notation `@SlugField` path (e.g. `@SlugField("category.title", "title")`)
 * and updates their slugs via a JPQL bulk UPDATE — which does not re-trigger JPA lifecycle callbacks.
 *
 * Registered programmatically by [SlugAutoConfiguration] — no `@EntityListeners` needed on the entity.
 */
class SlugCascadeListener : PostUpdateEventListener {
    @Suppress("SqlSourceToSinkFlow")
    override fun onPostUpdate(event: PostUpdateEvent) {
        val updatedEntity = event.entity
        val dependents = SlugRegistry.getCascadeDependents(updatedEntity::class.java)
        if (dependents.isEmpty()) return

        @Suppress("UNCHECKED_CAST")
        val em = event.session as EntityManager

        for (dep in dependents) {
            // Both values are JPA identifiers validated at registration time (alphanumeric + underscore).
            // String interpolation into JPQL is intentional — parameterized entity/field names are not
            // supported by JPA, and these values are derived from class metadata, never from user input.
            if (!SAFE_IDENTIFIER.matches(dep.entityName) || !SAFE_IDENTIFIER.matches(dep.fieldName)) continue

            try {
                val selectJpql = "SELECT e FROM ${dep.entityName} e WHERE e.${dep.fieldName} = :ref"
                val results = em.createQuery(selectJpql)
                    .setParameter("ref", updatedEntity)
                    .apply { flushMode = FlushModeType.COMMIT }
                    .resultList

                for (obj in results) {
                    val dependent = obj as? ISlugSupport<*> ?: continue
                    val sourceValue = SlugListener.findSlugFieldValue(dependent) ?: continue
                    val base = SlugUtil.generate(sourceValue) ?: continue
                    val newSlug = SlugRegistry.getSlugProvider().generateSlug(dependent, base)

                    val updateJpql = "UPDATE ${dep.entityName} e SET e.slug = :slug WHERE e.id = :id"
                    em.createQuery(updateJpql)
                        .setParameter("slug", newSlug)
                        .setParameter("id", dependent.id)
                        .executeUpdate()
                }
            } catch (_: Exception) {
                // Cascade failure must not block the original update
            }
        }
    }
}
