package com.eduagent.service;

import com.eduagent.entity.ChatHistory;
import com.eduagent.mapper.ChatHistoryMapper;
import com.eduagent.repository.StudyGoalRepository;
import com.eduagent.mapper.UserProfileMapper;
import com.eduagent.agent.AgentContext;
import com.eduagent.entity.StudyGoal;
import com.eduagent.entity.UserProfile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆系统 —— 短期记忆 + 长期记忆管理
 *
 * 短期记忆: 读取最近N条对话，提取关键主题注入AgentContext
 * 长期记忆: 用户画像 + 学习进度统计 + 知识掌握度矩阵
 */
@Slf4j
@Service
public class MemoryService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final UserProfileMapper userProfileMapper;
    private final StudyGoalRepository studyGoalRepository;
    private final UserProfileService userProfileService;
    private final AIService aiService;

    /** 短期记忆保留条数 */
    private static final int SHORT_TERM_MEMORY_SIZE = 10;

    /** 压缩阈值：超过此条数触发LLM摘要压缩 */
    @Value("${memory.compress-threshold:50}")
    private int compressThreshold;

    /** 每次压缩处理的旧消息条数 */
    @Value("${memory.compress-batch-size:40}")
    private int compressBatchSize;

    public MemoryService(ChatHistoryMapper chatHistoryMapper,
                        UserProfileMapper userProfileMapper,
                        StudyGoalRepository studyGoalRepository,
                        UserProfileService userProfileService,
                        AIService aiService) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.userProfileMapper = userProfileMapper;
        this.studyGoalRepository = studyGoalRepository;
        this.userProfileService = userProfileService;
        this.aiService = aiService;
    }

    /**
     * 加载用户的短期记忆（最近N条对话的主题摘要）
     */
    public List<String> loadRecentTopics(Long userId) {
        if (userId == null) return List.of();

        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getUserId, userId)
                   .orderByDesc(ChatHistory::getCreatedAt)
                   .last("LIMIT " + SHORT_TERM_MEMORY_SIZE);

            List<ChatHistory> recentChats = chatHistoryMapper.selectList(wrapper);

            // 简单提取: 从最近对话中提取关键词作为主题
            Set<String> topics = new LinkedHashSet<>();
            for (ChatHistory chat : recentChats) {
                if (chat.getContent() != null && chat.getContent().length() > 5) {
                    String topic = extractMainTopic(chat.getContent());
                    if (topic != null && !topic.isEmpty()) {
                        topics.add(topic);
                    }
                }
            }
            return new ArrayList<>(topics);

        } catch (Exception e) {
            log.warn("加载短期记忆失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 加载用户的长期记忆（完整画像 + 学习统计）
     */
    public Map<String, Object> loadLongTermMemory(Long userId) {
        Map<String, Object> memory = new LinkedHashMap<>();

        if (userId == null) return memory;

        try {
            // 用户画像
            UserProfile profile = userProfileMapper.selectByUserId(userId);
            if (profile != null) {
                memory.put("keywords", profile.getKeywords());
                memory.put("interests", profile.getInterests());
                memory.put("preferences", profile.getPreferences());
            }

            // 学习目标统计
            LambdaQueryWrapper<StudyGoal> goalWrapper = new LambdaQueryWrapper<>();
            goalWrapper.eq(StudyGoal::getUserId, userId);
            List<StudyGoal> goals = studyGoalRepository.selectList(goalWrapper);

            int totalGoals = goals.size();
            int completedGoals = (int) goals.stream().filter(g -> g.getProgress() != null && g.getProgress() >= 100).count();
            int inProgressGoals = (int) goals.stream().filter(g -> g.getProgress() != null && g.getProgress() > 0 && g.getProgress() < 100).count();
            double avgProgress = goals.isEmpty() ? 0 : goals.stream().mapToInt(g -> g.getProgress() != null ? g.getProgress() : 0).average().orElse(0);

            // 学习主题分布
            Map<String, Long> categoryDistribution = goals.stream()
                    .filter(g -> g.getCategory() != null)
                    .collect(Collectors.groupingBy(StudyGoal::getCategory, Collectors.counting()));

            memory.put("totalGoals", totalGoals);
            memory.put("completedGoals", completedGoals);
            memory.put("inProgressGoals", inProgressGoals);
            memory.put("avgProgress", Math.round(avgProgress));
            memory.put("completionRate", totalGoals > 0 ? Math.round((double) completedGoals / totalGoals * 100) : 0);
            memory.put("categoryDistribution", categoryDistribution);

            // 最近学习时间
            StudyGoal latestGoal = goals.stream()
                    .filter(g -> g.getLastStudyTime() != null)
                    .max(Comparator.comparing(StudyGoal::getLastStudyTime))
                    .orElse(null);
            memory.put("lastStudyTime", latestGoal != null ? latestGoal.getLastStudyTime() : null);

            log.debug("长期记忆加载完成: userId={}, goals={}, completed={}", userId, totalGoals, completedGoals);

        } catch (Exception e) {
            log.warn("加载长期记忆失败: {}", e.getMessage());
        }

        return memory;
    }

    /**
     * 构建完整的AgentContext（含短期+长期记忆）
     */
    public AgentContext buildAgentContext(String query, Long userId) {
        AgentContext.AgentContextBuilder builder = AgentContext.builder()
                .query(query)
                .userId(userId)
                .previousOutputs(new LinkedHashMap<>());

        if (userId != null) {
            // 加载画像
            Map<String, Object> profile = Map.of();
            try {
                profile = userProfileService.analyzeProfile(userId);
                builder.userProfile(profile);
            } catch (Exception e) {
                log.warn("加载用户画像失败: {}", e.getMessage());
                builder.userProfile(Map.of());
            }

            // 加载短期记忆
            builder.recentTopics(loadRecentTopics(userId));

            // 加载长期记忆（学习统计）并合并到画像
            // FIX: 先放入profile数据，再用longTermMemory覆盖统计字段，避免丢失画像维度
            Map<String, Object> longTermMemory = loadLongTermMemory(userId);
            Map<String, Object> mergedProfile = new LinkedHashMap<>();
            mergedProfile.putAll(profile);          // 先放基础画像（keywords/interests/preferences）
            mergedProfile.putAll(longTermMemory);   // 再合并长期记忆统计（totalGoals/completionRate等）
            builder.userProfile(mergedProfile);
        } else {
            builder.userProfile(Map.of());
            builder.recentTopics(List.of());
        }

        return builder.build();
    }

    /**
     * 加载最新压缩摘要（从chat_history中role=memory_summary的记录）
     * 只加载当前用户的数据
     */
    public String loadMemorySummary(Long userId) {
        if (userId == null) return null;
        try {
            ChatHistory summary = chatHistoryMapper.selectLatestMemorySummary(userId);
            return summary != null ? summary.getContent() : null;
        } catch (Exception e) {
            log.warn("加载记忆摘要失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新短期记忆（对话完成后调用）
     * 当普通消息超过阈值时触发LLM压缩
     */
    public void updateShortTermMemory(Long userId, String query, String response) {
        if (userId == null) return;

        try {
            long count = chatHistoryMapper.countByUserId(userId);

            if (count > compressThreshold) {
                log.info("用户{}的对话历史({})超过阈值({})，触发摘要压缩", userId, count, compressThreshold);
                compressHistory(userId);
            }

        } catch (Exception e) {
            log.warn("更新短期记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 压缩旧对话：取最早的N条 → LLM生成摘要 → 存为memory_summary → 删除原始消息
     * 全程基于userId隔离
     */
    private void compressHistory(Long userId) {
        try {
            List<ChatHistory> oldest = chatHistoryMapper.selectOldestMessages(userId, 0L, compressBatchSize);

            if (oldest == null || oldest.isEmpty()) {
                log.info("用户{}没有可压缩的旧消息", userId);
                return;
            }

            // 格式化为可读文本（去掉STRUCTURED_DATA标记）
            StringBuilder dialogText = new StringBuilder();
            for (ChatHistory msg : oldest) {
                String role = "user".equals(msg.getRole()) ? "用户" : "助手";
                String content = cleanContent(msg.getContent());
                if (content != null && !content.isBlank()) {
                    dialogText.append(role).append(": ").append(content).append("\n");
                }
            }

            if (dialogText.isEmpty()) {
                log.info("用户{}的旧消息格式化后为空，跳过压缩", userId);
                return;
            }

            // 调用LLM生成摘要
            String systemPrompt = "你是一个对话摘要助手。请将以下对话历史压缩为一段简洁的摘要（200字以内）。" +
                    "只保留：学习主题、关键知识点、用户偏好、薄弱环节、学习进度。忽略闲聊和无关内容。";

            String summary = aiService.chatWithSystemPrompt(systemPrompt, dialogText.toString());

            if (summary == null || summary.isBlank()) {
                log.warn("LLM摘要生成返回空，跳过压缩 userId={}", userId);
                return;
            }

            // 存储摘要（带userId）
            ChatHistory summaryEntry = new ChatHistory();
            summaryEntry.setUserId(userId);
            summaryEntry.setAgentId(0L);
            summaryEntry.setAgentName("记忆压缩");
            summaryEntry.setRole("memory_summary");
            summaryEntry.setContent(summary);
            summaryEntry.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(summaryEntry);

            // 删除已压缩的原始消息（双重校验：userId + ids）
            List<Long> idsToDelete = oldest.stream().map(ChatHistory::getId).collect(Collectors.toList());
            int deleted = chatHistoryMapper.deleteByIds(userId, idsToDelete);

            log.info("对话压缩完成: userId={}, 压缩{}条→1条摘要, 删除{}条",
                    userId, oldest.size(), deleted);

        } catch (Exception e) {
            log.error("对话压缩失败 userId={}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 清洗消息内容：去掉STRUCTURED_DATA标记的JSON部分，截断过长内容
     */
    private String cleanContent(String content) {
        if (content == null) return "";
        int markerIdx = content.indexOf("<!--STRUCTURED_DATA-->");
        if (markerIdx >= 0) {
            content = content.substring(0, markerIdx).trim();
        }
        if (content.length() > 300) {
            content = content.substring(0, 300) + "...";
        }
        return content;
    }

    /**
     * 更新长期记忆（学习进度变化时调用）
     * 统计用户的学习行为数据并写入画像
     */
    public void updateLongTermMemory(Long userId, String topic, String category, int progress) {
        if (userId == null) return;

        try {
            UserProfile profile = userProfileMapper.selectByUserId(userId);
            if (profile != null) {
                // 更新画像关键词和兴趣
                if (topic != null && !topic.isEmpty()) {
                    userProfileService.updateKeywords(userId, topic);
                }
                if (category != null && !category.isEmpty()) {
                    userProfileService.updateUserInterests(userId, category);
                }

                // 更新学习统计
                Map<String, Object> longTermMemory = loadLongTermMemory(userId);
                Map<String, Object> updates = new LinkedHashMap<>();
                updates.put("preferences",
                        "完成率: " + longTermMemory.getOrDefault("completionRate", 0) + "%" +
                        " | 活跃目标: " + longTermMemory.getOrDefault("inProgressGoals", 0));

                userProfileService.updateProfile(userId, updates);
                log.debug("长期记忆已更新: userId={}, topic={}, progress={}%", userId, topic, progress);
            }
        } catch (Exception e) {
            log.warn("更新长期记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 简单关键词提取（从文本中提取主要学习主题）
     */
    private String extractMainTopic(String text) {
        if (text == null || text.length() < 5) return null;

        // 简单的主题匹配（可升级为LLM提取）
        String[] keywords = {
            "Python", "Java", "英语", "四级", "六级", "雅思", "数学",
            "编程", "算法", "数据结构", "机器学习", "前端", "后端",
            "考研", "考公", "面试", "SQL", "数据库"
        };

        for (String kw : keywords) {
            if (text.contains(kw)) {
                return kw;
            }
        }
        return null;
    }
}
