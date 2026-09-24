# VLC Music Player

A lightweight desktop audio player built with Java Swing and VLCJ 4.8.0, featuring an MVC architecture and a real-time waveform visualizer.

## Prerequisites

- **Java Development Kit (JDK)**: Version 17 or higher
- **VLC Media Player**: 64-bit installation matching your system architecture

## Quickstart (Running the Distribution)

1. Download and extract the distribution archive `vlc-music-player-*-bin.zip`.
2. Launch the application:
   - **Windows**: Double-click or run `run.bat`
   - **Linux / macOS**: Make executable and execute `./run.sh`:
     ```bash
     chmod +x run.sh
     ./run.sh
     ```
   - **Direct JAR execution**:
     ```bash
     java -jar vlc-music-player-1.0-SNAPSHOT.jar
     ```

> **Note**: If VLC is installed in a non-standard location, the launch scripts will prompt you in the terminal to provide the path to your VLC installation directory containing `libvlc`.

## Building from Source

To compile the codebase, execute unit tests, and build both the standalone JAR and distribution ZIP archive:

```bash
mvn clean package
```

The build artifacts will be produced under the `target/` directory:
- `target/vlc-music-player-1.0-SNAPSHOT.jar`: Packaged application JAR with manifest classpath.
- `target/lib/`: Copied runtime dependencies.
- `target/vlc-music-player-1.0-SNAPSHOT-bin.zip`: Standalone distribution package containing the JAR, dependencies, and launch scripts.

To run tests independently:

```bash
mvn test
```
