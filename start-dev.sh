#!/bin/bash

# ReadyBell Development Environment Startup Script
# Starts PostgreSQL, Redis, Backend, and Frontend in one go

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        ReadyBell Development Environment Startup Script        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}✗ Java not found. Please install Java 21${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | grep -oE "version \"[0-9]+" | cut -d'"' -f2)
if [[ ! $JAVA_VERSION =~ ^2[1-9]$ ]]; then
    echo -e "${RED}✗ Java 21+ required (found: $JAVA_VERSION)${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java $JAVA_VERSION${NC}"

# Check Node
if ! command -v node &> /dev/null; then
    echo -e "${RED}✗ Node.js not found. Please install Node.js 18+${NC}"
    exit 1
fi
NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [[ $NODE_VERSION -lt 18 ]]; then
    echo -e "${RED}✗ Node 18+ required (found: $NODE_VERSION)${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Node $(node -v)${NC}"

# Check PostgreSQL
if ! command -v psql &> /dev/null; then
    echo -e "${RED}✗ PostgreSQL not found${NC}"
    echo "   Install with: brew install postgresql@15 (macOS)"
    echo "   Or: sudo apt install postgresql (Linux)"
    exit 1
fi
echo -e "${GREEN}✓ PostgreSQL${NC}"

# Check Redis
if ! command -v redis-cli &> /dev/null; then
    echo -e "${RED}✗ Redis not found${NC}"
    echo "   Install with: brew install redis (macOS)"
    echo "   Or: sudo apt install redis-server (Linux)"
    exit 1
fi
echo -e "${GREEN}✓ Redis${NC}"

# Check ports
echo -e "\n${YELLOW}Checking ports...${NC}"

check_port() {
    local port=$1
    local service=$2
    if lsof -i ":$port" > /dev/null 2>&1; then
        echo -e "${YELLOW}⚠ Port $port ($service) already in use${NC}"
        return 1
    else
        echo -e "${GREEN}✓ Port $port available ($service)${NC}"
        return 0
    fi
}

check_port 5432 "PostgreSQL" || true
check_port 6379 "Redis" || true
check_port 8080 "Backend" || true
check_port 5173 "Frontend" || true

# Create project directory reference
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UI_DIR="$PROJECT_DIR/dashboard-ui"

# Start PostgreSQL
echo -e "\n${YELLOW}Starting PostgreSQL...${NC}"
if brew services list 2>/dev/null | grep -q "postgresql"; then
    brew services start postgresql@15 2>/dev/null || true
    echo -e "${GREEN}✓ PostgreSQL started${NC}"
elif command -v pg_ctl &> /dev/null; then
    # Manual start if brew services not available
    echo -e "${GREEN}✓ PostgreSQL (manual mode)${NC}"
else
    echo -e "${YELLOW}⚠ Couldn't auto-start PostgreSQL${NC}"
fi

sleep 2

# Verify database exists
if psql -U postgres -lqt 2>/dev/null | cut -d'|' -f1 | grep -qw readybell; then
    echo -e "${GREEN}✓ Database 'readybell' exists${NC}"
else
    echo -e "${YELLOW}Creating database 'readybell'...${NC}"
    createdb -U postgres readybell 2>/dev/null || echo "Database may already exist"
fi

# Start Redis
echo -e "\n${YELLOW}Starting Redis...${NC}"
if brew services list 2>/dev/null | grep -q "redis"; then
    brew services start redis 2>/dev/null || true
    echo -e "${GREEN}✓ Redis started${NC}"
else
    echo -e "${YELLOW}ℹ Please start Redis separately: redis-server${NC}"
fi

sleep 2

# Verify Redis
if redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis responsive${NC}"
else
    echo -e "${YELLOW}⚠ Redis not responding - start manually: redis-server${NC}"
fi

# Build backend
echo -e "\n${YELLOW}Building backend...${NC}"
cd "$PROJECT_DIR"
./gradlew build -x test > /dev/null 2>&1
echo -e "${GREEN}✓ Backend built${NC}"

# Install frontend dependencies
echo -e "\n${YELLOW}Installing frontend dependencies...${NC}"
cd "$UI_DIR"
npm install > /dev/null 2>&1
echo -e "${GREEN}✓ Frontend dependencies installed${NC}"

# Display startup instructions
echo -e "\n${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                   All Systems Ready!                            ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"

echo -e "\n${YELLOW}Next steps - Start each service in a separate terminal:${NC}"

echo -e "\n${BLUE}Terminal 1: Backend${NC}"
echo -e "  cd $PROJECT_DIR"
echo -e "  ${GREEN}./gradlew bootRun${NC}"

echo -e "\n${BLUE}Terminal 2: Frontend${NC}"
echo -e "  cd $UI_DIR"
echo -e "  ${GREEN}npm run dev${NC}"

echo -e "\n${BLUE}Then open your browser:${NC}"
echo -e "  ${GREEN}http://localhost:5173${NC}"

echo -e "\n${YELLOW}API endpoints:${NC}"
echo -e "  Backend API: ${GREEN}http://localhost:8080${NC}"
echo -e "  Health Check: ${GREEN}curl http://localhost:8080/actuator/health${NC}"

echo -e "\n${YELLOW}Useful commands:${NC}"
echo -e "  View logs: tail -f ${PROJECT_DIR}/backend.log"
echo -e "  Stop services: ./stop-dev.sh"
echo -e "  Reset database: dropdb readybell && createdb readybell"
echo -e "  Clear Redis: redis-cli FLUSHALL"

echo -e "\n${YELLOW}Documentation:${NC}"
echo -e "  Full setup guide: ${GREEN}BACKEND_UI_SETUP.md${NC}"
echo -e "  UI testing guide: ${GREEN}dashboard-ui/TESTING_GUIDE.md${NC}"

echo -e "\n${GREEN}Ready to develop!${NC}\n"
