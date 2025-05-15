package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Registry for managing the global [ISlugProvider].
 *
 * This class is used to register and retrieve the [ISlugProvider] used for generating slugs.
 * The [ISlugProvider] is intended to be set at the application startup and used throughout the lifecycle
 * of slug generation operations.
 */
object SlugRegistry {
    /**
     * The global [ISlugProvider] instance.
     *
     * This instance is used for generating slugs across the application.
     */
    private var ISlugProvider: ISlugProvider? = null

    /**
     * Sets the [ISlugProvider] to be used globally for slug generation.
     *
     * @param provider The [ISlugProvider] instance to set.
     * @throws IllegalArgumentException if the provider is null.
     */
    fun setSlugProvider(provider: ISlugProvider?) {
        ISlugProvider = provider
    }

    /**
     * Retrieves the currently set [ISlugProvider].
     *
     * @return The current [ISlugProvider].
     * @throws SlugOperationException if no [ISlugProvider] has been set.
     */
    fun getSlugProvider(): ISlugProvider = ISlugProvider ?: throw SlugOperationException("ISlugProvider not set")
}
