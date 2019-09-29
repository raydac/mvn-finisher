[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 8.0+](https://img.shields.io/badge/java-8.0%2b-green.svg)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/com.igormaznitsa/mvn-finisher-extension/badge.svg)](http://search.maven.org/#artifactdetails|com.igormaznitsa|mvn-finisher-extension|1.0.0|jar)
[![Maven 3.3.1+](https://img.shields.io/badge/maven-3.3.1%2b-green.svg)](https://maven.apache.org/)
[![PayPal donation](https://img.shields.io/badge/donation-PayPal-red.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=AHWJHJFBAWGL2)
[![Yandex.Money donation](https://img.shields.io/badge/donation-Я.деньги-yellow.svg)](http://yasobe.ru/na/iamoss)


# What is it
Small [maven](https://maven.apache.org/) extesion adds three new phases into build process:
 - __finish__ is called in any case after session build completion
 - __finish-ok__ is called only if session is built without errors
 - __finish-error__ is called only if session is built with errors
 
 It's behavior very similar to well-known `try...catch...finally` mechanism where __finish-error__ situated in the `catch` section and __finish__ situated in the `finally` section, __finish-ok__ will be called as the last ones in the body.
 
 # How to use?
 Just add extension into project build extension section
```xml
<build>
    <extensions>
        <extension>
            <groupId>com.igormaznitsa</groupId>
                <artifactId>mvn-finisher-extension</artifactId>
                <version>1.0.0-SNAPSHOT</version>
        </extension>
    </extensions>
</build>
```
after every end of session build, the extenstion looks for for finishing tasks in all session projects, for instance task to print some message into console:
```xml
<plugin>
    <groupId>com.github.ekryd.echo-maven-plugin</groupId>
    <artifactId>echo-maven-plugin</artifactId>
    <version>1.2.0</version>
    <executions>
        <execution>
            <id>print-echo</id>
            <phase>finish</phase>
            <goals>
                <goal>echo</goal>
            </goals>
            <configuration>
                <message>Hello World from finishing task</message>
            </configuration>
        </execution>
    </executions>
</plugin>
```
in the case, after session processing end (either successful or error) the task `print-echo` is executed.