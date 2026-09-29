# BjlIo

Small, dependency-free Java classes for reading and writing lines of text with an
explicit line terminator, plus a few useful stream wrappers.

`BufferedReader.readLine()` accepts any of `\n`, `\r` or `\r\n` as the end of a line,
and `PrintWriter.println()` writes whatever the local OS uses. That is fine for local
files, but Internet protocols (SMTP, HTTP, FTP, POP3, ...) define the end of line
exactly, usually as CR LF. BjlIo lets you say which terminator you mean, so the same
code behaves the same way on every OS.

- Java 11 or later
- No runtime dependencies
- Apache License 2.0

## What's included

| Class | Purpose |
|---|---|
| `CRLFLineReader` / `CRLFLineWriter` | Lines terminated by CR LF. A lone CR or LF is kept as part of the line. |
| `LFLineReader` / `LFLineWriter` | Lines terminated by LF. |
| `ILineReader` / `ILineWriter` | Common interfaces for the readers and writers above. |
| `ContinuousInputStream` | Reads a file and waits for more data at the end, like `tail -f`. |
| `MonitoredInputStream` / `MonitoredOutputStream` | Report progress to an `IStreamMonitor` as data flows through them. |
| `TeeOutputStream` | Writes the same data to several output streams. |
| `TelnetInputStream` / `TelnetOutputStream` | Echo input back to the peer, and strip output to 7-bit printable characters. |

The line readers and writers:

- are `InputStream` / `OutputStream` subclasses, so you can mix `readLine()` with `read()` on the same stream;
- use UTF-8 unless you pass another `Charset`;
- count bytes (`getBytesIn()`, `getBytesOut()`) and record when they last read or wrote (`getLastReadTime()`, `getLastWriteTime()`), which is handy for idle time-outs;
- return a final line that has no terminator, instead of dropping it.

## Installation

The artifact is published to GitHub Packages:

```xml
<dependency>
    <groupId>us.bringardner</groupId>
    <artifactId>bjl_io</artifactId>
    <version>0.1.2</version>
</dependency>
```

GitHub Packages needs authentication even for public packages. Add the repository to
your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/tony-bringardner/BjlIo</url>
    </repository>
</repositories>
```

Then add a matching server to `~/.m2/settings.xml`. The token needs the `read:packages` scope.

```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>
```

## Usage

### Talk to a CR LF protocol

```java
try (Socket socket = new Socket("mail.example.com", 25);
     CRLFLineReader in = new CRLFLineReader(socket.getInputStream());
     CRLFLineWriter out = new CRLFLineWriter(socket.getOutputStream())) {

    // Protect against a peer that never sends a line terminator.
    in.setMaxLineLength(1000);

    String greeting = in.readLine();      // "220 mail.example.com ESMTP ..."
    out.writeLine("EHLO client.example.com");
    String reply = in.readLine();
}
```

When a stream writer is created, it flushes after every write, so each line goes out
straight away. Call `setAutoFlush(false)` to batch several lines, and then call `flush()` yourself.

### Limit line length

Without a limit, `readLine()` keeps buffering until it finds a terminator. On input from
the network, set a limit:

```java
reader.setMaxLineLength(8192);
try {
    String line = reader.readLine();
} catch (LineTooLongException e) {
    // The rest of that line is still unread; usually you close the connection here.
}
```

### Read and write files

```java
try (LFLineWriter out = new LFLineWriter(new File("data.txt"))) {
    out.writeLine("first");
    out.writeLine("second");
}

try (LFLineReader in = new LFLineReader(new File("data.txt"), StandardCharsets.UTF_8)) {
    String line;
    while ((line = in.readLine()) != null) {
        System.out.println(line);
    }
}
```

When a writer is created from a `File`, it is buffered and does not flush after every line.

### Follow a growing file (`tail -f`)

```java
ContinuousInputStream log = new ContinuousInputStream(new File("app.log"), false);
log.unreadLines(10);            // start with the last 10 lines, like tail -n 10

// Blocks waiting for new lines. From another thread call log.setEof(true)
// (finish reading what's in the file, then stop) or log.close() (stop now).
try {
    while (true) {
        System.out.println(log.readLine());
    }
} catch (EOFException e) {
    // reached the end after setEof(true) or close()
} finally {
    log.close();
}
```

If the file is truncated, for example by log rotation, reading starts again from the
beginning. `setFreq(ms)` sets how often the file is checked for new data (40 ms by default).

### Track progress

```java
IStreamMonitor monitor = new IStreamMonitor() {
    public void start()                            { System.out.println("started"); }
    public void update(long total, long blockSize) { System.out.println(total + " bytes"); }
    public void complete(long total)               { System.out.println("done: " + total); }
};

try (InputStream in = new MonitoredInputStream(new FileInputStream("big.zip"), 1024 * 1024, monitor)) {
    in.transferTo(OutputStream.nullOutputStream());
}
```

`update` is called each time another `blockSize` bytes have passed through the stream
(4 KB by default). `complete` is called once, at end of stream or on `close()`.

### Copy output to several places

```java
try (OutputStream both = new TeeOutputStream(new FileOutputStream("session.log"), System.out)) {
    both.write("hello\n".getBytes(StandardCharsets.UTF_8));
}
```

If one stream fails, the others are still written. The first error is thrown, and any
later errors are attached to it as suppressed exceptions.

## Building

```
mvn verify
```

This compiles the code, runs the JUnit 5 tests and writes a
[JaCoCo](https://www.jacoco.org/jacoco/) coverage report to
`target/site/jacoco/index.html`. The build fails if line or branch coverage drops below 90%.
You can change the limits with the `jacoco.minLineCoverage` / `jacoco.minBranchCoverage`
properties in `pom.xml`, or for a single run:

```
mvn verify -Djacoco.minLineCoverage=0.95
```

The tests use `us.bringardner:bjl_core`, which is also published to GitHub Packages,
so the `settings.xml` credentials above are needed to build.

## License

Copyright Tony Bringardner. Licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
