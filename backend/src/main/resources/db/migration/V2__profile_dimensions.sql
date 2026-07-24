-- ============================================
-- M8: 画像数据结构化存储 — 数据迁移脚本
--
-- 将 UserProfile.preferences 中的维度数据
-- 从字符串解析迁移到 profile_dimensions 表
-- ============================================

-- 1. 创建画像维度表
CREATE TABLE IF NOT EXISTS profile_dimensions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dimension_key VARCHAR(50) NOT NULL COMMENT '维度键: knowledgeLevel/cognitiveStyle/errorPatterns/motivation/timePreference/socialTendency/learningPace/completionRate',
    dimension_value VARCHAR(500) NOT NULL COMMENT '维度值: 中级/视觉型/概念混淆/...',
    dimension_label VARCHAR(50) DEFAULT NULL COMMENT '维度中文标签: 知识基础/认知风格/...',
    confidence DECIMAL(3,2) DEFAULT 1.0 COMMENT '置信度 0.00-1.00',
    source VARCHAR(30) DEFAULT 'MIGRATED' COMMENT '数据来源: QUESTIONNAIRE/DIALOGUE_INFERRED/PROGRESS/MANUAL/MIGRATED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_dimension (user_id, dimension_key),
    UNIQUE KEY uk_user_dimension (user_id, dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像维度表';

-- 2. 从 UserProfile.preferences 迁移现有数据
-- 解析 preferences 中的 key:value 格式并转换为维度记录
-- 注意: 此迁移脚本需配合 Java 端的 DataMigrationService 执行
-- 纯SQL无法可靠解析换行分隔的键值对

-- 3. 为 KnowledgeEntry 添加推荐所需字段
ALTER TABLE knowledge_entry
    ADD COLUMN IF NOT EXISTS tags VARCHAR(500) DEFAULT NULL COMMENT '逗号分隔标签: 入门,Python,编程基础',
    ADD COLUMN IF NOT EXISTS difficulty_level INT DEFAULT 3 COMMENT '难度等级 1-5: 1=入门 2=基础 3=进阶 4=挑战 5=专家';

CREATE INDEX IF NOT EXISTS idx_entry_tags ON knowledge_entry(category);
CREATE INDEX IF NOT EXISTS idx_entry_difficulty ON knowledge_entry(difficulty_level);

-- 4. 回滚脚本（如需回滚执行以下SQL）
-- DROP TABLE IF EXISTS profile_dimensions;
-- ALTER TABLE knowledge_entry DROP COLUMN IF EXISTS tags, DROP COLUMN IF EXISTS difficulty_level;
