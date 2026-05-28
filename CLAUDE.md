# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Monorepo of three independent Spring Boot mock services for local development and testing of a game backend. Each service is a separate Gradle project under its own subdirectory.

| Service | Port | Purpose |
|---|---|---|
| `be-agency-mock` | 8083 | User management (register/login), game token issuance, deposit relay |
| `be-wallet-mock` | 8081 (REST), 9093 (gRPC) | In-memory wallet — YAM REST API + Luigi TransferGateway gRPC |
| `be-wsproxy-mock` | 8082 (WS), 5555 (ZMQ) | WebSocket proxy bridging clients to game backend via gRPC/ZMQ |

## Commands

### Docker (recommended — starts all services in dependency order)
```bash
docker-compose up -d
docker-compose logs -f [service-name]
docker-compose down
```

### Per-service local run (from repo root)
```bash
cd be-agency-mock && ./gradlew bootRun
cd be-wsproxy-mock && ./gradlew bootRun
cd be-wallet-mock  && ./gradlew bootRun
```

### Build & test
```bash
# Build (per service)
cd be-agency-mock  && ./gradlew build
cd be-wsproxy-mock && ./gradlew build   # also generates gRPC Java from .proto
cd be-wallet-mock  && ./gradlew build   # also generates gRPC Java from .proto

# Run tests (per service)
cd be-agency-mock  && ./gradlew test
cd be-wsproxy-mock && ./gradlew test
cd be-wallet-mock  && ./gradlew test
```

Proto regeneration happens automatically as part of `./gradlew build` via the `com.google.protobuf` Gradle plugin.

## Architecture

### Request flow
```
Client
  ├─ REST → be-agency-mock:8083  (register, login, deposit, play-game/get-game-token)
  │         └─ REST → be-wallet-mock:8081  (register user, deposit, get balance)
  └─ WS  → be-wsproxy-mock:8082
              ├─ gRPC → game backend:9092  (ConnectAndCall / Call / Disconnect)
              └─ ZMQ SUB ← game backend   (responses published back to client via WS)
```

### be-wsproxy-mock message protocol
WebSocket frames are JSON arrays. The first element is the frame type:
- `1` — Auth: `[1, zone, pluginName, "", {"accessToken": "<game-token>"}]`
- `6` — Command: `[6, zone, pluginName, {"cmd": <int>, ...}]`
- `7` — Ping: `["7", zone, "1", seq]`

After auth succeeds, `cmd=1005` triggers `ConnectAndCall` (JOIN to game backend); all other commands use `Call`. Responses are delivered back to the client via the ZMQ subscriber thread, not the gRPC response.

### be-wallet-mock dual interface
- **REST** (`/wallet/*`) — consumed by `be-agency-mock` for user registration and deposit
- **gRPC** (port 9093, `TransferGateway` service) — consumed by the game backend (be-nagas) for DEBIT/CREDIT during gameplay; implements Luigi wallet response codes (200 OK, 404/605/606/607 errors, 409 duplicate)

### Data storage
All three services use H2 in-memory databases (`create-drop` on restart). Data does not survive restarts. H2 console available at `/h2-console` on agency-mock and wallet-mock.

## Key files to know

- `be-wsproxy-mock/src/main/proto/plugin_service.proto` — gRPC contract between WsProxy and game backend; must match the game backend's proto exactly (no `package` directive — wire path is `/PluginService/ConnectAndCall`)
- `be-wsproxy-mock/src/main/java/.../websocket/WsProxyHandler.java` — central WS frame dispatcher; owns cmd routing and session lifecycle
- `be-wsproxy-mock/libs/common-data-1.0.0.jar` — local JAR providing `PuElementMessageFrame` for ZMQ message parsing (Luigi proprietary)
- `be-wallet-mock/src/main/proto/transfer_gateway.proto` — gRPC contract for Luigi wallet service

## Configuration

All config is in `src/main/resources/application.yml` per service; overridable via environment variables in `docker-compose.yml`. Critical values:

- `APP_GAME_TOKEN_SECRET` — must be identical in `be-agency-mock` and `be-wsproxy-mock` (token issued by agency, validated by wsproxy)
- `APP_GRPC_GAME_BACKEND_HOST/PORT` — wsproxy target for game backend (default `host.docker.internal:9092`)
- `APP_ZMQ_SUB_URL` — ZMQ subscriber address (default `tcp://0.0.0.0:5555`)

## Adapting for a new game

1. Replace `be-wsproxy-mock/src/main/proto/plugin_service.proto` with the new game's proto
2. Run `./gradlew build` to regenerate Java classes
3. Update `app.grpc.game-backend.*` and `app.zmq.sub-url` in `application.yml`
4. Review `GameTokenValidator.java` for JWT claim names (`gid`, `aid`) — update if the new game uses different claims
5. Review `WsProxyHandler.handleCommand()` for game-specific cmd routing
