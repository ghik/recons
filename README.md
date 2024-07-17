# Scadesh - Scala Debug Shell

Scadesh (Scala Debug Shell) allows you to embed a Scala REPL server into your application,
and connect to it from outside. This is mostly useful as an advanced troubleshooting utility,
giving you almost unlimited access to all the guts and internals of your running application.

## Supported Scala versions

Scadesh supports Scala 2 and 3. However, it uses the Scala compiler API, which has no
compatibility guarantees. As a result, Scadesh must be cross-built for every minor Scala version.
The currently supported versions are 2.13.14+ and 3.4.2+ (unless a version is very fresh and
Scadesh hasn't been built for it yet).

## Usage

To embed Scadesh into your application, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.github.ghik" % "scadesh-server" % VERSION % cross CrossVersion.full
```

### Determining the classpath

Scadesh runs the Scala REPL, which wraps a real Scala compiler, which needs an explicit classpath.
Typically, you can obtain the classpath of your JVM process from the `java.class.path` system
property:

```scala
sys.props("java.class.path").split(File.pathSeparator).toSeq
```

> [!WARNING]
> Depending on the exact way your application is launched, you may not be able to rely on
> the `java.class.path` system property - it may be unset or set to an incorrect value (for the
> REPL server needs). You may need another way to determine the classpath.

### Launching the server

```scala
import java.io.File
import com.github.ghik.scadesh.server.*

val server = new ReplServer(
  classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq,
  bindAddress = "localhost",
  bindPort = 6666,
)

server.run()
```

### Connecting to the server

In order to connect to the REPL server running inside your application, you need to use
the client binary. You can download it from a [Scadesh release](https://github.com/ghik/scadesh/releases) 
on GitHub.

```shell
SCADESH_VERSION=<desired scadesh version>
SCALA_VERSION=<desired scala version>

package=scadesh-client_$SCALA_VERSION-$SCADESH_VERSION
wget https://github.com/ghik/scadesh/releases/download/v$SCADESH_VERSION/$package.zip
unzip $package.zip && cd $package
```

Then, run the client to connect to the server:
```
./bin/scadesh-client localhost 6666
```

and you should be able to see a fully-featured Scala REPL, e.g.

```shell
Welcome to Debug Shell, based on Scala 3.4.2 (21.0.1, Java OpenJDK 64-Bit Server VM).
Type in expressions for evaluation. Or try :help.
val $ext: com.github.ghik.scadesh.server.utils.ShellExtensions = com.github.ghik.scadesh.server.utils.ShellExtensions@46a00624

scala>
```
