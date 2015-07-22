Kitteh Maven Plugin
===================

A fun, simple plugin.

toString analysis
-----------------
This feature checks if classes override toString or use Object's default.

###Example Usage

The below example will fail until all classes (or their super classes) have a custom toString:
```java
<plugin>
    <groupId>org.kitteh</groupId>
    <artifactId>kitteh-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <configuration>
        <packageName>org.kitteh.irc.client.library</packageName>
        <toStringRequired>true</toStringRequired>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>tostring</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```