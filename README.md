# BE Mock Services

Monorepo chứa mock services cho local development/testing của game backend.

## Services

- **be-agency-mock** (port 8083): Agency API mock (register, login, deposit, play-game)
- **be-wsproxy-mock** (port 8082): WebSocket Proxy mock (WebSocket server, gRPC client, ZMQ subscriber)
- **be-wallet-mock** (port 8081): Wallet API mock (YAM REST API)

## Quick Start

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

## Local Development (without Docker)

```bash
# Build each service
cd be-agency-mock && ./gradlew build
cd ../be-wsproxy-mock && ./gradlew build
cd ../be-wallet-mock && ./gradlew build

# Run each service
cd be-agency-mock && ./gradlew bootRun
cd be-wsproxy-mock && ./gradlew bootRun
cd be-wallet-mock && ./gradlew bootRun
```

## Testing Flow

### 1. Register User
```bash
curl --location 'http://localhost:8083/api/v1/user/register' \
--header 'Content-Type: application/json' \
--data '{"username":"testuser","password":"12345678","displayName":"Test User"}'
```

Response:
```json
{
  "userId": "uuid",
  "username": "testuser",
  "token": "jwt-token"
}
```

### 2. Login
```bash
curl --location 'http://localhost:8083/api/v1/user/login' \
--header 'Content-Type: application/json' \
--data '{"username":"testuser","password":"12345678"}'
```

Response:
```json
{
  "token": "jwt-token",
  "userId": "uuid"
}
```

### 3. Deposit
```bash
curl --location 'http://localhost:8083/api/v1/user/deposit' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <login-token>' \
--data '{"amount": "10000"}'
```

### 4. Get Game Token
```bash
curl --location 'http://localhost:8083/api/v1/play-game' \
--header 'Authorization: Bearer <login-token>' \
--header 'Content-Type: application/json' \
--data '{"gameId": "game-nagas-treasure"}'
```

Response:
```json
{
  "gameToken": "jwt-game-token",
  "gameId": "game-nagas-treasure",
  "agencyId": "AGENCY_001"
}
```

### 5. Connect WebSocket
```
ws://localhost:8082/websocket
```

**Auth Frame:**
```json
[1, "MiniGameGatewayZone", "game-nagas-treasure", "", {"accessToken": "<game-token>"}]
```

**Auth Success Response:**
```json
[1, true, 0, "sessionId", "MiniGameGatewayZone", null]
```

**Cmd 100 (Account State):**
```json
[5, {"cmd": 100, "uid": "sessionId", "a": "", "As": {"gold": 1000000, "guarranteed_gold": 0, "time": 1234567890}, "u": "sessionId", "g": 0, "dn": "userId"}]
```

**Cmd 1005 (JOIN):**
```json
[6, "MiniGameGatewayZone", "game-nagas-treasure", {"cmd": "1005"}]
```

**Cmd 1500 (SPIN):**
```json
[6, "MiniGameGatewayZone", "game-nagas-treasure", {"cmd": "1500", "bet": "1"}]
```

**Transport Ping:**
```json
["7", "MiniGameGatewayZone", "1", 1]
```

**Pong Response:**
```json
[6, 1, 1]
```

## Architecture

```
Client → Agency API (user mgmt, wallet, game token)
       ↓ (get game token)
Client → WebSocket (wsproxy) → gRPC → Game Backend (be-nagas)
```

## Configuration

Mỗi service có file `application.yml` riêng để cấu hình port, endpoints, v.v.

## Integration with be-nagas

Để sử dụng mock services với be-nagas:

1. Start mock services: `docker-compose up -d`
2. Cấu hình be-nagas:
   ```yaml
   wallet:
     http:
       enabled: true
       base-url: http://localhost:8081/wallet
   ```
3. Run be-nagas với gRPC port 9092
4. WsProxy mock sẽ tự động kết nối đến be-nagas tại `host.docker.internal:9092`

## Development

Mỗi service là Spring Boot project độc lập với Gradle build.

## WebSocket Command Reference

### Frame Types
- **1**: Auth frame - Authenticate and establish session
- **5**: Server envelope - Game response/data
- **6**: Transport command - In-game commands (SPIN, BUY, etc.)
- **7**: Transport ping - Keepalive

### Supported Commands
- **100**: Account state (sent automatically after auth)
- **1005**: JOIN - Connect to game backend
- **1500**: SPIN - Regular spin
- **1501**: BUY_FEATURE - Buy free spins or hold-and-win
- **1502**: LAST_SESSION - Get current game state

### Frame Structure
```
[frameType, zone, plugin, data]
```

**Example:**
```
[6, "MiniGameGatewayZone", "game-nagas-treasure", {"cmd": "1500", "bet": "1"}]
```

## Troubleshooting

### MongoDB Authentication Error
```
Exception authenticating MongoCredential{...userName='yamazaki'...}
```
**Solution:** Create user in MongoDB:
```bash
docker exec nagas-mongo mongosh --username root --password secret \
  --authenticationDatabase admin \
  --eval "db.getSiblingDB('admin').createUser({user: 'yamazaki', pwd: 'yamazaki@123', roles: [{role: 'readWrite', db: 'nagas-treasure'}]})"
```

### Spin Failed with 500 Error
**Cause:** Missing `agency_id` and `user_id` in payload
**Solution:** Ensure WsProxyHandler includes these fields (already fixed in current version)

### ZMQ Connection Failed
**Cause:** Game backend not running or ZMQ port mismatch
**Solution:**
1. Check game backend is running on configured gRPC port (default 9092)
2. Verify ZMQ publisher address in application.yml

### WebSocket Connection Refused
**Cause:** WsProxy mock not running
**Solution:**
```bash
docker-compose ps be-wsproxy-mock
docker-compose logs be-wsproxy-mock
```

## Using as Template for Other Games

Để sử dụng be-mock-services làm template cho game khác:

1. **Copy the monorepo:**
   ```bash
   cp -r be-mock-services be-mock-services-<<new-game>>
   ```

2. **Replace proto file:**
   - Copy game backend's `plugin_service.proto` to `be-wsproxy-mock/src/main/proto/`
   - Regenerate Java classes: `./gradlew build`

3. **Update configuration:**
   ```yaml
   # be-wsproxy-mock/src/main/resources/application.yml
   app:
     grpc:
       game-backend:
         host: localhost
         port: <<game-grpc-port>>
     zmq:
       sub-url: tcp://0.0.0.0:<<zmq-port>>
   ```

4. **Adjust JWT claims (if needed):**
   - Check `GameTokenValidator.java` for claim names (`gid`, `aid`)
   - Update if game uses different claim structure

5. **Update command routing:**
   - Review `WsProxyHandler.handleCommand()` for game-specific logic
   - Remove/adjust cmd 1005 handling if game doesn't use JOIN

6. **Test with new game:**
   - Start mock services: `docker-compose up -d`
   - Connect game backend to mock services
   - Test full flow

**Reusable Components (~80%):**
- WebSocket frame handling (auth, command, ping)
- Session management
- JWT token validation
- MessagePack encoding/decoding
- ZMQ subscriber
- Wallet mock (YAM API)
- Agency mock

**Game-Specific Components (~20%):**
- Proto file (gRPC contract)
- gRPC port configuration
- ZMQ address configuration
- Command routing logic
- JWT claim structure
