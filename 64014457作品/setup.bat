@echo off
chcp 65001 >nul
title 智学未来 - 安装程序 64014457

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║     智学未来 -- 高等教育个性化多智能体学习系统               ║
echo ║     作品编号: 64014457                                       ║
echo ║     一键安装部署脚本 (Windows)                               ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

set "PROJECT_DIR=%~dp0"
set "BACKEND_DIR=%PROJECT_DIR%backend"
set "FRONTEND_DIR=%PROJECT_DIR%frontend"

:: ============================================
:: Step 1: 环境检查
:: ============================================
echo [Step 1/6] 检查运行环境...
echo.

:: Java 17+
echo   检测 Java...
java -version 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo   [错误] 未检测到 Java，请安装 JDK 17 或更高版本
    echo   下载地址: https://adoptium.net/
    pause
    exit /b 1
)
echo   [OK] Java 已安装

:: Maven
echo   检测 Maven...
mvn --version 2>nul >nul
if %ERRORLEVEL% NEQ 0 (
    echo   [错误] 未检测到 Maven，请安装 Maven 3.8+
    echo   下载地址: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)
echo   [OK] Maven 已安装

:: Node.js 18+
echo   检测 Node.js...
node --version 2>nul >nul
if %ERRORLEVEL% NEQ 0 (
    echo   [错误] 未检测到 Node.js，请安装 Node.js 18+
    echo   下载地址: https://nodejs.org/
    pause
    exit /b 1
)
for /f "tokens=*" %%i in ('node --version') do set NV=%%i
echo   [OK] Node.js: %NV%

:: MySQL
echo   检测 MySQL...
mysql --version 2>nul >nul
if %ERRORLEVEL% NEQ 0 (
    echo   [警告] 未检测到 MySQL 命令行，请确认 MySQL 8.0+ 已安装并启动
) else (
    echo   [OK] MySQL 已安装
)

:: Docker
echo   检测 Docker...
docker --version 2>nul >nul
if %ERRORLEVEL% NEQ 0 (
    echo   [提示] 未检测到 Docker，Neo4j 图数据库将跳过（不影响核心功能）
    echo          安装 Docker Desktop 后可获得知识图谱持久化能力
    echo          下载: https://www.docker.com/products/docker-desktop/
    set "HAS_DOCKER=0"
) else (
    for /f "tokens=*" %%i in ('docker --version') do set DV=%%i
    echo   [OK] Docker: %DV%
    set "HAS_DOCKER=1"
)

echo.
echo   -- 环境检查完毕 --
echo.

:: ============================================
:: Step 2: 配置
:: ============================================
echo [Step 2/6] 检查配置文件...
echo.

if not exist "%BACKEND_DIR%\src\main\resources\application.yml" (
    echo   [警告] application.yml 不存在，从模板创建...
    copy "%BACKEND_DIR%\src\main\resources\application.example.yml" "%BACKEND_DIR%\src\main\resources\application.yml" >nul 2>&1
    echo   [OK] 已创建配置文件，API密钥已预置，可直接使用
) else (
    echo   [OK] 配置文件已存在
)

echo.

:: ============================================
:: Step 3: Neo4j (Docker)
:: ============================================
echo [Step 3/6] 启动 Neo4j 图数据库...
echo.

if "%HAS_DOCKER%"=="1" (
    :: Check if Neo4j container already exists
    docker ps -a --format "{{.Names}}" | findstr /C:"neo4j-eduagent" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        :: Container exists, check if running
        docker ps --format "{{.Names}}" | findstr /C:"neo4j-eduagent" >nul 2>&1
        if %ERRORLEVEL% EQU 0 (
            echo   [OK] Neo4j 容器已在运行
        ) else (
            echo   正在启动已有 Neo4j 容器...
            docker start neo4j-eduagent >nul 2>&1
            echo   [OK] Neo4j 容器已启动
        )
    ) else (
        echo   正在拉取 Neo4j 镜像并创建容器...
        cd /d "%PROJECT_DIR%"
        docker compose up -d 2>nul
        if %ERRORLEVEL% EQU 0 (
            echo   [OK] Neo4j 已启动 (端口 7474/7687)
        ) else (
            :: fallback to docker run
            docker run -d --name neo4j-eduagent ^
                -p 7474:7474 -p 7687:7687 ^
                -e NEO4J_AUTH=neo4j/password123 ^
                -v neo4j_data:/data ^
                neo4j:5 >nul 2>&1
            if %ERRORLEVEL% EQU 0 (
                echo   [OK] Neo4j 已启动 (端口 7474/7687)
            ) else (
                echo   [警告] Neo4j 启动失败，知识图谱持久化不可用（其余功能正常）
            )
        )
    )
) else (
    echo   [跳过] Docker 未安装，Neo4j 不启动（知识图谱仍可展示，仅不持久化）
)

echo.

:: ============================================
:: Step 4: 数据库
:: ============================================
echo [Step 4/6] 初始化 MySQL 数据库...
echo.

mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS eduagent_v2 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo   [警告] 自动建库失败，请手动创建 eduagent_v2 数据库
) else (
    echo   [OK] 数据库 eduagent_v2 已就绪
    mysql -u root -p123456 eduagent_v2 < "%BACKEND_DIR%\src\main\resources\schema.sql" 2>nul
    echo   [OK] 表结构已导入
)

echo.

:: ============================================
:: Step 5: 构建
:: ============================================
echo [Step 5/6] 构建项目...
echo.

echo   [5.1] 构建后端 (Maven)...
cd /d "%BACKEND_DIR%"
call mvn clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo   [错误] 后端构建失败，请检查 Maven 和网络
    pause
    exit /b 1
)
echo   [OK] 后端构建成功

for /f "delims=" %%f in ('dir /s /b "%BACKEND_DIR%\target\*.jar" 2^>nul ^| findstr /v "original" ^| findstr /v "sources"') do set JAR_FILE=%%f

echo   [5.2] 安装前端依赖...
cd /d "%FRONTEND_DIR%"
call npm install --silent 2>nul
echo   [OK] 依赖已安装

echo   [5.3] 构建前端...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo   [错误] 前端构建失败
    pause
    exit /b 1
)
echo   [OK] 前端构建成功

echo.
echo   -- 构建完成 --
echo.

:: ============================================
:: Step 6: 启动
:: ============================================
echo [Step 6/6] 启动服务...
echo.

echo   启动后端 (端口 8081)...
start "智学未来-后端" java -jar "%JAR_FILE%"
echo   [OK] 后端已启动

echo   等待后端就绪 (Neo4j 连接可能需要几秒)...
timeout /t 10 /nobreak >nul

echo   启动前端开发服务器 (端口 5174)...
cd /d "%FRONTEND_DIR%"
start "智学未来-前端" cmd /c "npm run dev"

timeout /t 5 /nobreak >nul

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║     安装完成!                                                ║
echo ║                                                              ║
echo ║     前端:      http://localhost:5174                         ║
echo ║     后端 API:  http://localhost:8081                         ║
echo ║     Neo4j:     http://localhost:7474   (如已启动)            ║
echo ║                                                              ║
echo ║     作品编号: 64014457                                       ║
echo ║     关闭命令行窗口即可停止服务                               ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

start http://localhost:5174
pause
