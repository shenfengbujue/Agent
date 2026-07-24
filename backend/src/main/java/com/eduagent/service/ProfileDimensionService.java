package com.eduagent.service;

import com.eduagent.entity.ProfileDimension;
import com.eduagent.mapper.ProfileDimensionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 画像维度服务 — 结构化读写画像维度
 *
 * 替代 UserProfile.preferences 字符串解析模式，
 * 提供维度级别的增删改查和置信度管理。
 */
@Slf4j
@Service
public class ProfileDimensionService {

    private final ProfileDimensionMapper dimensionMapper;

    public ProfileDimensionService(ProfileDimensionMapper dimensionMapper) {
        this.dimensionMapper = dimensionMapper;
    }

    /**
     * 获取用户所有画像维度（返回 Map<dimensionKey, ProfileDimension>）
     */
    public Map<String, ProfileDimension> getUserDimensions(Long userId) {
        List<ProfileDimension> dimensions = dimensionMapper.selectByUserId(userId);
        return dimensions.stream()
                .collect(Collectors.toMap(
                        ProfileDimension::getDimensionKey,
                        d -> d,
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ));
    }

    /**
     * 获取用户单个维度值
     */
    public String getDimensionValue(Long userId, String dimensionKey) {
        ProfileDimension dim = dimensionMapper.selectByUserIdAndKey(userId, dimensionKey);
        return dim != null ? dim.getDimensionValue() : null;
    }

    /**
     * 获取用户单个维度值（带默认值）
     */
    public String getDimensionValue(Long userId, String dimensionKey, String defaultValue) {
        String value = getDimensionValue(userId, dimensionKey);
        return value != null ? value : defaultValue;
    }

    /**
     * 保存或更新维度
     */
    public void saveDimension(Long userId, String key, String value, String source, Double confidence) {
        ProfileDimension dim = ProfileDimension.of(userId, key, value, source, confidence);
        dimensionMapper.upsert(dim);
        log.debug("画像维度已更新: userId={}, key={}, value={}, source={}", userId, key, value, source);
    }

    /**
     * 批量保存维度
     */
    public void saveDimensions(Long userId, Map<String, String> dimensions, String source) {
        for (Map.Entry<String, String> entry : dimensions.entrySet()) {
            saveDimension(userId, entry.getKey(), entry.getValue(), source, 0.8);
        }
        log.info("批量保存画像维度: userId={}, count={}, source={}", userId, dimensions.size(), source);
    }

    /**
     * 批量保存维度（带置信度）
     */
    public void saveDimensionsWithConfidence(Long userId, Map<String, ProfileDimension> dimensions) {
        for (ProfileDimension dim : dimensions.values()) {
            dim.setUserId(userId);
            dim.setUpdatedAt(LocalDateTime.now());
            if (dim.getCreatedAt() == null) {
                dim.setCreatedAt(LocalDateTime.now());
            }
            dimensionMapper.upsert(dim);
        }
    }

    /**
     * 将画像维度转换为Map（兼容旧接口）
     */
    public Map<String, Object> toProfileMap(Long userId) {
        Map<String, ProfileDimension> dimensions = getUserDimensions(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);

        // 维度数据
        for (ProfileDimension dim : dimensions.values()) {
            result.put(dim.getDimensionKey(), dim.getDimensionValue());
        }

        // 统计信息
        result.put("dimensionCount", dimensions.size());
        long highConfidence = dimensions.values().stream()
                .filter(d -> d.getConfidence() != null && d.getConfidence() >= 0.8).count();
        result.put("highConfidenceCount", highConfidence);
        result.put("dimensions", dimensions);

        return result;
    }

    /**
     * 生成画像文本摘要（注入LLM Prompt用）
     */
    public String buildProfileSummary(Long userId) {
        Map<String, ProfileDimension> dimensions = getUserDimensions(userId);
        if (dimensions.isEmpty()) {
            return "暂无用户画像数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("用户画像:\n");
        for (ProfileDimension dim : dimensions.values()) {
            String label = dim.getDimensionLabel() != null ? dim.getDimensionLabel() : dim.getDimensionKey();
            String confidenceStr = dim.getConfidence() != null && dim.getConfidence() < 1.0
                    ? String.format(" (置信度: %.0f%%)", dim.getConfidence() * 100) : "";
            sb.append("- ").append(label).append(": ").append(dim.getDimensionValue()).append(confidenceStr).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取关键词列表（从维度中提取）
     */
    public List<String> getKeywords(Long userId) {
        Map<String, ProfileDimension> dimensions = getUserDimensions(userId);
        List<String> keywords = new ArrayList<>();

        // 从相关知识维度提取关键词
        ProfileDimension knowledgeLevel = dimensions.get(ProfileDimension.DIM_KNOWLEDGE_LEVEL);
        if (knowledgeLevel != null) {
            keywords.add(knowledgeLevel.getDimensionValue());
        }

        ProfileDimension motivation = dimensions.get(ProfileDimension.DIM_MOTIVATION);
        if (motivation != null) {
            keywords.add(motivation.getDimensionValue());
        }

        return keywords;
    }

    /**
     * 获取兴趣列表（从维度中提取）
     */
    public List<String> getInterests(Long userId) {
        Map<String, ProfileDimension> dimensions = getUserDimensions(userId);
        List<String> interests = new ArrayList<>();

        for (ProfileDimension dim : dimensions.values()) {
            if (dim.getConfidence() != null && dim.getConfidence() >= 0.7) {
                interests.add(dim.getDimensionLabel() != null ? dim.getDimensionLabel() : dim.getDimensionKey());
            }
        }

        return interests;
    }
}
