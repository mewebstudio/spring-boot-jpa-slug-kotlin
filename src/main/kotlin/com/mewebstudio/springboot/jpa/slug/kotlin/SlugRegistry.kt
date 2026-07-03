package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Registry for managing the global slug provider and cascade slug dependencies.
 */
object SlugRegistry {
    /**
     * Describes a dependent entity that needs its slug refreshed when an intermediate entity updates.
     *
     * @param fieldName The field name on the dependent entity that holds the reference to the intermediate entity.
     * @param entityName The JPQL entity name for the dependent entity.
     */
    data class CascadeDependency(
        val fieldName: String,
        val entityName: String
    ) {
        init {
            require(SAFE_IDENTIFIER.matches(fieldName)) { "Invalid field name for slug cascade: $fieldName" }
            require(SAFE_IDENTIFIER.matches(entityName)) { "Invalid entity name for slug cascade: $entityName" }
        }
    }

    private var slugProvider: ISlugProvider? = null

    /**
     * Key: intermediate entity class (e.g. Category)
     * Value: list of entities whose slugs depend on that intermediate entity
     */
    private val cascadeDependents = mutableMapOf<Class<*>, MutableList<CascadeDependency>>()

    /**
     * Sets the global slug provider to be used for generating slugs.
     *
     * @param provider The [ISlugProvider] implementation to set. If `null`, the slug provider will be unset.
     */
    fun setSlugProvider(provider: ISlugProvider?) {
        slugProvider = provider
    }

    /**
     * Retrieves the global slug provider.
     *
     * @return The [ISlugProvider] implementation currently set.
     * @throws SlugOperationException if the slug provider has not been set.
     */
    fun getSlugProvider(): ISlugProvider = slugProvider ?: throw SlugOperationException("ISlugProvider not set")

    /**
     * Registers a dependent entity that needs its slug refreshed when an intermediate entity updates.
     *
     * @param intermediateClass The class of the intermediate entity (e.g. Category).
     * @param dep The [CascadeDependency] describing the dependent entity and its slug field.
     * @throws IllegalArgumentException if the field name or entity name in [dep] is
     * invalid according to JPA identifier rules.
     */
    fun registerCascadeDependent(intermediateClass: Class<*>, dep: CascadeDependency) {
        cascadeDependents.getOrPut(intermediateClass) { mutableListOf() }.add(dep)
    }

    /**
     * Retrieves the list of dependent entities that need their slugs refreshed when the specified intermediate entity
     * updates.
     *
     * @param clazz The class of the intermediate entity (e.g. Category).
     * @return A list of [CascadeDependency] instances describing the dependent entities and their slug
     */
    fun getCascadeDependents(clazz: Class<*>): List<CascadeDependency> =
        cascadeDependents[clazz] ?: emptyList()

    /**
     * Clears all registered cascade dependents. This is primarily intended for testing purposes to reset the state of
     * the registry.
     */
    fun clearCascadeDependents() {
        cascadeDependents.clear()
    }
}
