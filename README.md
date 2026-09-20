# bambu-java

A small Java client for **Bambu Lab 3D printers** in LAN mode. It connects to the printer's
built-in MQTT broker over TLS, keeps the latest printer status in memory, and sends basic
commands (light, pause, resume, stop).

Bambu Lab has no official API for this. The protocol was worked out from the printer's own
traffic and from community notes. Tested on a **Bambu Lab H2D**.

## Install

Available through [JitPack](https://jitpack.io):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.efecanseymen</groupId>
    <artifactId>bambu-java</artifactId>
    <version>v0.1.0</version>
</dependency>
```

Requires Java 17 or newer.

## Usage

```java
BambuClient client = new BambuClient("10.10.10.183", "accessCode", "serialNumber");
client.connect();

PrinterStatus status = client.getStatus();
System.out.println(status.getGcodeState() + " - " + status.getMcPercent() + "%");

client.setLight(true);
client.disconnect();
```

`getStatus()` returns `null` until the first message arrives, which usually takes under a second.
To react to every update instead of polling:

```java
client.setStatusListener(status -> {
    if ("FINISH".equals(status.getGcodeState())) {
        System.out.println("Print finished: " + status.getSubtaskName());
    }
});
```

### Where the connection details come from

| Value | Where to find it |
|---|---|
| IP address | Printer screen → Settings → WLAN |
| Access code | Printer screen → Settings → WLAN → Access Code |
| Serial number | Printer screen → Settings → Device, or the label on the back |

The printer must be in **LAN mode** and your machine must be on the same network — the MQTT
broker runs inside the printer itself.

## What it reads

`PrinterStatus` exposes the fields most applications need: `gcode_state`, print progress and
remaining time, current/total layer, nozzle and bed temperatures (current and target), HMS
warnings, Wi-Fi signal and nozzle diameter. The printer sends about 99 fields; unknown ones are
ignored, so a firmware update will not break parsing.

Three helpers save you from a common mistake:

```java
status.isAvailable();   // IDLE, FINISH or FAILED
status.isPrinting();    // RUNNING or PREPARE
status.hasWarning();    // HMS list is not empty
```

`gcode_state` is *sticky*: after a print finishes it stays `FINISH` until a new job starts — it
never goes back to `IDLE`. Checking `== "IDLE"` to decide whether the printer is free gives the
wrong answer, which is why `isAvailable()` exists.

## What it does not do

Out of scope in this version: starting prints (that needs FTPS file upload), AMS filament
control, the camera stream, multiple printers, and raw G-code.

## Reconnecting

`connect()` enables automatic reconnect. Because the client uses a clean session, the
subscription is re-created and a full status refresh is requested after every reconnect —
otherwise the client would look connected while silently serving stale data.

Connection state can be checked two ways, and you usually want both:

```java
client.isConnected();   // TCP/MQTT connection is up
client.isStale(30);     // no message from the printer for 30 seconds
```

## Security

**TLS certificate verification is disabled.** The printer uses a self-signed certificate with no
SAN field, so neither the chain nor the hostname can be verified. Traffic is still encrypted, but
a machine on the same network could impersonate the printer. Use this only on a network you
trust. Certificate pinning is planned for a later version.

Two more things worth knowing:

- Anyone with the serial number **and** the access code has full control of the printer. Keep
  them out of source code, screenshots and logs — read them from environment variables.
- The library never logs the raw status JSON, because the serial number appears in it.

## License

MIT — see [LICENSE](LICENSE).
