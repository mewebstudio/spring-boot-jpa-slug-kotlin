package com.mewebstudio.springboot.jpa.slug.kotlin

/**
 * Exception thrown when an error occurs during slug generation or slug-related operations.
 *
 * This exception is typically thrown when a slug operation fails, such as when a duplicate slug is found,
 * when the slug cannot be generated, or any other slug-related error occurs during the process.
 *
 * @see SlugUtil
 * @see SlugRegistry
 */
class SlugOperationException : RuntimeException {
    /**
     * Default constructor that initializes the exception with a default error message.
     */
    constructor() : super("An error occurred during slug operation.")

    /**
     * Constructor that initializes the exception with a specific error message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    constructor(message: String) : super(message)

    /**
     * Constructor that initializes the exception with a specific error message and a cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the cause of the exception (usually another exception that triggered this one)
     */
    constructor(message: String, cause: Throwable) : super(message, cause)
}
