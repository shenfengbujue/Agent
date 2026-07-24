# 智学未来——高等教育个性化多智能体学习系统

> 作品编号：**64014457**

---

## 前置条件

| 软件 | 版本 | 下载 |
|------|------|------|
| JDK | 17+ | https://adoptium.net/ |
| Maven | 3.8+ | https://maven.apache.org/download.cgi |
| Node.js | 18+ | https://nodejs.org/ |
| MySQL | 8.0+ | https://dev.mysql.com/downloads/mysql/ |
| Docker | 24+ (可选) | https://www.docker.com/products/docker-desktop/ |

MySQL 安装后确保服务已启动，root 密码设为 `123456`（如不同，修改 `backend/src/main/resources/application.yml`）。

Docker 可选 —— 仅用于启动 Neo4j 图数据库。**没有 Docker 不影响核心功能**，知识图谱照常展示，只是不会持久化（刷新页面后图谱数据不保留）。

---

## 一、自动安装（推荐）

### Windows

```
双击运行 setup.bat
```

### Linux / macOS

```bash
chmod +x setup.sh
./setup.sh
```

脚本自动完成：环境检查 → 配置 → Docker启动Neo4j → MySQL建库 → Maven构建后端 → npm构建前端 → 启动服务 → 打开浏览器。

---

## 二、手动安装

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS eduagent_v2 DEFAULT CHARACTER SET utf8mb4;
```

```bash
mysql -u root -p123456 eduagent_v2 < backend/src/main/resources/schema.sql
```

### 2. 启动 Neo4j（可选，需 Docker）

```bash
docker compose up -d
# 或:
docker run -d --name neo4j-eduagent -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/password123 neo4j:5
```

### 3. 确认配置

文件 `backend/src/main/resources/application.yml`：

- `spring.datasource.password` — MySQL root 密码，默认 `123456`
- `ai.api-key` — 已预置 DeepSeek API 密钥，可直接使用
- `spring.neo4j.uri` — Neo4j 连接地址，默认 `bolt://localhost:7687`

### 4. 启动后端（端口 8081）

```bash
cd backend
mvn clean package -DskipTests
java -jar target/edu-agent-backend-1.0.0.jar
```

看到 `Started EduAgentApplication` 即启动成功。如果 Neo4j 没启动，日志会显示 `Neo4j连接失败`，可忽略。

### 5. 启动前端（端口 5174）

打开新终端：

```bash
cd frontend
npm install
npm run dev
```

### 6. 访问

浏览器打开 `http://localhost:5174`，注册后即可使用。

---

## 常见问题

| 问题 | 解决 |
|------|------|
| 数据库连接失败 | 确认 MySQL 已启动，密码与 `application.yml` 一致 |
| 数据库不存在 | 先执行 `CREATE DATABASE eduagent_v2` |
| 前端页面空白 | 确认后端 8081 端口正常运行 |
| Neo4j 连接失败 | 可忽略，不影响核心功能；如需持久化，`docker compose up -d` |
| AI 无输出 | API 密钥已预置，如过期需更换 `ai.api-key` |
