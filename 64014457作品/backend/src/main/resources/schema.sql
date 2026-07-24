-- =============================================
-- EduAgent 智能学习多智能体系统 数据库初始化脚本 (MySQL)
-- =============================================

-- =============================================
-- 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    email VARCHAR(100),
    role VARCHAR(20) DEFAULT 'student',
    status VARCHAR(20) DEFAULT 'active',
    profile_data TEXT,
    learning_goal VARCHAR(100),
    time_availability VARCHAR(100),
    learning_style VARCHAR(100),
    work_pain_points VARCHAR(500),
    skill_level VARCHAR(100),
    exam_time VARCHAR(100),
    knowledge_level VARCHAR(100),
    weak_points VARCHAR(500),
    motivation VARCHAR(100),
    achievement_style VARCHAR(100),
    social_willingness VARCHAR(100),
    frustration_handling VARCHAR(100),
    created_at DATETIME,
    last_login DATETIME,
    login_days INT DEFAULT 0,
    last_login_date VARCHAR(20),
    level VARCHAR(20) DEFAULT 'L1',
    profile_updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 学习小组表
-- =============================================
CREATE TABLE IF NOT EXISTS study_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(100),
    description TEXT,
    course VARCHAR(100),
    creator_id VARCHAR(50),
    member_count INT DEFAULT 1,
    max_members INT DEFAULT 50,
    post_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 小组成员表
-- =============================================
CREATE TABLE IF NOT EXISTS group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT,
    user_id VARCHAR(50),
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 学习帖子表
-- =============================================
CREATE TABLE IF NOT EXISTS study_post (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT,
    user_id VARCHAR(50),
    title VARCHAR(200),
    content TEXT,
    post_type VARCHAR(20) DEFAULT 'DISCUSSION',
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    is_pinned TINYINT DEFAULT 0,
    is_solved TINYINT DEFAULT 0,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 学习评论表
-- =============================================
CREATE TABLE IF NOT EXISTS study_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT,
    user_id VARCHAR(50),
    content TEXT,
    parent_id BIGINT,
    is_accepted TINYINT DEFAULT 0,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 点赞记录表
-- =============================================
CREATE TABLE IF NOT EXISTS like_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50),
    target_type VARCHAR(20),
    target_id BIGINT,
    created_at DATETIME,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 同伴辅导表
-- =============================================
CREATE TABLE IF NOT EXISTS peer_tutoring (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id VARCHAR(50),
    tutor_id VARCHAR(50),
    topic VARCHAR(200),
    description TEXT,
    status VARCHAR(20) DEFAULT 'OPEN',
    rating INT,
    feedback TEXT,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 用户成就表
-- =============================================
CREATE TABLE IF NOT EXISTS user_achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50),
    achievement_type VARCHAR(50),
    title VARCHAR(100),
    description VARCHAR(255),
    icon_url VARCHAR(255),
    earned_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 学习目标表
-- =============================================
CREATE TABLE IF NOT EXISTS study_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    goal_icon VARCHAR(50),
    category VARCHAR(100),
    progress INT DEFAULT 0,
    color VARCHAR(100),
    resources TEXT,
    current_resource_index INT DEFAULT 0,
    completed_resources TEXT,
    learning_path TEXT,
    current_stage_index INT DEFAULT 0,
    last_study_time DATETIME,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 资源统计表
