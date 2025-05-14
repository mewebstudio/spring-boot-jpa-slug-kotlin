package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Utility class for handling slug generation operations.
 *
 * This class provides static methods for setting and getting a global [ISlugGenerator] and for generating slugs.
 * The [ISlugGenerator] is used to transform input strings into slugs based on specific rules.
 */
object SlugUtil {
    /**
     * The global [ISlugGenerator] instance.
     *
     * This instance is used for generating slugs across the application.
     */
    private var generator: ISlugGenerator? = null

    /**
     * Sets the [ISlugGenerator] to be used globally for slug generation.
     *
     * @param slugGenerator The [ISlugGenerator] instance to set.
     * @throws SlugOperationException if the provided generator is null.
     */
    fun setGenerator(slugGenerator: ISlugGenerator?) {
        generator = slugGenerator ?: throw SlugOperationException("SlugGenerator cannot be null")
    }

    /**
     * Retrieves the currently set [ISlugGenerator].
     *
     * @return The current [ISlugGenerator].
     * @throws SlugOperationException if no [ISlugGenerator] has been set.
     */
    fun getGenerator(): ISlugGenerator = generator ?: throw SlugOperationException("SlugGenerator not set")

    /**
     * Converts the input string into a slug using the globally set [ISlugGenerator].
     *
     * The generated slug will follow the rules defined by the [ISlugGenerator] implementation.
     *
     * @param input The input string to be converted into a slug.
     * @return The generated slug.
     */
    fun generate(input: String?): String? = getGenerator().generate(input)
}
