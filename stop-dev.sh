#!/bin/bash

# ReadyBell Development Environment Stop Script
# Cleanly stops all services

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║      ReadyBell Development Environment Shutdown Script         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"

# Stop backend
echo -e "\n${YELLOW}Stopping backend...${NC}"
pkill -f "java.*bootRun" || true
pkill -f "ReadyBell" || true
echo -e "${GREEN}✓ Backend stopped${NC}"

# Stop frontend
echo -e "${YELLOW}Stopping frontend...${NC}"
pkill -f "npm.*dev" || true
pkill -f "vite" || true
echo -e "${GREEN}✓ Frontend stopped${NC}"

# Stop Redis (optional - keep data)
echo -e "${YELLOW}Stopping Redis...${NC}"
brew services stop redis 2>/dev/null || redis-cli SHUTDOWN 2>/dev/null || true
echo -e "${GREEN}✓ Redis stopped${NC}"

# Stop PostgreSQL (optional - keep data)
echo -e "${YELLOW}Stopping PostgreSQL...${NC}"
brew services stop postgresql@15 2>/dev/null || true
echo -e "${GREEN}✓ PostgreSQL stopped${NC}"

echo -e "\n${GREEN}All services stopped cleanly${NC}"
echo -e "\n${YELLOW}To resume development:${NC}"
echo -e "  Run: ${GREEN}./start-dev.sh${NC}\n"
