# ReadyBell Quick Start Guide

## 🚀 30-Second Start

```bash
# Terminal 1: Start databases and services
cd /home/marian/IdeaProjects/ReadyBell
./start-dev.sh

# Terminal 2: Start backend
./gradlew bootRun

# Terminal 3: Start frontend
cd dashboard-ui
npm run dev

# Open browser
open http://localhost:5173
```

---

## 📋 Prerequisites

Make sure you have installed:

```bash
# Check Java
java -version          # Should be 21+

# Check Node
node -v               # Should be 18+

# Check PostgreSQL
brew services list    # Should show postgresql

# Check Redis
redis-cli ping        # Should return PONG
```

If any are missing, see **BACKEND_UI_SETUP.md** for installation instructions.

---

## 🔧 Manual Setup (if automated script doesn't work)

### 1. Start PostgreSQL
```bash
brew services start postgresql@15
# or
postgres -D /usr/local/var/postgres
```

### 2. Start Redis
```bash
redis-server
```

### 3. Create Database (first time only)
```bash
createdb -U postgres readybell
```

### 4. Start Backend
```bash
cd /home/marian/IdeaProjects/ReadyBell
./gradlew bootRun
```

Expected output:
```
Tomcat started on port(s): 8080 (http)
```

### 5. Start Frontend
```bash
cd /home/marian/IdeaProjects/ReadyBell/dashboard-ui
npm install  # First time only
npm run dev
```

Expected output:
```
➜  Local:   http://localhost:5173/
```

---

## ✅ Verify Everything Works

### Check Backend Health
```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP",...}
```

### Check Frontend Loading
```bash
curl http://localhost:5173/
# Should return HTML with React app
```

### Open Browser
```
http://localhost:5173/
```

---

## 🎯 What You Can Test Now

- **Theme Toggle**: Click sun/moon icon, reload page to verify persistence
- **Navigation**: Click sidebar links to navigate between pages
- **Table Features**: Sort columns, paginate, search, filter orders
- **Keyboard**: Tab through elements, use Enter/Space to activate
- **Responsive**: Press F12, toggle device emulation, resize browser
- **API Calls**: Open DevTools Network tab to see backend requests

---

## 🔌 API Endpoints

### Public (Customer)
```bash
POST   /api/sessions                      # Start session
GET    /api/sessions/{id}/orders          # Get orders
GET    /api/sessions/{id}/orders/stream   # SSE updates
```

### Staff (requires Basic Auth: staff:password)
```bash
GET    /api/orders                        # List orders
POST   /api/orders                        # Create order
PATCH  /api/orders/{id}/status            # Update status
GET    /api/orders/stream                 # SSE updates
```

Test with:
```bash
curl -H "Authorization: Basic c3RhZmY6cGFzc3dvcmQ=" \
  http://localhost:8080/api/orders
```

---

## 📊 Architecture

```
Browser (localhost:5173)
    ↓
Dashboard UI (React + Vite)
    ↓ HTTP/SSE
Backend (localhost:8080)
    ↓
PostgreSQL + Redis
```

---

## 🛑 Stopping Services

```bash
# All at once
./stop-dev.sh

# Or manually
Ctrl+C (backend terminal)
Ctrl+C (frontend terminal)
brew services stop redis
brew services stop postgresql@15
```

---

## 🆘 Troubleshooting

### Backend won't start
```bash
# Check if port in use
lsof -i :8080
kill -9 <PID>

# Or try different port
# Edit build.gradle.kts and add:
# bootRun { args = ['--server.port=8081'] }
```

### Frontend won't start
```bash
# Clear cache and reinstall
cd dashboard-ui
rm -rf node_modules .vite
npm install
npm run dev
```

### Can't connect to PostgreSQL
```bash
# Check if running
psql -U postgres

# Start it
brew services start postgresql@15

# Create database
createdb -U postgres readybell
```

### Can't connect to Redis
```bash
# Check if running
redis-cli ping

# Start it
redis-server
```

### Frontend can't reach backend
```bash
# Check backend is running
curl http://localhost:8080/actuator/health

# Check proxy config in vite.config.ts
# Look in DevTools Network tab for actual error
```

---

