# Running ReadyBell Backend + Dashboard UI Locally

This guide shows how to run the ReadyBell backend (Spring Boot) and dashboard-ui (React) together locally for integrated testing.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│ ReadyBell System                                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Dashboard UI (React + Vite)                               │
│  ├─ http://localhost:5173/                                 │
│  └─ Communicates with backend via HTTP/SSE                 │
│                                                             │
│  Backend (Spring Boot)                                     │
│  ├─ http://localhost:8080/                                 │
│  ├─ REST API endpoints                                      │
│  ├─ SSE streams for real-time updates                      │
│  └─ Connects to PostgreSQL + Redis                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Prerequisites

Before starting, you need:

1. **Java 21** (Backend)
   ```bash
   java -version
   # Should show: openjdk version "21" or similar
   ```

2. **Node.js 18+** (UI)
   ```bash
   node -v
   npm -v
   ```

3. **PostgreSQL** (Database)
   ```bash
   brew install postgresql@15  # macOS
   # or
   sudo apt install postgresql  # Linux
   ```

4. **Redis** (Session/Cache Store)
   ```bash
   brew install redis  # macOS
   # or
   sudo apt install redis-server  # Linux
   ```

## Quick Start (All in One Terminal)

```bash
# 1. Start PostgreSQL
brew services start postgresql@15

# 2. Start Redis
redis-server

# 3. In a new terminal, build and run backend
cd /home/marian/IdeaProjects/ReadyBell
./gradlew bootRun

# 4. In another terminal, run frontend
cd /home/marian/IdeaProjects/ReadyBell/dashboard-ui
npm run dev

# 5. Open browser
# Dashboard: http://localhost:5173/
# Backend API: http://localhost:8080/
```

---

## Detailed Setup Instructions

### Step 1: Setup PostgreSQL Database

#### Option A: Using Brew (macOS)

```bash
# Install PostgreSQL
brew install postgresql@15

# Start PostgreSQL service
brew services start postgresql@15

# Create the readybell database
createdb readybell

# Verify connection
psql -d readybell -c "SELECT version();"
```

#### Option B: Using Docker

```bash
# Pull and run PostgreSQL container
docker run -d \
  --name readybell-postgres \
  -e POSTGRES_PASSWORD=qwer1234 \
  -e POSTGRES_DB=readybell \
  -p 3254:5432 \
  postgres:15

# Verify
docker exec readybell-postgres psql -U postgres -d readybell -c "SELECT version();"
```

#### Option C: Using System Package Manager (Linux)

```bash
# Install PostgreSQL
sudo apt install postgresql postgresql-contrib

# Start service
sudo systemctl start postgresql

# Create database
sudo -u postgres createdb readybell

# Verify
sudo -u postgres psql -d readybell -c "SELECT version();"
```

**Connection Details** (from application.yml):
- Host: localhost
- Port: 3254 (or 5432 if using standard PostgreSQL)
- Database: readybell
- Username: postgres
- Password: qwer1234

**Note**: If using port 5432, update `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/readybell
```

---

### Step 2: Setup Redis

#### Option A: Using Brew (macOS)

```bash
# Install Redis
brew install redis

# Start Redis service
brew services start redis

# Verify connection
redis-cli ping
# Should output: PONG
```

#### Option B: Using Docker

```bash
# Pull and run Redis container
docker run -d \
  --name readybell-redis \
  -p 6379:6379 \
  redis:7

# Verify
docker exec readybell-redis redis-cli ping
# Should output: PONG
```

#### Option C: Using System Package Manager (Linux)

```bash
# Install Redis
sudo apt install redis-server

# Start service
sudo systemctl start redis-server

# Verify
redis-cli ping
# Should output: PONG
```

**Connection Details** (from application.yml):
- Host: localhost
- Port: 6379

---

### Step 3: Build and Run Backend

```bash
# Navigate to project root
cd /home/marian/IdeaProjects/ReadyBell

# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

**Expected Output**:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.4.1)

2025-01-31 15:30:00.000  INFO ... : Starting ReadyBell application
...
2025-01-31 15:30:05.000  INFO ... : Tomcat started on port(s): 8080 (http)
2025-01-31 15:30:05.000  INFO ... : ReadyBell started in 5.123 seconds
```

**Backend is ready at**: http://localhost:8080/

---

### Step 4: Run Dashboard UI

In a **new terminal**:

```bash
cd /home/marian/IdeaProjects/ReadyBell/dashboard-ui

# Install dependencies (if not already done)
npm install

# Start development server
npm run dev
```

