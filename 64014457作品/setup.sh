#!/bin/bash
# =============================================
# 智学未来 -- 高等教育个性化多智能体学习系统
# 作品编号: 64014457
# 一键安装部署脚本 (Linux / macOS)
# =============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo "=============================================="
echo "  智学未来 -- 高等教育个性化多智能体学习系统"
echo "  作品编号: 64014457"
echo "=============================================="
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"
FRONTEND_DIR="$PROJECT_DIR/frontend"

# ============================================
# Step 1: Check environment
# ============================================
echo "[Step 1/6] Checking environment..."
echo ""

# Java 17+
echo -n "  Java... "
if command -v java &>/dev/null; then
    echo -e "${GREEN}OK${NC} ($(java -version 2>&1 | head -1))"
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "  Install JDK 17+: https://adoptium.net/"
    exit 1
fi

# Maven
echo -n "  Maven... "
if command -v mvn &>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "  Install Maven 3.8+: https://maven.apache.org/"
    exit 1
fi

# Node.js
echo -n "  Node.js... "
if command -v node &>/dev/null; then
    echo -e "${GREEN}OK${NC} ($(node --version))"
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "  Install Node.js 18+: https://nodejs.org/"
    exit 1
fi

# MySQL
echo -n "  MySQL... "
if command -v mysql &>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${YELLOW}WARNING - mysql CLI not found${NC}"
    echo "  Please ensure MySQL 8.0+ is installed and running"
fi

# Docker
HAS_DOCKER=0
echo -n "  Docker... "
if command -v docker &>/dev/null; then
    echo -e "${GREEN}OK${NC} ($(docker --version))"
    HAS_DOCKER=1
else
    echo -e "${YELLOW}NOT FOUND (Neo4j will be skipped)${NC}"
fi

echo ""
echo "  -- Environment check done --"
echo ""

# ============================================
# Step 2: Configuration
# ============================================
echo "[Step 2/6] Checking configuration..."
echo ""

if [ ! -f "$BACKEND_DIR/src/main/resources/application.yml" ]; then
    echo "  Creating application.yml from template..."
    cp "$BACKEND_DIR/src/main/resources/application.example.yml" \
       "$BACKEND_DIR/src/main/resources/application.yml"
    echo "  [OK] Created (API key pre-configured)"
fi

echo "  [OK] Configuration ready"
echo ""

# ============================================
# Step 3: Neo4j (Docker)
# ============================================
echo "[Step 3/6] Starting Neo4j graph database..."
echo ""

if [ "$HAS_DOCKER" -eq 1 ]; then
    if docker ps -a --format '{{.Names}}' | grep -q '^neo4j-eduagent$'; then
        if docker ps --format '{{.Names}}' | grep -q '^neo4j-eduagent$'; then
            echo "  [OK] Neo4j container already running"
        else
            echo "  Starting existing Neo4j container..."
            docker start neo4j-eduagent > /dev/null 2>&1
            echo "  [OK] Neo4j started"
        fi
    else
        echo "  Pulling Neo4j image and creating container..."
        cd "$PROJECT_DIR"
        if docker compose up -d 2>/dev/null; then
            echo "  [OK] Neo4j started (ports 7474/7687)"
        else
            # fallback to docker run
            docker run -d --name neo4j-eduagent \
                -p 7474:7474 -p 7687:7687 \
                -e NEO4J_AUTH=neo4j/password123 \
                -v neo4j_data:/data \
                neo4j:5 > /dev/null 2>&1
            if [ $? -eq 0 ]; then
                echo "  [OK] Neo4j started (ports 7474/7687)"
            else
                echo -e "  ${YELLOW}[WARNING] Neo4j failed to start (non-critical)${NC}"
            fi
        fi
    fi
else
    echo "  [SKIP] Docker not installed, Neo4j unavailable"
    echo "         Knowledge graph will still display, just won't persist"
fi

echo ""

# ============================================
# Step 4: Database
# ============================================
echo "[Step 4/6] Initializing MySQL database..."
echo ""

mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS eduagent_v2 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null && \
    echo "  [OK] Database eduagent_v2 ready" || \
    echo "  [WARNING] Please manually create database eduagent_v2"

mysql -u root -p123456 eduagent_v2 < "$BACKEND_DIR/src/main/resources/schema.sql" 2>/dev/null && \
    echo "  [OK] Schema imported" || \
    echo "  [WARNING] Schema import failed (will auto-init on first run)"

echo ""

# ============================================
# Step 5: Build
# ============================================
echo "[Step 5/6] Building project..."
echo ""

echo "  [5.1] Building backend (Maven)..."
cd "$BACKEND_DIR"
mvn clean package -DskipTests -q
echo "  [OK] Backend build complete"

JAR_FILE=$(find "$BACKEND_DIR/target" -name "*.jar" ! -name "*sources*" ! -name "*original*" | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "  [ERROR] JAR not found"
    exit 1
fi

echo "  [5.2] Installing frontend dependencies..."
cd "$FRONTEND_DIR"
npm install --silent 2>/dev/null || true
echo "  [OK] Dependencies installed"

echo "  [5.3] Building frontend..."
npm run build
echo "  [OK] Frontend build complete"

echo ""
echo "  -- Build complete --"
echo ""

# ============================================
# Step 6: Start
# ============================================
echo "[Step 6/6] Starting services..."
echo ""

echo "  Starting backend (port 8081)..."
java -jar "$JAR_FILE" &
BACKEND_PID=$!
echo "  [OK] Backend started (PID: $BACKEND_PID)"

sleep 10

echo "  Starting frontend dev server (port 5174)..."
cd "$FRONTEND_DIR"
npm run dev &
FRONTEND_PID=$!

sleep 5

echo ""
echo "=============================================="
echo "  Installation complete!"
echo ""
echo "  Frontend:  http://localhost:5174"
echo "  Backend:   http://localhost:8081"
if [ "$HAS_DOCKER" -eq 1 ]; then
echo "  Neo4j:     http://localhost:7474"
fi
echo ""
echo "  To stop:   kill $BACKEND_PID $FRONTEND_PID"
echo "  Project:   64014457"
echo "=============================================="
echo ""

# Open browser
if command -v xdg-open &>/dev/null; then
    xdg-open http://localhost:5174
elif command -v open &>/dev/null; then
    open http://localhost:5174
fi

wait
