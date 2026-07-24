# 智学未来——高等教育个性化多智能体学习系统

> 作品编号：**64014457**

基于多智能体协作架构的个性化学习系统，通过 AI 深度解析用户学习需求，自动规划学习路径、检索知识内容、生成练习题与知识图谱，为高等教育学习者提供一站式智能学习体验。

---

## 系统架构

```
用户输入 → 统筹解析智能体 → 路径规划智能体 → 知识库检索智能体
                                                    ↓
         ┌──────────────────────────────────────────┘
         ↓                  ↓                  ↓
  练习题生成智能体    图生成智能体      格式化总结智能体
         ↓                  ↓                  ↓
         └──────────────────┴──────────────────┘
                            ↓
                    前端学习面板展示
```

### 智能体协作流程

| 智能体 | 职责 |
|--------|------|
| 统筹解析智能体 | 解析用户自然语言学习需求，输出标准化需求标签 |
| 路径规划智能体 | 基于需求标签生成分阶段个性化学习路径 |
| 知识库检索智能体 | 按学习路径从知识库拉取匹配知识与多媒体资源 |
| 练习题生成智能体 | 根据知识点自动生成配套练习题 |
| 图生成智能体 | 将知识结构转化为可视化知识图谱 |
| 格式化总结智能体 | 对学习内容进行格式化总结输出 |

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Vue Router + vis-network + KaTeX |
| 后端 | Spring Boot 3.2 + Java 17 + MyBatis-Plus |
| 数据库 | MySQL 8.0 + Neo4j 5（图数据库，可选） |
| AI | DeepSeek API 多智能体协作 |
| 认证 | JWT + Spring Security Crypto |
| 容器化 | Docker Compose |

---

## 前置条件

| 软件 | 版本 | 下载 |
|------|------|------|
| JDK | 17+ | https://adoptium.net/ |
| Maven | 3.8+ | https://maven.apache.org/download.cgi |
| Node.js | 18+ | https://nodejs.org/ |
| MySQL | 8.0+ | https://dev.mysql.com/downloads/mysql/ |
| Docker | 24+（可选） | https://www.docker.com/products/docker-desktop/ |

MySQL 安装后确保服务已启动，root 密码设为 `123456`（如不同，修改 `backend/src/main/resources/application.yml`）。

Docker 可选——仅用于启动 Neo4j 图数据库。**没有 Docker 不影响核心功能**，知识图谱照常展示，只是不会持久化（刷新页面后图谱数据不保留）。

---

## 快速开始

### 自动安装（推荐）

**Windows**
```
双击运行 setup.bat
```

**Linux / macOS**
```bash
chmod +x setup.sh
./setup.sh
```

脚本自动完成：环境检查 → 配置 → Docker 启动 Neo4j → MySQL 建库 → Maven 构建后端 → npm 构建前端 → 启动服务 → 打开浏览器。

### 手动安装

#### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS eduagent_v2 DEFAULT CHARACTER SET utf8mb4;
```

```bash
mysql -u root -p123456 eduagent_v2 < backend/src/main/resources/schema.sql
```

#### 2. 启动 Neo4j（可选，需 Docker）

```bash
docker compose up -d
# 或:
docker run -d --name neo4j-eduagent -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/password123 neo4j:5
```

#### 3. 确认配置

文件 `backend/src/main/resources/application.yml`：

- `spring.datasource.password` — MySQL root 密码，默认 `123456`
- `ai.api-key` — 已预置 DeepSeek API 密钥，可直接使用
- `spring.neo4j.uri` — Neo4j 连接地址，默认 `bolt://localhost:7687`

#### 4. 启动后端（端口 8081）

```bash
cd backend
mvn clean package -DskipTests
java -jar target/edu-agent-backend-1.0.0.jar
```

看到 `Started EduAgentApplication` 即启动成功。如果 Neo4j 没启动，日志会显示 `Neo4j连接失败`，可忽略。

#### 5. 启动前端（端口 5174）

打开新终端：

```bash
cd frontend
npm install
npm run dev
```

#### 6. 访问

浏览器打开 `http://localhost:5174`，注册后即可使用。

---

## 项目结构

```
banchenping/
├── backend/                        # Spring Boot 后端
│   ├── src/main/java/com/eduagent/
│   │   ├── agent/                  # 智能体基础框架（AgentContext, AgentRegistry, BaseAgent）
│   │   ├── config/                 # 配置（线程池、JWT拦截器、数据初始化）
│   │   ├── controller/             # REST API 控制器
│   │   ├── entity/                 # 数据库实体
│   │   ├── mapper/                 # MyBatis-Plus Mapper
│   │   ├── model/dto/              # 请求/响应 DTO
│   │   ├── service/                # 业务服务 & 各智能体实现
│   │   └── util/                   # 工具类（JWT等）
│   └── src/main/resources/
│       ├── application.yml         # 应用配置
│       └── schema.sql              # 数据库初始化脚本
├── frontend/                       # Vue 3 前端
│   ├── src/
│   │   ├── components/             # 公共组件
│   │   ├── views/                  # 页面视图
│   │   ├── router/                 # 路由配置
│   │   ├── api/                    # API 请求封装
│   │   └── utils/                  # 工具函数
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
├── scripts/                        # 知识库构建脚本
├── data/                           # 数据文件
├── 智能体prompt参考文档/            # 各智能体系统提示词参考
├── 64014457作品/                   # 作品提交目录
│   ├── docker-compose.yml
│   ├── setup.bat / setup.sh
│   └── README.md
└── docker-compose.yml              # Neo4j 容器编排
```

---

## 常见问题

| 问题 | 解决 |
|------|------|
| 数据库连接失败 | 确认 MySQL 已启动，密码与 `application.yml` 一致 |
| 数据库不存在 | 先执行 `CREATE DATABASE eduagent_v2` |
| 前端页面空白 | 确认后端 8081 端口正常运行 |
| Neo4j 连接失败 | 可忽略，不影响核心功能；如需持久化，`docker compose up -d` |
| AI 无输出 | API 密钥已预置，如过期需更换 `ai.api-key` |

---

## 许可证

本项目仅供学习交流使用。详见 [LICENSE](LICENSE) 文件。
