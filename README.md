# ReCon - Embeddable Remote Scala Console

ReCon allows you to embed a Scala REPL server into your application,
and connect to it from outside. This is mostly useful as an advanced troubleshooting utility,
giving you almost unlimited access to all the guts and internals of your running application.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [Features](#features)
- [Supported Scala versions](#supported-scala-versions)
- [Quickstart](#quickstart)
    - [Determining the classpath](#determining-the-classpath)
    - [Launching the server](#launching-the-server)
    - [Connecting to the server](#connecting-to-the-server)
- [Using standard input and output](#using-standard-input-and-output)
- [Security](#security)
- [Troubleshooting utilities](#troubleshooting-utilities)
- [Customization](#customization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Features

* remote, fully-featured Scala console, i.e. with syntax highlighting, tab completion, etc.
* support for Scala 2 and 3
* troubleshooting utilities and customizations
* pre-bundled client packages
* secure communication with TLS

## Supported Scala versions

ReCon supports Scala 2 and 3. However, it uses the Scala compiler API, which has no
compatibility guarantees. As a result, ReCon must be cross-built for every minor Scala version.
The currently supported versions are 2.13.14+ and 3.4.2+ (unless a version is very fresh and
ReCon hasn't been built for it yet).

Because the implementation of ReCon copies some code from the compiler, and uses some
private APIs via runtime reflection, there's a risk that it may not work with future Scala
versions or require more significant changes to keep up with the compiler. Hopefully, it may
be possible to propose some refactors to the compiler itself to improve the situation.

## Quickstart

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies +=
  "com.github.ghik" % "recon-server" % VERSION cross CrossVersion.full
```

### Determining the classpath

ReCon runs the Scala REPL, which wraps a real Scala compiler, which needs an explicit classpath.
Typically, you can obtain the classpath of your JVM process from the `java.class.path` system
property:

```scala
sys.props("java.class.path").split(File.pathSeparator).toSeq
```

> [!WARNING]
> Depending on the exact way your application is launched, you may not be able to rely on
> the `java.class.path` system property - it may be unset or set to an incorrect value (for the
> REPL server needs). You may need another way of determining the classpath.

### Launching the server

```scala
import java.io.File
import com.github.ghik.recon.server.*

val server = new ReplServer(
  classpath = sys.props("java.class.path").split(File.pathSeparator).toSeq,
  tlsConfig = None, // disabling TLS for the sake of brevity of this example
  bindAddress = "localhost",
  bindPort = 6666,
)

server.run()
```

### Connecting to the server

In order to connect to the REPL server running inside your application, you need to use
the client binary. You can download it from a [ReCon release](https://github.com/ghik/recon/releases)
on GitHub.

```shell
RECON_VERSION=<desired recon version>
SCALA_VERSION=<desired scala version>

package=recon-client_$SCALA_VERSION-$RECON_VERSION
wget https://github.com/ghik/recon/releases/download/v$RECON_VERSION/$package.zip
unzip $package.zip && cd $package
```

Then, run the client to connect to the server:

```
./bin/recon-client -h localhost -p 6666 --no-tls
```

and you should be able to see a fully-featured Scala REPL, e.g.

```shell
Welcome to ReCon, based on Scala 3.4.2 (Java 21.0.1, OpenJDK 64-Bit Server VM).
Type in expressions for evaluation. Or try :help.

val $ext: com.github.ghik.recon.server.utils.ShellExtensions = com.github.ghik.recon.server.utils.ShellExtensions@46a00624

recon>
```

> [!NOTE]
> For simplicity of the example, we have disabled TLS.

## Using standard input and output

The REPL runs in the server JVM process, which is a different process than the client.
This means that the standard input and output of the REPL are not connected to the client,
like they would in a regular, local Scala REPL.
If you invoke `scala.Predef.println` or similar functions in the remote REPL,
you will not see the output in your terminal.

In order to work around this to some extent, ReCon shadows `println` and `print` methods
with ones that use a custom output stream, connected to the client. However, they work only
in code written and compiled directly in the REPL.

You can also use `out` (a `PrintStream`) to grab direct access to this custom output stream,
pass it to other functions, etc.

There is currently no way to read client's standard input inside the REPL.

## Security

ReCon gives you full access to your application process from outside, and lets you run arbitrary
code in it. This is an obvious security risk, so you must make sure to limit access to the
remote REPL to power users, and be extremely careful about what you execute in it. Otherwise, you may
crash your process, put a thread into an infinite loop, or corrupt the internal state of your application.

ReCon binds on localhost address by default. It might make sense to leave it this way, and
allow access only from the local machine or container/pod (e.g. in Kubernetes you would need permissions
to execute `kubectl exec` on your application's pods in order to gain access to the REPL).

You can also secure client-server communication with TLS:

* [`ReplServer`](./server/src/main/scala/com/github/ghik/recon/server/ReplServer.scala)
  accepts a [`TlsConfig`](./server/src/main/scala/com/github/ghik/recon/server/TlsConfig.scala) parameter,
  which allows you to configure a complete
  [`SSLContext`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLContext.html)
  and [`SSLParameters`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLParameters.html)
  for the server.
  This effectively allows you to specify (among others):
    * the keystore and truststore
    * enabled protocols and cipher suites
    * client authentication
* The client accepts `--cacert` option, as well as `--cert` and `--key` options for client
  authentication. Invoke the client with `--help` for more details.

## Troubleshooting utilities

Since ReCon is designed for troubleshooting, it provides some utilities to make it easier.
Namely, every REPL session automatically
imports [`ShellExtensions`](./server/src/main/scala/com/github/ghik/recon/server/utils/ShellExtensions.scala),
which give you the following tools:

* `out`, `print` and `println` - see [Using standard input and output](#using-standard-input-and-output).
* private field and method accessors, e.g. `someObject.d.privateField`, `someObject.d.privateMethod(arg)`
* private static field and method accessors,
  e.g. `statics[SomeClass].privateStaticField`, `statics[SomeClass].privateStaticMethod(arg)`
* shorter syntax for `.asInstanceOf[T]`, e.g. `someObject.as[T]` - to complement
  the private field and method accessors, which return untyped values

## Customization

You can customize the REPL in several ways, by specifying:

* a custom welcome message
* a custom prompt
* initial set of bindings and imports

See [`ReplConfig`](./server/src/main/scala/com/github/ghik/recon/server/ReplConfig.scala) for more details.
