# Slug Generator for Spring Boot (Kotlin)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/com.mewebstudio/spring-boot-jpa-slug-kotlin)](https://central.sonatype.com/artifact/com.mewebstudio/spring-boot-jpa-slug-kotlin)
[![Javadoc](https://javadoc.io/badge2/com.mewebstudio/spring-boot-jpa-slug-kotlin/javadoc.svg)](https://javadoc.io/doc/com.mewebstudio/spring-boot-jpa-slug-kotlin)

A simple and customizable slug generation solution for Spring Boot applications, designed to easily create and manage slugs for entities. This package integrates with JPA entities and provides a flexible way to generate unique slugs for your models.

---

## ✅ Features

- **Customizable Slug Generation**: Provides an interface for defining custom slug generation logic using `ISlugGenerator`.
- **Automatic Slug Assignment**: Automatically generates and assigns slugs to entities upon creation or update.
- **Unique Slug Enforcement**: Ensures that slugs are unique across entities, retrying with suffixes if needed.
- **Multi-Field Slug Sources**: Combine multiple fields into a single slug using `@SlugField("title", "description")` at class level.
- **Dot-Notation for Related Entities**: Reference fields from related entities with dot-notation: `@SlugField("category.title", "title")`.
- **Cascade Slug Updates**: When a related entity (e.g. `Category`) is updated, slugs of dependent entities (e.g. `Article`) are automatically refreshed — no `@EntityListeners` needed on the related entity.
- **Composite Unique Constraint Support**: Automatically detects and respects composite unique constraints (e.g., `locale + slug`) for multi-locale entities.
- **Integration with Spring Boot**: Easily integrates with Spring Boot using `@EnableSlug` and `SlugRegistry`.

## 📥 Installation

#### for Maven users

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.mewebstudio</groupId>
  <artifactId>spring-boot-jpa-slug-kotlin</artifactId>
  <version>0.1.6</version>
</dependency>
```

#### for Gradle users
```groovy
implementation 'com.mewebstudio:spring-boot-jpa-slug-kotlin:0.1.6'
```

## 🚀 Usage

### 1. Add the `@EnableSlug` annotation to your Spring Boot application class:

```kotlin
import com.mewebstudio.springboot.jpa.slug.kotlin.EnableSlug

@SpringBootApplication
@EnableSlug // Specify your custom generator if needed: @EnableSlug(generator = CustomSlugGenerator::class) 
class SlugApplication

fun main(args: Array<String>) {
    runApplication<SlugApplication>(*args)
}
```

### 2. Implement a Custom Slug Generator (Optional)

```kotlin
import com.mewebstudio.springboot.jpa.slug.kotlin.ISlugGenerator

class CustomSlugGenerator : ISlugGenerator {
    override fun generate(input: String?): String? = input?.lowercase()?.replace(Regex("[^a-z0-9\\s-]"), "")
}
```

### 3. Add Slug Field to Your Entity

#### Single field (field-level annotation — backward compatible)

```kotlin
import com.mewebstudio.springboot.jpa.slug.kotlin.ISlugSupport
import com.mewebstudio.springboot.jpa.slug.kotlin.SlugField
import com.mewebstudio.springboot.jpa.slug.kotlin.SlugListener

@Entity
@EntityListeners(SlugListener::class)
class Category : AbstractBaseEntity(), ISlugSupport<String> {
    @SlugField
    var name: String? = null

    @Column(name = "slug", unique = true, nullable = false)
    override var slug: String? = null
}
```

#### Multiple fields (class-level annotation)

Place `@SlugField` on the class and list the field names to combine. Values are joined with a space (configurable via `separator`) before slug generation.

```kotlin
@Entity
@EntityListeners(SlugListener::class)
@SlugField("title", "description")
class Article : AbstractBaseEntity(), ISlugSupport<Long> {
    var title: String? = null
    var description: String? = null

    @Column(name = "slug", unique = true, nullable = false)
    override var slug: String? = null
}
// title="Hello World", description="A great article"
// → slug = "hello-world-a-great-article"
```

Custom separator example:

```kotlin
@SlugField("brand", "model", separator = " - ")
```

#### Dot-notation for related entity fields

Reference a field on a related entity using dot-notation. The library automatically navigates the object graph at persist/update time.

```kotlin
@Entity
@EntityListeners(SlugListener::class)
@SlugField("category.name", "title")
class Article : AbstractBaseEntity(), ISlugSupport<Long> {
    @ManyToOne
    var category: Category? = null

    var title: String? = null

    @Column(name = "slug", unique = true, nullable = false)
    override var slug: String? = null
}
// category.name="Tech", title="AI News"
// → slug = "tech-ai-news"
```

> **Cascade updates:** When a `Category` is updated (e.g. its `name` changes), the library automatically re-generates the slugs of all `Article` entities that reference that category — without any additional `@EntityListeners` on `Category`.  
> Deep traversal is supported: `"a.b.c"` resolves `entity.a.b.c` via getter/field reflection.

### 4. Using Composite Unique Constraints (Optional)

For multi-locale entities, you can define composite unique constraints on `(locale, slug)`. The slug generator will automatically detect and respect these constraints:

```kotlin
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["locale", "slug"])
    ]
)
@EntityListeners(SlugListener::class)
class ProductTranslation : AbstractBaseEntity(), ISlugSupport<String> {
    @Column(name = "locale")
    var locale: String = ""