**Expected Output**:
```
  VITE v5.4.21  ready in 236 ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```

**UI is ready at**: http://localhost:5173/

---

## Testing the Integration

### 1. Verify Backend API

Open a new terminal and test API endpoints:

```bash
# Health check
curl http://localhost:8080/actuator/health

# List orders (with basic auth)
curl -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" \
  http://localhost:8080/api/orders

# Check logs
./gradlew bootRun | grep -i "order\|api\|request"
```

### 2. Verify UI Connection

1. Open **http://localhost:5173/** in browser
2. Open **DevTools** (F12)
3. Check **Network tab** → **XHR/Fetch**
4. Try an action (toggle theme, search, etc.)
5. Watch for API calls to `http://localhost:8080/`

### 3. Test Mock Data vs Real Data

The UI can work in two modes:

**Mode 1: Mock Data (Current Default)**
- Uses MSW (Mock Service Worker)
- No backend needed
- Good for UI development

**Mode 2: Real Backend (Toggle in code)**
- Calls real backend APIs
- Requires backend running
- For integration testing

To switch to real backend:
1. Edit `/dashboard-ui/src/mocks/handlers.ts`
2. Comment out or remove MSW handlers
3. Update API base URL in `/dashboard-ui/src/utils/api.ts`
4. Restart UI dev server

---

## Running Multiple Services (Terminal Breakdown)

For better organization, use separate terminals:

### Terminal 1: PostgreSQL
```bash
brew services start postgresql@15
# or keep running: postgres -D /usr/local/var/postgres
```

### Terminal 2: Redis
```bash
redis-server
```

### Terminal 3: Backend
```bash
cd /home/marian/IdeaProjects/ReadyBell
./gradlew bootRun
```

### Terminal 4: Frontend
```bash
cd /home/marian/IdeaProjects/ReadyBell/dashboard-ui
npm run dev
```

### Terminal 5: Monitoring (Optional)
```bash
# Watch backend logs
tail -f /tmp/spring-boot.log

# or watch frontend build
cd dashboard-ui && npm run build -- --watch
```

---

## Using Terminal Multiplexer (tmux)

If you prefer a single window with multiple panes:

```bash
# Start a tmux session
tmux new-session -d -s readybell -x 200 -y 50

# Create windows
tmux new-window -t readybell -n postgres
tmux new-window -t readybell -n redis
tmux new-window -t readybell -n backend
tmux new-window -t readybell -n frontend

# Start services
tmux send-keys -t readybell:postgres "brew services start postgresql@15" Enter
tmux send-keys -t readybell:redis "redis-server" Enter
tmux send-keys -t readybell:backend "cd /home/marian/IdeaProjects/ReadyBell && ./gradlew bootRun" Enter
tmux send-keys -t readybell:frontend "cd /home/marian/IdeaProjects/ReadyBell/dashboard-ui && npm run dev" Enter

# Attach to session
tmux attach -t readybell

# Navigate panes
# Ctrl+B, arrow keys to switch
# Ctrl+B, N/P for next/previous window
# Ctrl+B, X to kill pane
```

---

## API Endpoints Reference

### Customer Session API (Public)

```bash
# Start a customer session
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -d '{"qrToken": "restaurant-123"}'

# Get orders for session
curl http://localhost:8080/api/sessions/{sessionId}/orders

# Poll for updates
curl http://localhost:8080/api/sessions/{sessionId}/orders/poll

# SSE stream
curl -N http://localhost:8080/api/sessions/{sessionId}/orders/stream
```

### Order Management API (Staff Auth)

```bash
# List orders (with basic auth: username "staff", password "password")
curl -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" \
  http://localhost:8080/api/orders

# Create order
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "session-123", "items": [...]}'

# Update order status
curl -X PATCH http://localhost:8080/api/orders/{orderId}/status \
  -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" \
  -H "Content-Type: application/json" \
  -d '{"status": "READY"}'

# SSE stream
curl -N -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" \
  http://localhost:8080/api/orders/stream
```

---

## Troubleshooting

### Issue: Backend won't start - "Address already in use: bind"

**Solution**: Port 8080 is already in use
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or use different port (add to application.yml)
server.port: 8081
```

### Issue: PostgreSQL connection refused

**Solution**: PostgreSQL is not running
```bash
# Check if running
brew services list

# Start PostgreSQL
brew services start postgresql@15

# Or check if using Docker
docker ps | grep postgres
docker start readybell-postgres
```

### Issue: Redis connection timeout

**Solution**: Redis is not running
```bash
# Check if running
ps aux | grep redis

