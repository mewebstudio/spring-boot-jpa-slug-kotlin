package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Registry for managing the global [slugProvider].
 *
 * This class is used to register and retrieve the [slugProvider] used for generating slugs.
 * The [slugProvider] is intended to be set at the application startup and used throughout the lifecycle
 * of slug generation operations.
 */
object SlugRegistry {
    /**
     * The global [slugProvider] instance.
     *
     * This instance is used for generating slugs across the application.
     */
    private var slugProvider: ISlugProvider? = null

    /**
     * Sets the [slugProvider] to be used globally for slug generation.
     *
     * @param provider The [slugProvider] instance to set.
     * @throws IllegalArgumentException if the provider is null.
     */
    fun setSlugProvider(provider: ISlugProvider?) {
        slugProvider = provider
    }

    /**
     * Retrieves the currently set [slugProvider].
     *
     * @return The current [slugProvider].
     * @throws SlugOperationException if no [slugProvider] has been set.
     */
    fun getSlugProvider(): ISlugProvider = slugProvider ?: throw SlugOperationException("ISlugProvider not set")
}