    @SlugField
    @Column(name = "title")
    var title: String = ""

    @Column(name = "slug")
    override var slug: String? = null

    // Same slug can exist in different locales; uniqueness is enforced per locale
}
```

### 5. Handling Slug Generation

Slugs are automatically generated when entities are created or updated, and they can be customized using the logic provided in the `ISlugProvider`. The system will also ensure uniqueness by checking against the existing slugs in the database.

---

## 📘 API Overview

### `@SlugField`

Marks the slug source on a field or class.

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SlugField(
    vararg val fields: String,   // empty = field-level (backward compat)
    val separator: String = " "  // joined before slug generation
)
```

| Usage | Behavior |
|---|---|
| `@SlugField` on a field | Uses that field's value as slug source (backward compatible) |
| `@SlugField("f1", "f2")` on a class | Joins `f1` and `f2` values with `separator` |
| `@SlugField("rel.field", "f2")` on a class | Resolves `rel.field` via object graph traversal |
| Class-level + field-level both present | Class-level takes priority |

### `EnableSlug`

Annotation to enable slug generation in your Spring Boot application. You can specify a custom slug generator by providing the `generator` attribute.

```kotlin
annotation class EnableSlug(
    val generator: KClass<out ISlugGenerator> = DefaultSlugGenerator::class
)
```

### `ISlugSupport`

Interface for entities that support slug generation. Implement this interface in your entity classes to enable slug functionality.

```kotlin
interface ISlugSupport<ID> {
    val id: ID
    var slug: String?
}
```

### `ISlugGenerator`

The interface for implementing custom slug generators.
```kotlin
interface ISlugGenerator {
    fun generate(input: String?): String?
}
```

### `SlugUtil`

Utility class for managing the global slug generator and slug creation.
```kotlin
object SlugUtil {
    fun setGenerator(slugGenerator: ISlugGenerator?)
    fun getGenerator(): ISlugGenerator
    fun generate(input: String?): String?
}
```

### `SlugRegistry`

A registry to manage the global `ISlugProvider` instance and cascade slug dependency map.
```kotlin
object SlugRegistry {
    fun setSlugProvider(provider: ISlugProvider?)
    fun getSlugProvider(): ISlugProvider
    fun registerCascadeDependent(intermediateClass: Class<*>, dep: CascadeDependency)
    fun getCascadeDependents(clazz: Class<*>): List<CascadeDependency>
}
```

### `ISlugProvider`

An interface for generating slugs based on an entity and a base slug string. Supports composite unique constraints.
```kotlin
interface ISlugProvider {
    fun generateSlug(entity: ISlugSupport<*>, slug: String, compositeConstraintFields: Map<String, Any?> = emptyMap()): String
}
```

### `SlugListener`

A JPA entity listener that automatically generates slugs for entities before they are persisted or updated in the database. Add via `@EntityListeners(SlugListener::class)` on your entity.

```kotlin
class SlugListener {
    @PrePersist
    @PreUpdate
    fun handle(entity: Any)
}
```

### `SlugCascadeListener`

A Hibernate `PostUpdateEventListener` registered automatically at startup by `SlugAutoConfiguration`. Refreshes slugs of dependent entities when an intermediate entity (e.g. `Category`) is updated. No manual configuration required.

### `SlugOperationException`

Custom exception thrown when errors occur during slug generation.
```kotlin
class SlugOperationException : RuntimeException {
    constructor()
    constructor(message: String)
    constructor(message: String, cause: Throwable)
}
```

---

## 🛠 Requirements

- Java 17+
- Kotlin 1.9+
- Spring Boot 3.x
- Spring Data JPA (Hibernate as JPA provider)

---

## 🔁 Other Implementations

[Spring Boot JPA Slug (Java Maven Package)](https://github.com/mewebstudio/spring-boot-jpa-slug)

## 🤝 Contributing
I welcome contributions! Please fork this repository, make your changes, and submit a pull request. If you're fixing a bug, please provide steps to reproduce the issue and the expected behavior.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

## 💡 Example Implementations

[Spring Boot JPA Slug - Kotlin Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-kotlin-impl)

[Spring Boot JPA Slug - Java Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-java-impl)