-- =============================================
CREATE TABLE IF NOT EXISTS resource_stats (
    resource_id BIGINT PRIMARY KEY,
    like_count INT DEFAULT 0,
    view_count INT DEFAULT 0,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 资源浏览记录表
-- =============================================
CREATE TABLE IF NOT EXISTS resource_view_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    viewed_at DATETIME,
    UNIQUE KEY uk_resource_user_view (resource_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 小组资源表
-- =============================================
CREATE TABLE IF NOT EXISTS group_resources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    uploader_id VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    resource_type VARCHAR(50),
    file_url VARCHAR(500),
    file_name VARCHAR(200),
    file_size BIGINT DEFAULT 0,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 知识库表
-- =============================================
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    domain VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 知识条目表
-- =============================================
CREATE TABLE IF NOT EXISTS knowledge_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(100),
    sub_module VARCHAR(100),
    metadata TEXT,
    embedding TEXT,
    entry_type VARCHAR(50) DEFAULT 'KNOWLEDGE',
    plan_data JSON DEFAULT NULL,
    owner_id BIGINT DEFAULT NULL,
    tags VARCHAR(500) DEFAULT NULL,
    difficulty_level VARCHAR(20) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (base_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 意图规则表
-- =============================================
CREATE TABLE IF NOT EXISTS intent_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pattern VARCHAR(500) NOT NULL,
    pattern_type VARCHAR(20) DEFAULT 'KEYWORD',
    intent_type VARCHAR(50) NOT NULL,
    target_base_id BIGINT,
    priority INT DEFAULT 10,
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (target_base_id) REFERENCES knowledge_base(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 数据源表
-- =============================================
CREATE TABLE IF NOT EXISTS data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    url VARCHAR(500),
    config TEXT,
    base_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (base_id) REFERENCES knowledge_base(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 用户画像表
-- =============================================
CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    keywords TEXT,
    interests TEXT,
    preferences TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 聊天历史表
-- =============================================
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    conversation_id BIGINT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_history_user_agent (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 种子数据：知识库
-- =============================================
INSERT INTO knowledge_base (id, name, domain, description, status) VALUES
(1, '通用知识库', 'GENERAL', '处理日常闲聊、意图识别、简单常识问答', 'ACTIVE'),
(2, 'Python文档库', 'PYTHON', 'Python编程相关知识、API文档、代码示例', 'ACTIVE'),
(3, '雅思词汇库', 'IELTS', '雅思考试核心词汇、例句、记忆技巧', 'ACTIVE'),
(4, '公文写作模板库', 'OFFICIAL_WRITING', '各类公文写作模板、格式规范', 'ACTIVE');

-- =============================================
-- 种子数据：意图规则
-- =============================================
INSERT INTO intent_rule (pattern, pattern_type, intent_type, target_base_id, priority, description)
VALUES ('python|Python|编程|代码|语法|函数|类', 'KEYWORD', 'PYTHON', 2, 1, 'Python相关问题');

INSERT INTO intent_rule (pattern, pattern_type, intent_type, target_base_id, priority, description)
VALUES ('雅思|IELTS|词汇|单词|英语', 'KEYWORD', 'IELTS', 3, 1, '雅思英语相关问题');

INSERT INTO intent_rule (pattern, pattern_type, intent_type, target_base_id, priority, description)
VALUES ('公文|报告|通知|函|请示|写作', 'KEYWORD', 'OFFICIAL_WRITING', 4, 1, '公文写作相关问题');

-- =============================================
-- 每日学习内容表
-- =============================================
CREATE TABLE IF NOT EXISTS daily_learning_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    stage_index INT NOT NULL DEFAULT 0,
    day_index INT NOT NULL DEFAULT 1,
    knowledge JSON COMMENT '知识点内容',
    exercises JSON COMMENT '随堂练习',
    comprehensive_test JSON COMMENT '综合测试',
    status VARCHAR(20) DEFAULT 'PENDING',
    score INT DEFAULT NULL,
    time_spent INT DEFAULT 0,
    weak_points JSON DEFAULT NULL,
    generated_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_goal_stage_day (goal_id, stage_index, day_index),
    INDEX idx_goal_stage (goal_id, stage_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- 对话会话表
-- =============================================
CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) DEFAULT '新对话',
    agent_id BIGINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- profile_dimensions 画像维度表
-- =============================================
CREATE TABLE IF NOT EXISTS profile_dimensions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dimension_key VARCHAR(64) NOT NULL,
    dimension_value VARCHAR(255),
    dimension_label VARCHAR(100),
    confidence DOUBLE DEFAULT 1.0,
    source VARCHAR(50) DEFAULT 'MANUAL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_dim (user_id, dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================
-- push_records 推送记录表
-- =============================================
CREATE TABLE IF NOT EXISTS push_records (
    id VARCHAR(16) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200),
    reason VARCHAR(500),
    push_type VARCHAR(50),
    is_read TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;