# CheckCopy
A small tool to check if your copies are correct.

## About
I wrote _CheckCopy_ while I was trying to clean-up several harddrives
and got a bit paranoid about forgetting some files on drives when
copying them to other drives. The initial mess started when I had
to move files around because of low disk space and lost track of
what was where in the end...

So this tool is built for a niche task - it does not copy anything itself and
it does not help you organize. All it does is look for all files from a source
in a destination. It can however also check sizes and checksums
for the extra paranoid...

## Requirements
This application requires a Java 8+ runtime to be executed.

## Usage

### Graphical
The Jar file contains all
dependencies and can directly be run, e.g. with a double-click.

The UI should be quite self explaining. All options have tooltip texts
when you hover with your mouse cursor.

### CLI
When running the Jar inside a terminal it switches to CLI mode.
If no parameters are given a summary of available parameters is shown.

There is a parameter to force GUI mode from the CLI while applying your
configuration - this could be used for some form of file manager integration
but I have not found a solid use case for this...

## Internationalization
Currently the application is available in _English_ and _German_. The choosen language
depends on the setting of the JVM that normally matches your system language.

If your language is not supported it falls back to _English_.

More languages can be added via Java _Resource-Bundles_ (see __'resources'__ folder in the source) .

Note: you can set the JVM language to English with the _'-Duser.language=US'_ JVM option if you want to force it.

## Building
You need a Java 8+ JDK to build the application. The build process will download
required dependencies during the first build.

This project uses __Gradle__ for building and the __Shadow__ plugin for
bundling all dependencies inside the _Jar_.

You can build the application by executing the _'shadowJar'_ task (e.g. 'gradlew shadowJar').

This will create the _Jar_ file inside __'build/libs'__.

## License

This application and its source files are distributed under the Apache 2.0 license. See the __'LICENSE'__ file for more information.

## Changelog

1.0: initial release