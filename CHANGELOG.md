# Changelog

## 1.0.1 (unreleased)

### Fixed

- A line interrupted by a read error was lost. If reading failed part way through a line, most often
  a `SocketTimeoutException` from a socket with a read timeout, the next `readLine()` started a new
  line and the part already read was dropped. A server that retries after a timeout (as
  bjl_net_framework does) then saw a client that paused in the middle of a line, longer than the
  timeout, send `ice` instead of `USER alice`. The part read is now kept and the next `readLine()`
  carries on with the same line; at the end of the stream it is returned as the last line.
- `getLastReadTime()` was only updated by `readLine()`, so a connection read with `read()`,
  `read(byte[])` or `skip()` looked idle to an idle check (and could be closed while it was busy).
  It is now updated whenever data is read, by any method.

### Changed

- The line readers use a `ReentrantLock` instead of `synchronized` methods (BJL-55). A reader is
  usually blocked on a socket while it holds the lock, and on Java 21-23 a virtual thread blocked
  inside a `synchronized` method holds on to its carrier thread, so a server with a virtual thread
  per connection stopped responding once there were as many idle connections as CPUs.

## 1.0.0

First stable release. It contains breaking changes from 0.1.x; see
[Upgrading from 0.1.x](#upgrading-from-01x) below.

### Breaking changes

- **`ContinuosInputStream` is renamed `ContinuousInputStream`** (spelling fix).
- **`ContinuousInputStream.readLine()` returns `null` at the end of the stream** instead of
  throwing `EOFException`, like the other line readers and `BufferedReader`.
- **`IoConstants` is now a `final class`** instead of an interface. `ILineReader`, `ILineWriter`
  and the reader/writer classes no longer inherit its constants, so write `IoConstants.CR`
  (or use `import static us.bringardner.io.IoConstants.*`) instead of `CRLFLineReader.CR`, and
  classes can no longer `implements IoConstants`.
- **Text is encoded and decoded as UTF-8** unless you pass another `Charset`. 0.1.x writers used
  the platform default encoding, and readers turned each byte into one `char`.
- **`getBytesIn()` counts every byte consumed**, including line terminators and bytes read with
  `read()` or `skip()`. It now matches the writer's `getBytesOut()` for the same data.
- **`TelnetInputStream`** requires a non-null input and echo stream, and `close()` now flushes the
  echo stream but no longer closes it (so `System.out` stays open).
- **`TelnetOutputStream`** transmits all printable ASCII: `MAX_CHAR` is now `'~'`, so `{ | } ~`
  are no longer dropped.
- **`MonitoredInputStream` / `MonitoredOutputStream`** no longer override `equals`/`hashCode`; like
  other streams, each object is only equal to itself.
- **The `main()` test drivers were removed** from `TeeOutputStream` and `ContinuousInputStream`.

### Deprecated

- `IoConstants.CRNL`: any code can change the array. Use `IoConstants.crlf()`, which returns a new copy.

### Added

- `AbstractLineReader`, a shared base class for `CRLFLineReader` and `LFLineReader`.
- `Charset` constructors on every line reader and writer.
- `setMaxLineLength(int)` and `LineTooLongException`, to protect against a peer that never sends
  a line terminator.
- `ContinuousInputStream`: `unreadLines(int)` (like `tail -n`), `setCharset`, bulk `read(byte[], int, int)`,
  and restarting from the beginning when the file is truncated (log rotation).
- Efficient bulk reads and writes in the Telnet streams, and in the monitored and line streams.
- The jar declares the module name `us.bringardner.io` (`Automatic-Module-Name`).
- JaCoCo coverage report and a 90% line/branch coverage check in `mvn verify`.
- `LICENSE` file (Apache 2.0) and license, SCM and URL information in the POM.

### Fixed

- The line readers returned `null` (end of stream) for an empty line; they now return `""`.
- `TeeOutputStream` writes to every stream even if one fails, and reports all errors.
- `getBytesOut()` no longer loses counts when several threads write at the same time.
- Many smaller fixes; see the git history.

### Upgrading from 0.1.x

1. Rename `ContinuosInputStream` to `ContinuousInputStream`.
2. Replace `catch (EOFException e)` around `ContinuousInputStream.readLine()` with a `null` check.
3. Replace constants reached through a reader or writer (`CRLFLineReader.NL`) with `IoConstants.NL`.
4. If you relied on the platform encoding, pass that `Charset` to the reader or writer constructor.
5. Don't pass `null` as the echo stream of a `TelnetInputStream`. If you relied on `close()` closing
   the echo stream, close it yourself.
