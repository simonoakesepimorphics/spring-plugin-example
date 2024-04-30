# Spring Boot Plugin Development

This document explores the use cases and methodology for implementing,
packaging and integrating Spring Boot applications which are extensible by external plugins.
This document refers to application and library code written in Kotlin and managed by Maven,
but should be applicable to any other equivalent Spring-compatible technologies.

When developing a Spring Boot application,
we may want to import dependencies into the project which serve a specific set of features or integrations with other services,
but are not necessarily useful to all deployments of the application.
For example, an application which publishes or consumes files may - in some cases - need to integrate
with an external service such as Amazon S3 via the Amazon SDK library,
while in other cases local file storage may be sufficient.

It is sometimes preferable to simply include those dependencies in the core application JAR, and make the related features optional.
In this case, the application must support enabling and disabling those features
through the local environment (environment variables, application.properties file, etc.) of each deployment of the application.

Alternatively, we may prefer to include such features and their dependencies selectively as "plugins", based on the specific needs of each deployment.
This is the focus of this document.
This approach prevents the core application JAR and configuration files from being inflated with unnecessary dependencies.
For features which utilise Spring auto-configuration, this approach also prevents the framework from attempting to
configure beans and resources that are not needed and might emit irrelevant errors.

By packaging optional features as plugins rather than building them into the core application,
the application developer empowers the operator to decide which features and dependencies should be included in each deployment at the point of configuration.
Furthermore, once we adapt the core application to support plugins using the method described in this document,
developers can extend its functionality arbitrarily with any number of additional plugins, as long as they are compatible.

## Overview

