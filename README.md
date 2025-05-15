# Slug Generator for Spring Boot (Kotlin)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven badge](https://maven-badges.herokuapp.com/maven-central/com.mewebstudio/spring-boot-jpa-slug-kotlin/badge.svg?style=flat)](https://central.sonatype.com/artifact/com.mewebstudio/spring-boot-jpa-slug-kotlin)
[![javadoc](https://javadoc.io/badge2/com.mewebstudio/spring-boot-jpa-slug-kotlin/javadoc.svg)](https://javadoc.io/doc/com.mewebstudio/spring-boot-jpa-slug-kotlin)

A simple and customizable slug generation solution for Spring Boot applications, designed to easily create and manage slugs for entities. This package integrates with JPA entities and provides a flexible way to generate unique slugs for your models.

---

## ✅ Features

- **Customizable Slug Generation**: Provides an interface for defining custom slug generation logic using `ISlugGenerator`.
- **Automatic Slug Assignment**: Automatically generates and assigns slugs to entities upon creation or update.
- **Unique Slug Enforcement**: Ensures that slugs are unique across entities, retrying with suffixes if needed.
- **Integration with Spring Boot**: Easily integrates with Spring Boot using `@EnableSlug` and `SlugRegistry`.

## 📥 Installation

#### for Maven users

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.mewebstudio</groupId>
  <artifactId>spring-boot-jpa-slug-kotlin</artifactId>
  <version>0.1.2</version>
</dependency>
```

#### for Gradle users
```groovy
implementation 'com.mewebstudio:spring-boot-jpa-slug-kotlin:0.1.2'
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
import com.mewebstudio.springboot.jpa.slug.kotlin.ISlugGenerator;

class CustomSlugGenerator : ISlugGenerator {
    override fun generate(input: String?): String? = input?.lowercase()?.replace(Regex("[^a-z0-9\\s-]"), "")
}
```

### 3. Add Slug Field to Your Entity

```kotlin
import com.mewebstudio.springboot.jpa.slug.ISlugSupport
import com.mewebstudio.springboot.jpa.slug.SlugField
import com.mewebstudio.springboot.jpa.slug.SlugListener

@Entity
@EntityListeners(SlugListener::class)
class Category : AbstractBaseEntity(), ISlugSupport<String> {
    @SlugField
    var name: String? = null

    @Column(name = "slug", unique = true, nullable = false)
    override var slug: String? = null
}
```

### 4. Handling Slug Generation

Slugs are automatically generated when entities are created or updated, and they can be customized using the logic provided in the ISlugProvider. The system will also ensure uniqueness by checking against the existing slugs in the database.

## 📘 API Overview

`EnableSlug`

Annotation to enable slug generation in your Spring Boot application. You can specify a custom slug generator by providing the `generator` attribute.

```kotlin
annotation class EnableSlug(
    val generator: KClass<out ISlugGenerator> = DefaultSlugGenerator::class
)
```

`ISlugSupport`

Interface for entities that support slug generation. Implement this interface in your entity classes to enable slug functionality.

```kotlin
interface ISlugSupport<ID> {
    val id: ID

    var slug: String?
}
```

`ISlugGenerator`

The interface for implementing custom slug generators.
```kotlin
interface ISlugGenerator {
    fun generate(input: String?): String?
}
```

`SlugUtil`

Utility class for managing the global slug generator and slug creation.
```kotlin
object SlugUtil {
    fun setGenerator(slugGenerator: ISlugGenerator?)

    fun getGenerator(): ISlugGenerator = generator ?: throw SlugOperationException("SlugGenerator not set")

    fun generate(input: String?): String? = getGenerator().generate(input)
}
```

`SlugRegistry`

A registry to manage the global `ISlugProvider` instance.
```kotlin
object SlugRegistry {
    fun setSlugProvider(provider: ISlugProvider?)

    fun getSlugProvider(): ISlugProvider
}
```

`ISlugProvider`

An interface for generating slugs based on an entity and a base slug string.
```kotlin
interface ISlugProvider {
    fun generateSlug(entity: ISlugSupport<*>, slug: String): String?
}
```

`SlugListener`

A listener that automatically generates slugs for entities before they are persisted or updated in the database.
```kotlin
class SlugListener {
    @PrePersist
    @PreUpdate
    fun handle(entity: Any)
}
```

`SlugOperationException`

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
- Spring Data JPA

---

## 🔁 Other Implementations

[Spring Boot JPA Slug (Java Maven Package)](https://github.com/mewebstudio/spring-boot-jpa-slug)

## 🤝 Contributing
We welcome contributions! Please fork this repository, make your changes, and submit a pull request. If you're fixing a bug, please provide steps to reproduce the issue and the expected behavior.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

## 💡 Example Implementations

[Spring Boot JPA Slug - Kotlin Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-kotlin-impl)

[Spring Boot JPA Slug - Java Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-java-impl)
