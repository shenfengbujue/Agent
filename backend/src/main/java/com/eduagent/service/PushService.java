package com.eduagent.service;

import com.eduagent.entity.StudyGoal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 学习进度触发推送服务
 *
 * 当用户完成学习目标后，自动匹配相关资源并生成推送通知。
 *
 * 推送策略:
 * - 同科目进阶: 完成"Python基础" → 推"Python进阶"资源
 * - 关联科目: 完成"英语四级" → 推"六级"、"考研英语"
 * - 薄弱点补强: 根据画像中的errorPatterns推针对性练习
 * - 频率限制: 每用户每天最多5条推送，同资源7天内不重复推
 */
@Slf4j
@Service
public class PushService {

    private final RecommendationService recommendationService;
    private final ProfileDimensionService profileDimensionService;

    /** 每日推送上限 */
    private static final int DAILY_PUSH_LIMIT = 5;
    /** 同资源重复推送间隔（天） */
    private static final int DEDUP_DAYS = 7;

    // 内存缓存作为一级加速，数据库持久化
    private final Map<Long, List<PushRecord>> pushHistory = new java.util.concurrent.ConcurrentHashMap<>();
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public PushService(RecommendationService recommendationService,
                       ProfileDimensionService profileDimensionService,
                       org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.recommendationService = recommendationService;
        this.profileDimensionService = profileDimensionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 学习目标完成后触发推送
     *
     * @param userId       用户ID
     * @param completedGoal 已完成的学习目标
     * @return 推送资源列表
     */
    public List<Map<String, Object>> generatePushOnGoalComplete(Long userId, StudyGoal completedGoal) {
        if (userId == null || completedGoal == null) return List.of();

        try {
            // 检查频率限制
            if (!canPushToday(userId)) {
                log.info("用户{}今日推送已达上限，跳过", userId);
                return List.of();
            }

            // 匹配相关资源
            String goalTitle = completedGoal.getTitle();
            String goalCategory = completedGoal.getCategory();

            List<Map<String, Object>> recommendations = new ArrayList<>();

            // 策略1: 同类别进阶资源
            if (goalCategory != null) {
                List<Map<String, Object>> advanced = recommendationService
                        .getHomeRecommendations(userId, 3)
                        .stream()
                        .filter(r -> !r.get("title").toString().contains(goalTitle))
                        .collect(java.util.stream.Collectors.toList());
                for (Map<String, Object> r : advanced) {
                    r.put("pushType", "GOAL_COMPLETED");
                    r.put("pushReason", "完成「" + goalTitle + "」后推荐进阶资源");
                    recommendations.add(r);
                }
            }

            // 策略2: 根据画像薄弱点推荐
            String errorPatterns = profileDimensionService.getDimensionValue(
                    userId, "errorPatterns", null);
            if (errorPatterns != null && !errorPatterns.isEmpty()) {
                Map<String, Object> weakPointResource = new LinkedHashMap<>();
                weakPointResource.put("title", "薄弱环节专项练习 - " + errorPatterns);
                weakPointResource.put("category", "练习");
                weakPointResource.put("summary", "针对您在「" + errorPatterns + "」方面的薄弱点，推荐专项练习题");
                weakPointResource.put("pushType", "WEAK_POINT");
                weakPointResource.put("pushReason", "根据您的薄弱环节「" + errorPatterns + "」推荐");
                recommendations.add(weakPointResource);
            }

            // 去重（排除7天内已推送的资源）
            List<Map<String, Object>> filtered = filterDuplicates(userId, recommendations);

            // 记录推送
            for (Map<String, Object> r : filtered) {
                recordPush(userId, r);
            }

            log.info("目标完成推送: userId={}, goal={}, results={}",
                    userId, goalTitle, filtered.size());
            return filtered.size() > 3 ? filtered.subList(0, 3) : filtered;

        } catch (Exception e) {
            log.warn("生成推送失败: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 阶段完成时触发推送
     */
    public List<Map<String, Object>> generatePushOnStageComplete(Long userId, StudyGoal goal, int stageIndex) {
        if (userId == null || goal == null) return List.of();
        if (!canPushToday(userId)) return List.of();

        try {
            List<Map<String, Object>> recommendations = new ArrayList<>();
            String stageLabel = "第" + (stageIndex + 1) + "阶段";
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", stageLabel + "完成 —— 推荐下一阶段学习资源");
            item.put("category", goal.getCategory() != null ? goal.getCategory() : "学习");
            item.put("summary", "继续加油！为您推荐下一阶段的进阶学习内容");
            item.put("pushType", "STAGE_COMPLETE");
            item.put("pushReason", stageLabel + "学习完成");
            recommendations.add(item);

            List<Map<String, Object>> filtered = filterDuplicates(userId, recommendations);
            for (Map<String, Object> r : filtered) {
                recordPush(userId, r);
            }
            return filtered;
        } catch (Exception e) {
            log.warn("阶段推送失败: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取用户未读推送列表
     */
    public List<Map<String, Object>> getPendingPushes(Long userId) {
        if (userId == null) return List.of();
        List<PushRecord> records = pushHistory.getOrDefault(userId, List.of());
        return records.stream()
                .filter(r -> !r.isRead)
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                .limit(10)
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.id);
                    item.put("title", r.title);
                    item.put("reason", r.reason);
                    item.put("pushType", r.pushType);
                    item.put("isRead", r.isRead);
                    item.put("createdAt", r.createdAt);
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取未读推送数量
     */
    public int getUnreadCount(Long userId) {
        if (userId == null) return 0;
        List<PushRecord> records = pushHistory.getOrDefault(userId, List.of());
        return (int) records.stream().filter(r -> !r.isRead).count();
    }

    /**
     * 标记推送为已读
     */
    public void markAsRead(Long userId, String pushId) {
        List<PushRecord> records = pushHistory.get(userId);
        if (records != null) {
            for (PushRecord r : records) {
                if (r.id.equals(pushId)) {
                    r.isRead = true;
                    break;
                }
            }
        }
    }

    // ==================== 私有方法 ====================

    private boolean canPushToday(Long userId) {
        List<PushRecord> records = pushHistory.getOrDefault(userId, List.of());
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayCount = records.stream()
                .filter(r -> !r.createdAt.isBefore(today))
                .count();
        return todayCount < DAILY_PUSH_LIMIT;
    }

    private List<Map<String, Object>> filterDuplicates(Long userId, List<Map<String, Object>> items) {
        List<PushRecord> records = pushHistory.getOrDefault(userId, List.of());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(DEDUP_DAYS);

        Set<String> recentTitles = new HashSet<>();
        for (PushRecord r : records) {
            if (!r.createdAt.isBefore(cutoff)) {
                recentTitles.add(r.title);
            }
        }

        return items.stream()
                .filter(item -> !recentTitles.contains(item.get("title")))
                .collect(java.util.stream.Collectors.toList());
    }

    private void recordPush(Long userId, Map<String, Object> item) {
        pushHistory.computeIfAbsent(userId, k -> new ArrayList<>());
        String pid = UUID.randomUUID().toString().substring(0, 8);
        String title = (String) item.getOrDefault("title", "");
        String reason = (String) item.getOrDefault("pushReason", "");
        String pushType = (String) item.getOrDefault("pushType", "GENERAL");
        PushRecord record = new PushRecord(pid, title, reason, pushType, false, LocalDateTime.now());
        pushHistory.get(userId).add(record);
        // 持久化到数据库
        try {
            jdbcTemplate.update(
                "INSERT INTO push_records (id, user_id, title, reason, push_type, is_read, created_at) VALUES (?,?,?,?,?,?,?)",
                pid, userId, title, reason, pushType, 0, record.createdAt);
        } catch (Exception e) { log.warn("推送记录持久化失败: {}", e.getMessage()); }
    }

    // ==================== 内部类 ====================

    private static class PushRecord {
        final String id;
        final String title;
        final String reason;
        final String pushType;
        boolean isRead;
        final LocalDateTime createdAt;

        PushRecord(String id, String title, String reason, String pushType,
                   boolean isRead, LocalDateTime createdAt) {
            this.id = id;
            this.title = title;
            this.reason = reason;
            this.pushType = pushType;
            this.isRead = isRead;
            this.createdAt = createdAt;
        }
    }
}
