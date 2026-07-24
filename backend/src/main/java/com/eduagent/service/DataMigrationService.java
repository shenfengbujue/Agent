package com.eduagent.service;

import com.eduagent.entity.ProfileDimension;
import com.eduagent.entity.UserProfile;
import com.eduagent.mapper.ProfileDimensionMapper;
import com.eduagent.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据迁移服务 — 将旧画像数据迁移到 profile_dimensions 表
 *
 * 从 UserProfile.preferences 和 User 表画像字段中解析维度数据，
 * 迁移到结构化的 profile_dimensions 表。
 *
 * 幂等性: 重复执行不会产生重复数据（使用UPSERT）。
 */
@Slf4j
@Service
public class DataMigrationService {

    private final UserProfileMapper profileMapper;
    private final ProfileDimensionMapper dimensionMapper;

    public DataMigrationService(UserProfileMapper profileMapper,
                                ProfileDimensionMapper dimensionMapper) {
        this.profileMapper = profileMapper;
        this.dimensionMapper = dimensionMapper;
    }

    /**
     * 执行全量画像数据迁移
     * 从 UserProfile 表迁移到 profile_dimensions 表
     */
    public Map<String, Object> migrateAllProfiles() {
        Map<String, Object> result = new LinkedHashMap<>();
        int totalUsers = 0;
        int migratedDimensions = 0;
        List<String> errors = new ArrayList<>();

        try {
            // 获取所有UserProfile记录
            List<UserProfile> allProfiles = profileMapper.selectList(null);
            if (allProfiles == null || allProfiles.isEmpty()) {
                result.put("status", "no_data");
                result.put("message", "没有需要迁移的用户画像数据");
                return result;
            }

            for (UserProfile profile : allProfiles) {
                try {
                    int count = migrateSingleProfile(profile);
                    migratedDimensions += count;
                    totalUsers++;
                } catch (Exception e) {
                    errors.add("用户" + profile.getUserId() + "迁移失败: " + e.getMessage());
                    log.warn("画像迁移失败: userId={}, error={}", profile.getUserId(), e.getMessage());
                }
            }

            result.put("status", "success");
            result.put("totalUsers", totalUsers);
            result.put("migratedDimensions", migratedDimensions);
            result.put("errors", errors);

            log.info("画像数据迁移完成: {}个用户, {}个维度, {}个错误",
                    totalUsers, migratedDimensions, errors.size());

        } catch (Exception e) {
            log.error("画像数据批量迁移失败", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * 迁移单个用户的画像数据
     */
    public int migrateSingleProfile(UserProfile profile) {
        int count = 0;
        Long userId = profile.getUserId();

        // 1. 迁移 keywords → 作为知识基础维度
        if (profile.getKeywords() != null && !profile.getKeywords().isEmpty()) {
            List<String> keywords = Arrays.asList(profile.getKeywords().split(","));
            if (!keywords.isEmpty()) {
                saveDimension(userId, ProfileDimension.DIM_KNOWLEDGE_LEVEL,
                        "keywords", profile.getKeywords().trim(), count);
                count++;
            }
        }

        // 2. 迁移 interests → 作为学习动机维度
        if (profile.getInterests() != null && !profile.getInterests().isEmpty()) {
            saveDimension(userId, ProfileDimension.DIM_MOTIVATION,
                    "学习动机", profile.getInterests().trim(), count);
            count++;
        }

        // 3. 解析 preferences 中的维度
        if (profile.getPreferences() != null && !profile.getPreferences().isEmpty()) {
            Map<String, String> parsed = parsePreferences(profile.getPreferences());
            for (Map.Entry<String, String> entry : parsed.entrySet()) {
                String key = normalizeDimensionKey(entry.getKey());
                String label = ProfileDimension.DIMENSION_LABELS.getOrDefault(key, entry.getKey());
                saveDimension(userId, key, label, entry.getValue().trim(), count);
                count++;
            }
        }

        log.debug("画像迁移: userId={}, dimensions={}", userId, count);
        return count;
    }

    private void saveDimension(Long userId, String key, String label, String value, int index) {
        if (value == null || value.isBlank()) return;
        // 截断过长值
        if (value.length() > 500) value = value.substring(0, 500);

        ProfileDimension dim = ProfileDimension.builder()
                .userId(userId)
                .dimensionKey(key)
                .dimensionValue(value)
                .dimensionLabel(label)
                .source(ProfileDimension.SOURCE_MIGRATED)
                .confidence(0.9) // 迁移数据的置信度较高（来自用户手动填写）
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        dimensionMapper.upsert(dim);
    }

    /**
     * 解析 preferences 字符串中的 key:value 对
     */
    private Map<String, String> parsePreferences(String preferences) {
        Map<String, String> result = new LinkedHashMap<>();
        if (preferences == null || preferences.isEmpty()) return result;

        String[] lines = preferences.split("[\\n|]");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            int colonIdx = line.indexOf(":");
            if (colonIdx > 0 && colonIdx < line.length() - 1) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    /**
     * 将旧的维度键名标准化为新的维度键
     */
    private String normalizeDimensionKey(String rawKey) {
        return switch (rawKey) {
            case "knowledgeLevel", "知识水平", "knowledge_level" -> ProfileDimension.DIM_KNOWLEDGE_LEVEL;
            case "cognitiveStyle", "认知风格", "cognitive_style" -> ProfileDimension.DIM_COGNITIVE_STYLE;
            case "errorPatterns", "易错点", "易错模式", "error_patterns" -> ProfileDimension.DIM_ERROR_PATTERNS;
            case "motivation", "学习动机" -> ProfileDimension.DIM_MOTIVATION;
            case "timePreference", "时间偏好", "time_preference" -> ProfileDimension.DIM_TIME_PREFERENCE;
            case "socialTendency", "社交倾向", "social_tendency" -> ProfileDimension.DIM_SOCIAL_TENDENCY;
            case "learningPace", "学习节奏", "learning_pace" -> ProfileDimension.DIM_LEARNING_PACE;
            case "completionRate", "完成率", "completion_rate" -> ProfileDimension.DIM_COMPLETION_RATE;
            default -> rawKey; // 保持原样
        };
    }

    /**
     * 检查迁移状态
     */
    public Map<String, Object> getMigrationStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            List<UserProfile> allProfiles = profileMapper.selectList(null);
            int totalUsers = allProfiles != null ? allProfiles.size() : 0;

            // 简单检查：第一条记录的迁移情况
            long migratedCount = 0;
            if (!allProfiles.isEmpty()) {
                UserProfile first = allProfiles.get(0);
                List<ProfileDimension> dims = dimensionMapper.selectByUserId(first.getUserId());
                migratedCount = dims != null ? dims.size() : 0;
            }

            status.put("totalUsers", totalUsers);
            status.put("sampleMigrated", migratedCount > 0);
            status.put("sampleDimensions", migratedCount);
        } catch (Exception e) {
            status.put("error", e.getMessage());
        }
        return status;
    }
}
