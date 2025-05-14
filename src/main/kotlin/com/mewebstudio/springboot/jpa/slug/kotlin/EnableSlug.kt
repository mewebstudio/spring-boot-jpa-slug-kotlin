package com.mewebstudio.springboot.jpa.slug.kotlin

import org.springframework.context.annotation.Import
import kotlin.reflect.KClass

/**
 * {@code @EnableSlug} is a configuration annotation that enables automatic slug generation
 * in a Spring Boot application.
 *
 * <p>This annotation imports {@link SlugAutoConfiguration} into the Spring application context
 * and optionally allows specifying a custom {@link ISlugGenerator} implementation.</p>
 *
 * <p>By default, {@link ISlugGenerator} is used as a placeholder. To provide custom slug generation
 * logic, supply your own implementation of the {@code ISlugGenerator} interface.</p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>{@code
 * @EnableSlug(generator = CustomSlugGenerator::class)
 * @SpringBootApplication
 * class MyApplication {
 *     fun main(args: Array<String>) {
 *         SpringApplication.run(MyApplication::class.java, *args)
 *     }
 * }
 * }</pre>
 *
 * @see ISlugGenerator
 * @see DefaultSlugGenerator
 * @see SlugAutoConfiguration
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(SlugAutoConfiguration::class)
annotation class EnableSlug(
    /**
     * Specifies the custom slug generator class to use.
     * If not provided, no custom slug generation logic is applied.
     *
     * @return the class implementing [ISlugGenerator]
     */
    val generator: KClass<out ISlugGenerator> = DefaultSlugGenerator::class
)
