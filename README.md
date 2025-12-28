# 🎮 Rock Paper Scissors — Multiplayer Edition

A real-time multiplayer Rock Paper Scissors game built with Java socket programming. Features a modern Swing-based UI, room-based matchmaking, and extended game modes with Lizard & Spock.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Java 17+ |
| **Networking** | TCP Sockets (`java.net.Socket`) |
| **UI Framework** | Java Swing |
| **Build System** | Bash Shell Scripts |
| **Architecture** | Multi-threaded Client-Server |

---

## ✨ Features

### Core Gameplay
- 🪨 **Classic Mode** — Rock, Paper, Scissors
- 🦎 **Extended Mode** — Adds Lizard & Spock (host-configurable)
- ❄️ **Cooldown Rule** — Prevents consecutive identical picks (host-configurable)
- ⏱️ **Timed Rounds** — Ready, round, and turn timers

### Multiplayer
- 🚪 **Room System** — Create/join game rooms
- 💬 **Live Chat** — In-game messaging between players
- 📋 **Dynamic User List** — Sorted by points, real-time status updates

### Player States
- ✅ **Ready Status** — Players must ready up to start
- 💀 **Elimination** — Players who don't pick are eliminated
- 😴 **Away Mode** — Temporarily step away without leaving
- 👁️ **Spectator Mode** — Watch games without participating

---

## 🚀 Quick Start

### Prerequisites
- Java JDK 17 or higher
- Bash-compatible terminal (Git Bash on Windows)

### Build
```bash
./build.sh Project
```

### Run Server
```bash
./run.sh Project server [port]
```
- Default port: `3000`

### Run Client (UI)
```bash
./run.sh Project ui
```

### Debug Mode
Add `-d` flag to enable VS Code debugging:
```bash
./run.sh Project server 3000 -d
./run.sh Project ui -d
```

---

## 🏗️ Architecture

```
Project/
├── Client/           # Client-side logic & UI
│   ├── Client.java          # Socket connection & payload handling
│   ├── ClientUI.java        # Main UI frame (CardLayout)
│   ├── Interfaces/          # Event callback interfaces
│   └── Views/               # Swing UI components
├── Server/           # Server-side logic
│   ├── Server.java          # Entry point, accepts connections
│   ├── ServerThread.java    # Per-client connection handler
│   ├── Room.java            # Base room management
│   └── GameRoom.java        # RPS game logic & battle resolution
├── Common/           # Shared models & utilities
│   ├── Payload.java         # Base communication object
│   ├── *Payload.java        # Specialized payload types
│   ├── User.java            # Player state model
│   └── Phase.java           # Game phase enum
└── Exceptions/       # Custom exception types
```

### Communication Flow
```
[Client UI] → Payload → [Socket] → [ServerThread] → [GameRoom]
                                          ↓
[Client UI] ← Payload ← [Socket] ← [Broadcast to Room]
```

---

## 📜 License

MIT License

---

**Author:** rk975
