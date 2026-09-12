# discord-ipc

A client for Discord's local IPC socket, with Rich Presence support. It connects in the
background, reconnects on its own, and replays what you set once the connection is back.

`com.r5ylx:discord-ipc:1.0.0` - Java 21 or newer, Apache 2.0.

## Install

Not on Maven Central yet. Clone and install it locally:

```bash
git clone https://github.com/r5ylx/discord-ipc.git
cd discord-ipc
./gradlew publishToMavenLocal
```

Then, in the consuming build:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.r5ylx:discord-ipc:1.0.0'
}
```

`org.json:json` comes along as an `api` dependency, because `Packet#getJson()` and `RichPresence#toJson()` hand out
`JSONObject`.

## Usage

```java
try (IPCClient client = new IPCClient(1234567890L)) {
    client.setListener(new IPCListener() {
        @Override
        public void onReady(IPCClient source, User user) {
            System.out.println("connected as " + user.getDisplayName());
        }
    });

    client.connect();

    client.setPresence(RichPresence.builder()
        .setDetails("In the lobby")
        .setState("Waiting")
        .setStartTimestamp(Instant.now())
        .setLargeImage("logo", "Talpius Client")
        .build());

    ...
}
```

The argument to the constructor is the application id from the
[Discord developer portal](https://discord.com/developers/applications).

## Connecting

`connect()` returns immediately. A background thread does the work and keeps retrying with a
growing delay (1s, doubling, capped at 30s) while Discord is not running.

**There is no need to wait for a connection before sending.** What you pass to `setPresence` and
`subscribe` is kept, and sent again as soon as a connection is made and after every reconnect.

```java
client.connect();
client.setPresence(presence);   // fine even if Discord is not running yet
```

`isConnected()` tells you whether the handshake has completed, if you need to know.

## Events

Ask for the events you want; they arrive through the listener.

```java
client.subscribe(Event.ACTIVITY_JOIN);
client.subscribe(Event.ACTIVITY_SPECTATE);
client.subscribe(Event.ACTIVITY_JOIN_REQUEST);
```

Subscriptions are kept across reconnects. Passing an event that cannot be subscribed to
(`READY`, `ERROR`, `UNKNOWN`) does nothing.

To make the join and spectate buttons appear, the presence needs both a party and a secret:

```java
RichPresence.builder()
    .setParty("party-1", 2, 4)
    .setJoinSecret("join-secret")
    .setSpectateSecret("spectate-secret")
    .build();
```

## The listener

Every method has a default that does nothing, so implement only what you need.

| Method | When |
| --- | --- |
| `onReady(client, user)` | the handshake completed - again after every reconnect |
| `onDisconnected(client, message)` | the connection dropped; a reconnect follows |
| `onError(client, message)` | something unexpected happened, including a throw from your own listener |
| `onJoinGame(client, secret)` | the join button was pressed |
| `onSpectateGame(client, secret)` | the spectate button was pressed |
| `onJoinRequest(client, user)` | someone asked to join |
| `onPacketReceived(client, packet)` | any frame arrived |
| `onPacketSent(client, packet)` | any frame went out |

Calls arrive on a thread owned by the library. Move anything heavy, and anything that belongs to
your own render thread, over to that thread yourself.

An exception thrown by a listener is caught and routed to `onError`. The threads keep running.

## Threads

Two daemon threads: one connects and reads, the other writes. Being daemon threads, they never
hold up JVM shutdown.

`close()` stops both and is safe to call before starting, and safe to call twice. `IPCClient` is
`AutoCloseable`.

## Testing without Discord

`IPCClient` takes a `TransportFactory`, which is a functional interface. Supply your own and the
whole client runs with no pipe at all - that is how this project's own tests work.

```java
IPCClient client = new IPCClient(1234567890L, index -> myFakeTransport());
```

## Public API

| Type | Role |
| --- | --- |
| `IPCClient` | connect, set the presence, subscribe |
| `IPCListener` | where notifications from Discord arrive |
| `RichPresence` | what is displayed, built with `RichPresence.builder()` |
| `User` | a Discord user, including avatar URLs |
| `Packet` | one frame: an `Opcode` and a `JSONObject` |
| `Event` / `Command` / `Opcode` / `State` | the protocol enums |
| `Transport` / `TransportFactory` | the seam below the protocol |
| `IPCException` and subclasses | connection, protocol, and not-connected failures |

`Connection`, which does the framing and the handshake, is not public.

## Building

```bash
./gradlew build   # compile, javadoc, test
./gradlew test
```

`-Xlint:all -Werror` is on. A single warning fails the build.

The tests never touch a real pipe, so they run anywhere, with or without Discord installed.

## License

Apache License 2.0. See [LICENSE](LICENSE).
