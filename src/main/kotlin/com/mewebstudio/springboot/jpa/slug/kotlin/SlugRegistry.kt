package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Registry for managing the global [SlugProvider].
 *
 * This class is used to register and retrieve the [SlugProvider] used for generating slugs.
 * The [SlugProvider] is intended to be set at the application startup and used throughout the lifecycle
 * of slug generation operations.
 */
object SlugRegistry {
    /**
     * The global [SlugProvider] instance.
     *
     * This instance is used for generating slugs across the application.
     */
    private var slugProvider: SlugProvider? = null

    /**
     * Sets the [SlugProvider] to be used globally for slug generation.
     *
     * @param provider The [SlugProvider] instance to set.
     * @throws IllegalArgumentException if the provider is null.
     */
    fun setSlugProvider(provider: SlugProvider?) {
        slugProvider = provider
    }

    /**
     * Retrieves the currently set [SlugProvider].
     *
     * @return The current [SlugProvider].
     * @throws SlugOperationException if no [SlugProvider] has been set.
     */
    fun getSlugProvider(): SlugProvider = slugProvider ?: throw SlugOperationException("SlugProvider not set")
}