The main method of integrating plugin code into the core application is to add the plugin JAR(s) to the classpath
and inject their configuration into the Spring application context at runtime.
In order to do so, the core application must provide a hook for adding a Spring [context configuration file](https://docs.spring.io/spring-framework/docs/4.2.x/spring-framework-reference/html/xsd-configuration.html)
which loads the plugin configuration and beans from any number of separate plugin JARs.
The [external context configuration](#external-context-configuration) section of this document explains this in detail.

For plugins whose features are "visible" to the core application (for example, an integration with an external database),
we define the required behaviour as a Kotlin interface in the core application, and implement that interface in the plugin library.
In this way, classes in the core application, such as controllers or services, can use the features of the interface at run time
without any knowledge of the plugin's implementation at compile time.

However, the approach described in this document also works for adding entirely new behaviour to the application,
such as new request handlers, message converters, MVC configurers, and so on.
Effectively, anything that can be added to the Spring application context can be written as a plugin.

## A Simplified Example

In order to illustrate the plugin approach, we will refer to an example project: [Spring Plugin Example](https://github.com/simonoakesepimorphics/spring-plugin-example),
which you can download, run and modify yourself.
Wherever we reference specific classes, you can follow the links to their source code on GitHub.
This example has the following characteristics:

* The core "greeting" application has one endpoint, `/greeting`, accepting a required `name` parameter, and an optional `language` parameter.
* The core implementation returns a default greeting addressed to the specified name.
* A "language plugin" interface defines a method for selecting the greeting format for the specified language.
* If a language plugin implementation is present in the application context and the language is specified, the greeting controller uses a greeting format obtained from the language plugin instead of the default format.
* The language plugin library contains an implementation which reads the greeting format for each language from an embedded JSON resource.
* The language plugin library introduces a dependency on the [Jackson](https://github.com/FasterXML/jackson) library for JSON deserialization.

We define the plugin behaviour as a Kotlin interface in the core application ([LanguagePlugin](https://github.com/simonoakesepimorphics/spring-plugin-example/blob/main/greeting-core/src/main/kotlin/com/epimorphics/greeting/LanguagePlugin.kt)),
and the plugin library provides an implementation of that interface ([JsonLanguagePlugin](https://github.com/simonoakesepimorphics/spring-plugin-example/blob/main/greeting-language-plugin/src/main/kotlin/com/epimorphics/greeting/JsonLanguagePlugin.kt)).
In this way, classes in the core application (such as [GreetingController](https://github.com/simonoakesepimorphics/spring-plugin-example/blob/main/greeting-core/src/main/kotlin/com/epimorphics/greeting/GreetingContoller.kt))
can use the features of the interface at run time without any knowledge of the plugin's implementation at compile time.

To run the core application in its standalone mode, clone the repository, navigate to the project directory, then run the following:

```
mvn clean package
java -jar greeting-core/target/greeting-core-1.0-SNAPSHOT-exec.jar
```

The `greeting-core-1.0-SNAPSHOT-exec.jar` JAR does not contain any of the plugin code or Jackson dependency.
When the application has started successfully, navigate to http://localhost:8080/greeting?name=Alice in your web browser to see the default greeting.

Note that http://localhost:8080/greeting?name=Alice&language=fr does not display a multilingual greeting
because the application is running without the language plugin.
To run the application with the language plugin, run the following:

```
java -cp \
	greeting-core/target/greeting-core-1.0-SNAPSHOT-exec.jar \
	-Dloader.path=greeting-language-plugin/target/greeting-language-plugin-1.0-SNAPSHOT-jar-with-dependencies.jar \
	org.springframework.boot.loader.PropertiesLauncher \
	--greeting.config.external.location=classpath:/context.xml
```

Then, navigate to http://localhost:8080/greeting?name=Alice&language=fr to see a greeting in French.
The plugin supports `en`, `fr`, `de` and `es` language codes (see the [JSON file](https://github.com/simonoakesepimorphics/spring-plugin-example/blob/main/greeting-language-plugin/src/main/resources/greeting.json) for details).

The next section describes the Maven project structure we use to create the JAR artifacts.
To skip directly to the details of the implementation, see the [Injecting Plugin Beans](#injecting-plugin-beans) section.

## Project Structure

This section describes how to organise and relate the Maven projects for the core application and its plugins.
Each plugin is developed as a separate Maven project, which may be a module of the original project for convenience.
Plugin development should not affect the packaging of the core application.

Our example project contains the following Maven modules:

* **spring-plugin-example** - Parent POM for all other modules.
* **greeting-core** - Core web application containing the basic features and the plugin interface definition.
    * Contains all Spring Boot and Spring WebMVC dependencies.
* **greeting-language-plugin** - Plugin library containing the plugin implementation.
    * Has **greeting-core** as a dependency with `provided` scope.

Dependencies that the core application and its plugins have in common must be synchronised as much as possible,
for example by sharing a common parent POM containing a `dependencyManagement` element which
sets the versions for all shared dependencies.
This is important for avoiding version conflicts in the deployed application.

In each plugin POM, you must mark any dependencies in common with the core application (including the core application library itself, if it is present)
with the `provided` scope (this makes them available for compilation but not included in the final JAR).
The core application JAR will provide these dependencies at runtime, so there is no need to include them in the plugin JAR.
The contents of these JARs are determined by the packaging process described in the next section.

## Project Packaging

This section describes the configuration and outputs of the packaging process (`mvn clean package`) for both the core application and its plugins.
Each project produces one or more JAR files which we can use to run the application either alone or with plugins.

### Packaging the Core Application

The core application must be repackaged by the Spring Boot Maven build plugin to produce an executable JAR,
as is standard for Spring Boot applications.
In our example project, we require this process to produce both the executable JAR and a non-executable library JAR so
that we can use the latter as a dependency (with `provided` scope) in the plugin project.
As such, we configure the Spring Boot Maven plugin in the POM `build` tag as follows:

```
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>${spring.boot.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
            <configuration>
                <attach>false</attach>
                <classifier>exec</classifier>
                <mainClass>com.epimorphics.greeting.WebAppRunnerKt</mainClass>
            </configuration>
        </execution>
    </executions>
</plugin>
```

With the configuration above, the packaging process produces the following JARs:

* **greeting-core-1.0-SNAPSHOT-exec.jar** - The standalone, executable application JAR.
* **greeting-core-1.0-SNAPSHOT.jar** - The application library. This is the artifact associated with the project in our Maven repository.

### Packaging an Application Plugin

Each plugin project has its own packaging process.
Since each plugin is effectively a Kotlin library,
we use the Kotlin Maven build plugin to package it as a non-executable library JAR, which can be used as a dependency in other projects.

However, this JAR contains only the plugin project code, **without** any of its dependencies.
The dependencies will be necessary when running the plugin code alongside the core application,
so we must use the Maven Assembly build plugin in the POM `build` tag to package both the plugin code and its dependencies in a single JAR as follows:

```
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.7.1</version>
    <configuration>
        <descriptorRefs>
            <descriptorRef>jar-with-dependencies</descriptorRef>
        </descriptorRefs>
    </configuration>
    <executions>
        <execution>
            <id>assemble-all</id>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

With the configuration above, the plugin packaging produces the following JARs:

* **greeting-language-plugin-1.0-SNAPSHOT.jar** - The plugin library. This is the artifact associated with the project in our Maven repository.
* **greeting-language-plugin-1.0-SNAPSHOT-jar-with-dependencies.jar** - The plugin library and its dependencies (excluding those with `provided` scope).

With the project structure and packaging in place, we can look more deeply into the implementation details.

## Injecting Plugin Beans

This section describes the mechanism for injecting plugin beans into core application code.
This is only relevant to plugins which implement some Kotlin interface defined by the core application (as described in the [overview section](#overview)).
Spring enables us to inject each plugin and invoke its interface conditionally, depending on whether
an implementation of that interface is present (i.e. a Bean) in the Spring application context.

Spring automatically injects Beans, including those supplied by the plugin configuration, into the parameters of `@Bean` annotated methods and component constructors.
By default, if an expected bean is not present, Spring raises an error and the application does not start.
This may be desirable in some cases where a plugin implements essential functionality such as a database integration.

Alternatively, we can make the bean injection optional.
To do so, we add the `@Autowired` annotation to the parameter where the bean is expected,
with the `required` parameter set to `false`.
This tells Spring to inject the bean if it is present, but otherwise provide a null value.
In Kotlin, we must ensure that parameters annotated in this way have a nullable type.

If the core application injects an interface implementation in this way,
the usage of the interface in the base application code must account for the case where no bean is provided.

For example:
```
@Bean
fun MyService(@Autowired(required = false) plugin: PluginInterface?): MyService {
  return MyService(plugin)
}
```

The plugin configuration class can be automatically added to the Spring application context if it's included by
an existing `@ComponentScan` annotation in the core application.
However, this is not necessarily desirable since it requires the core application code to anticipate
all of the possible plugins and their package names.
Instead, we use an externalized context configuration to add the plugin configuration to the context.

### External Context Configuration

The core application supports dynamically loading plugins into the Spring application context by declaring an
`@ImportResource` annotation on a specialised configuration class.
This class' role is simply to import an arbitrary Spring XML context configuration file (or resource) at run time.

The context configuration contains bean definitions for any number of plugin classes and additional customisations.
It may be written specifically by the application operator for each deployment,
or a minimal default configuration may be packaged in the plugin JAR for convenience.

The specialised configuration class should have this basic format:

```
@Configuration
@ConditionalOnProperty("app.config.external.location")
@ImportResource("\${app.config.external.location}")
class ExternalConfig
```

Spring Boot determines the configuration file to load based on the value of `app.config.external.location` at run time.
You can provide this property value as a program argument or environment variable.
Note that, if the property is not set, this class does nothing.

To use an external XML configuration file that is present in the application's local environment, set the location property to the absolute file path of that file with the `file:` prefix.

To use an XML configuration resource that is packaged with a specific plugin, set the location property to the classpath URL of the resource with the `classpath:` prefix.
Note that classpath URL paths must start with `/`).

In our example application, the default context configuration for the language plugin is packaged in `context.xml`, so we set the location property (`greeting.config.external.location`) to `classpath:/context.xml` to invoke it.
The context configuration contains the following XML content:

```
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
        http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean class="com.epimorphics.greeting.JsonLanguagePluginConfig" />
</beans>
```

This loads the [JsonLanguagePluginConfig](https://github.com/simonoakesepimorphics/spring-plugin-example/blob/main/greeting-language-plugin/src/main/kotlin/com/epimorphics/greeting/JsonLanguagePluginConfig.kt) configuration class from the plugin JAR.

To use multiple plugins at the same time, you must create a new XML configuration file containing the bean definitions of each.

When we have written or chosen an external context configuration file for a particular deployment, we can proceed to running the application.

## Running the Application with Plugins

The standard Spring Boot application packaging builds an executable JAR containing the application and all of its dependencies, which is suitable for running the application alone.
In order to insert one or more plugins into the application, we must run it with a customised main class and classpath;
we will use the Spring Boot `PropertiesLauncher` class and the `-Dloader.path` argument for this purpose.
See the [Spring documentation](https://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#appendix.executable-jar.launching) for more details.

In summary, we must run the application with the following conditions:
* Run the core application JAR with `java -cp`.
* Add the plugin JAR to the classpath with `-Dloader.path`.
* Use `org.springframework.boot.loader.PropertiesLauncher` as the main class.
* Set the [external context configuraton](#externalized-configuration) location with the relevant configuration property (You can set this with either a program argument or an environment variable).

For example, to run our example application with the language plugin:

```
CORE_APPLICATION_JAR=greeting-core/target/greeting-core-1.0-SNAPSHOT-exec.jar
PLUGIN_JAR_WITH_DEPS=greeting-language-plugin/target/greeting-language-plugin-1.0-SNAPSHOT-jar-with-dependencies.jar
EXTERNAL_PLUGIN_CONFIG=classpath:/context.xml

java \
  -cp $CORE_APPLICATION_JAR \
  -Dloader.path=$PLUGIN_JAR_WITH_DEPS \
  org.springframework.boot.loader.PropertiesLauncher \
  --greeting.config.external.location=$EXTERNAL_PLUGIN_CONFIG
```

With variables being defined as follows:

* `CORE_APPLICATION_JAR` - The location of the executable application JAR.
* `PLUGIN_JAR_WITH_DEPS` - The location of the plugin library JAR with dependencies.
* `EXTERNAL_PLUGIN_CONFIG` - The location of the external context configuraton file or resource.

Additional plugins can be added in the same way by listing their JAR files as a comma-delimited list in the `-Dloader.path` argument value.
When you add a plugin, you must remember to add its configuration classes to your context configuration file.

## Conclusion

The plugin approach described in this document enables developers to write extensible, flexible Spring Boot applications
while maintaining space efficiency.
At Epimorphics we have developed plugins specifically for Keycloak (authorization and authentication)
and PostgreSQL integrations for various applications.
This is one of a variety of Spring development practices which emerge as we become more familiar and adept with the framework.