# Start Redis
redis-server

# Or check Docker
docker ps | grep redis
docker start readybell-redis
```

### Issue: Frontend can't reach backend API

**Solution**: CORS or proxy issue
1. Check backend is running: `curl http://localhost:8080/actuator/health`
2. Check frontend proxy config in `vite.config.ts`
3. Check for CORS headers in backend response
4. Check browser DevTools Network tab for actual errors

**Frontend proxy configuration** (dashboard-ui/vite.config.ts):
```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, '/api'),
    }
  }
}
```

### Issue: Database creation failed

**Solution**: Create manually
```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE readybell;
CREATE USER readybell WITH PASSWORD 'qwer1234';
GRANT ALL PRIVILEGES ON DATABASE readybell TO readybell;

# Exit
\q
```

### Issue: Build fails - "Unsupported class-file format"

**Solution**: Wrong Java version
```bash
java -version
# Must be 21+

# If not, install Java 21
brew install java@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## Performance & Monitoring

### Backend Health Check
```bash
curl http://localhost:8080/actuator/health
# Response: {"status":"UP","components":{...}}
```

### Check Database Connection
```bash
psql -U postgres -d readybell -c "SELECT 1"
```

### Monitor Redis
```bash
redis-cli INFO
redis-cli DBSIZE
```

### View Backend Logs
```bash
# During bootRun, logs appear in terminal
# Or redirect to file
./gradlew bootRun > backend.log 2>&1 &
tail -f backend.log
```

### Monitor Frontend Build
```bash
# DevTools Network tab shows all API calls
# Console shows any JavaScript errors
# Watch Vite output for rebuild triggers
```

---

## Development Workflow

### Making Backend Changes

1. Edit code in `src/main/java/`
2. Backend auto-reloads with DevTools (if enabled)
3. Restart if needed: `./gradlew bootRun`

### Making Frontend Changes

1. Edit code in `dashboard-ui/src/`
2. Vite hot-reloads automatically
3. Browser refreshes automatically

### Testing New Features

1. Create feature in UI (components, pages)
2. Mock API in `dashboard-ui/src/mocks/handlers.ts`
3. Test with mock data
4. Implement backend endpoint
5. Switch UI to real API
6. Test integration
7. Run both test suites:
   ```bash
   # Backend
   ./gradlew test
   
   # Frontend
   npm test
   ```

---

## Stopping Services

### Stop Backend
```bash
# Ctrl+C in backend terminal
# or
pkill -f "java.*bootRun"
```

### Stop Frontend
```bash
# Ctrl+C in frontend terminal
```

### Stop Redis
```bash
brew services stop redis
# or
redis-cli SHUTDOWN
```

### Stop PostgreSQL
```bash
brew services stop postgresql@15
# or
killall postgres
```

### Stop Docker Containers
```bash
docker stop readybell-postgres readybell-redis
docker rm readybell-postgres readybell-redis
```

---

## Cleaning Up

### Reset Database
```bash
dropdb readybell
createdb readybell
```

### Clear Redis Cache
```bash
redis-cli FLUSHALL
```

### Clean Build Artifacts
```bash
./gradlew clean
cd dashboard-ui && rm -rf dist node_modules && npm install
```

---

## Next Steps

- **API Documentation**: Check `/actuator/swagger-ui/` (if Swagger enabled)
- **Testing**: Run `./gradlew test` and `npm test`
- **Deployment**: See deployment guide
- **Performance**: Monitor with `./gradlew bootRun --debug`
- **Real Devices**: Use `npm run dev -- --host` to expose on network

---

## Quick Reference Checklist

- [ ] Java 21 installed
- [ ] Node 18+ installed  
- [ ] PostgreSQL running on port 3254 (or 5432)
- [ ] Redis running on port 6379
- [ ] Backend running on port 8080
- [ ] Frontend running on port 5173
- [ ] Browser can access http://localhost:5173/
- [ ] Backend health check passes: `curl http://localhost:8080/actuator/health`
- [ ] DevTools Network tab shows API calls
- [ ] Theme toggle works and persists
- [ ] All tests pass

---

## Getting Help

Check logs for errors:
```bash
# Backend errors
grep "ERROR" ~/.m2/logs/* 2>/dev/null || echo "Check backend terminal"

# Frontend errors
cd dashboard-ui && npm run build 2>&1 | grep -i error

# System logs
brew services list
ps aux | grep -E "java|node|redis|postgres"
```