## 📁 Project Structure

```
/home/marian/IdeaProjects/ReadyBell/
├── src/                          # Backend (Java/Spring Boot)
│   ├── main/java/                # Application code
│   │   ├── session/              # Session bounded context
│   │   ├── ordering/             # Order bounded context
│   │   ├── subscription/         # Subscription context
│   │   ├── restaurant/           # Restaurant context
│   │   ├── notification/         # Notification context
│   │   ├── platformadmin/        # Admin context
│   │   └── platform/             # Spring configuration
│   └── test/java/                # Tests
├── dashboard-ui/                 # Frontend (React + TypeScript)
│   ├── src/
│   │   ├── components/           # UI components
│   │   ├── pages/                # Page components
│   │   ├── hooks/                # Custom hooks
│   │   ├── context/              # React Context
│   │   ├── mocks/                # MSW mock handlers
│   │   └── types/                # TypeScript types
│   └── package.json              # Frontend dependencies
├── build.gradle.kts              # Backend build config
├── start-dev.sh                  # Auto-start script
├── stop-dev.sh                   # Auto-stop script
└── README.md                     # Full documentation
```

---

## 🧪 Running Tests

### Backend Tests
```bash
cd /home/marian/IdeaProjects/ReadyBell
./gradlew test
```

### Frontend Tests
```bash
cd /home/marian/IdeaProjects/ReadyBell/dashboard-ui
npm test

# Keyboard navigation tests
npm test -- KeyboardNavigation.integration.test.tsx

# With coverage
npm test -- --coverage
```

---

## 📚 Documentation

- **Full Backend Setup**: `BACKEND_UI_SETUP.md`
- **UI Testing Guide**: `dashboard-ui/TESTING_GUIDE.md`
- **Build Summary**: `dashboard-ui/BUILD_AND_RUN_SUMMARY.md`
- **Requirements**: `.kiro/specs/dashboard-ui-theme-switcher/requirements.md`
- **Design Document**: `.kiro/specs/dashboard-ui-theme-switcher/design.md`

---

## 🎓 Learning Path

1. **Start**: Run `./start-dev.sh` and access http://localhost:5173/
2. **Explore**: Click around, try theme toggle, search, filters
3. **Inspect**: Open DevTools (F12) to see Network requests
4. **Test**: Run `npm test` to see test suite
5. **Read**: Check component code in `dashboard-ui/src/components/`
6. **Modify**: Edit a component and see hot-reload
7. **Build**: Create new feature branch and implement
8. **Test**: Write tests for new feature
9. **Review**: Submit PR for review

---

## 💡 Quick Tips

- **Hot reload**: Both backend and frontend auto-reload on save
- **Logs**: Frontend logs in browser console, backend in terminal
- **DevTools**: Press F12 to inspect elements and network
- **Device mode**: Press Ctrl+Shift+M to test mobile layout
- **React DevTools**: Install React DevTools browser extension
- **Storage**: Check Application → Local Storage for theme persistence

---

## 🚨 Common Issues

| Issue | Solution |
|-------|----------|
| Port 8080 in use | `lsof -i :8080` then `kill -9 <PID>` |
| Port 5173 in use | Frontend will use next available port |
| Database doesn't exist | `createdb -U postgres readybell` |
| Redis not responding | `redis-server` or `brew services start redis` |
| Frontend loads but shows blank | Check browser console for errors (F12) |
| Backend slow | Check if Redis/PostgreSQL are running |
| Tests fail | Clear node_modules: `rm -rf node_modules && npm install` |

---

## 📞 Getting Help

1. **Check logs**: Terminal output for errors
2. **Check console**: Browser DevTools console (F12)
3. **Check network**: DevTools Network tab for failed requests
4. **Check status**: `curl http://localhost:8080/actuator/health`
5. **Check services**: `brew services list` (macOS)

---

## ✨ Next Steps

- Explore the codebase in `dashboard-ui/src/`
- Read the requirements: `.kiro/specs/dashboard-ui-theme-switcher/`
- Run the test suite: `npm test`
- Try making a small change to a component
- Test your changes with the dev server
- Build for production: `npm run build`

---

**You're all set! Happy developing! 🎉**
