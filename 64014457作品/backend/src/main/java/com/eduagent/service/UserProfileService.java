package com.eduagent.service;

import com.eduagent.agent.JsonParserUtil;
import com.eduagent.entity.ProfileDimension;
import com.eduagent.entity.User;
import com.eduagent.entity.UserProfile;
import com.eduagent.mapper.UserProfileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UserProfileService {

    private final UserProfileMapper profileMapper;
    private final AIService aiService;
    private final ProfileDimensionService dimensionService;

    /** 画像分析专用System Prompt */
    private static final String PROFILE_ANALYZER_PROMPT = """
            你是学习画像分析专家。你需要从学生的自然语言对话中提取画像特征。

            需要从对话中提取以下维度的信息（如果对话中未体现该维度，填"未提及"）:
            1. knowledgeLevel: 知识基础（如"零基础"、"初级"、"中级"、"高级"）
            2. cognitiveStyle: 认知风格（如"视觉型"、"听觉型"、"动手实践型"、"读写型"）
            3. errorPatterns: 易错模式/薄弱点（如"语法薄弱"、"概念混淆"、"计算粗心"）
            4. motivation: 学习动机（如"考试备考"、"职场提升"、"兴趣爱好"、"学术研究"）
            5. timePreference: 学习时间偏好（如"早晨"、"晚上"、"碎片时间"、"周末集中"）
            6. socialTendency: 社交学习倾向（如"喜欢小组学习"、"偏好独立学习"、"喜欢讨论"）
            7. learningPace: 学习节奏偏好（如"快速推进"、"稳扎稳打"、"反复练习"）
            8. completionRate: 历史完成率评估（如"高"、"中"、"低"、"未知"）
            9. subjects: 学习科目/主题列表（数组）
            10. interests: 兴趣领域列表（数组）

            输出JSON格式（必须严格遵守）:
            {
              "dimensions": {
                "knowledgeLevel": "值",
                "cognitiveStyle": "值",
                "errorPatterns": "值",
                "motivation": "值",
                "timePreference": "值",
                "socialTendency": "值",
                "learningPace": "值",
                "completionRate": "值"
              },
              "subjects": ["科目1", "科目2"],
              "interests": ["兴趣1", "兴趣2"],
              "confidence": 0.0-1.0,
              "summary": "一句话画像总结"
            }
            """;

    private static final Set<String> STOP_WORDS;
    private static final List<String> LEARNING_KEYWORDS;

    static {
        Set<String> words = new HashSet<>();
        words.add("的"); words.add("了"); words.add("和"); words.add("是"); words.add("就");
        words.add("都"); words.add("而"); words.add("及"); words.add("与"); words.add("着"); words.add("或");
        words.add("一个"); words.add("没有"); words.add("我们"); words.add("你们"); words.add("他们");
        words.add("什么"); words.add("怎么"); words.add("为什么"); words.add("如何"); words.add("哪里");
        words.add("何时"); words.add("谁"); words.add("哪个"); words.add("这个"); words.add("那个");
        words.add("这些"); words.add("那些"); words.add("一些");
        words.add("推荐"); words.add("书籍"); words.add("课程"); words.add("资料");
        words.add("教程"); words.add("方法"); words.add("技巧"); words.add("策略");
        words.add("安排"); words.add("建议"); words.add("指导");
        words.add("想考"); words.add("想");
        words.add("learn"); words.add("want"); words.add("level"); words.add("hour");
        words.add("per"); words.add("day"); words.add("beginner"); words.add("study");
        words.add("need"); words.add("help"); words.add("like"); words.add("know");
        words.add("time"); words.add("week"); words.add("month"); words.add("year");
        words.add("我要考"); words.add("我想考"); words.add("我要学"); words.add("我想学");
        words.add("考英"); words.add("语四级"); words.add("四级学"); words.add("级学习");
        words.add("我要"); words.add("我想"); words.add("每天"); words.add("小时");
        // 非学习内容：问候、闲聊、不完整句子
        words.add("你好"); words.add("谢谢"); words.add("再见"); words.add("好的");
        words.add("嗯"); words.add("哦"); words.add("啊"); words.add("哈");
        words.add("是的"); words.add("不是"); words.add("可以"); words.add("行");
        words.add("帮我"); words.add("请问"); words.add("告诉"); words.add("给我");
        words.add("能不能"); words.add("可不可以"); words.add("怎么样");
        words.add("对于"); words.add("我不"); words.add("我不清楚"); words.add("不清楚");
        words.add("为了"); words.add("我是"); words.add("我在"); words.add("我有");
        words.add("测试"); words.add("test"); words.add("测试同学"); words.add("今天");
        STOP_WORDS = Collections.unmodifiableSet(words);

        LEARNING_KEYWORDS = Arrays.asList(
            "英语四级", "英语六级", "考研英语", "雅思", "IELTS", "托福", "TOEFL",
            "机器学习", "深度学习", "人工智能", "数据结构", "数据结构与算法",
            "算法", "Python", "Java", "编程", "代码", "语法", "函数", "类",
            "考研", "考公", "考证", "备考", "复习", "练习", "训练", "计划",
            "公文写作", "报告", "通知", "函", "请示",
            "学习", "学习计划", "入门教程", "入门",
            "前端开发", "后端开发", "数据分析", "自然语言处理",
            "计算机视觉", "操作系统", "计算机网络", "数据库",
            "高等数学", "线性代数", "概率论", "离散数学",
            "高考", "中考", "专升本", "GRE", "GMAT",
            "计算机二级", "计算机一级", "计算机三级",
            "MS Office", "Office", "WPS", "PPT", "Excel", "Word",
            "高数", "微积分", "数理统计", "大学物理", "大学化学",
            "日语", "韩语", "法语", "德语", "西班牙语",
            "CPA", "CFA", "ACCA", "司法考试", "法考",
            "教师资格证", "普通话", "驾照"
        );
    }

    public UserProfileService(UserProfileMapper profileMapper, AIService aiService,
                               ProfileDimensionService dimensionService) {
        this.profileMapper = profileMapper;
        this.aiService = aiService;
        this.dimensionService = dimensionService;
    }

    public UserProfile getProfile(Long userId) {
        try {
            UserProfile profile = profileMapper.selectByUserId(userId);
            if (profile == null) {
                profile = createProfile(userId);
            }
            return profile;
        } catch (Exception e) {
            log.error("Error getting profile for userId {}: {}", userId, e.getMessage());
            return createProfile(userId);
        }
    }

    public UserProfile createProfile(Long userId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setKeywords("");
        profile.setInterests("");
        profile.setPreferences("");
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        try {
            profileMapper.insert(profile);
        } catch (Exception e) {
            log.error("Error creating profile for userId {}: {}", userId, e.getMessage());
        }
        return profile;
    }

    public UserProfile updateProfile(Long userId, Map<String, Object> updates) {
        UserProfile profile = getProfile(userId);
        
        if (updates.containsKey("keywords")) {
            profile.setKeywords((String) updates.get("keywords"));
        }
        if (updates.containsKey("interests")) {
            profile.setInterests((String) updates.get("interests"));
        }
        if (updates.containsKey("preferences")) {
            profile.setPreferences((String) updates.get("preferences"));
        }
        
        profile.setUpdatedAt(LocalDateTime.now());
        try {
            profileMapper.updateById(profile);
        } catch (Exception e) {
            log.error("Error updating profile for userId {}: {}", userId, e.getMessage());
        }
        return profile;
    }

    public UserProfile updateKeywords(Long userId, String text) {
        List<String> extractedKeywords = extractKeywords(text);
        return updateUserKeywords(userId, extractedKeywords);
    }

    public List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) return List.of();

        Set<String> keywords = new LinkedHashSet<>();

        // 只匹配预定义的学习关键词列表，不再使用正则碎片抓取
        for (String keyword : LEARNING_KEYWORDS) {
            if (text.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        // 如果没有匹配到任何预定义关键词，且文本看起来像个主题/科目名，直接用它
        if (keywords.isEmpty() && text.length() >= 2 && text.length() <= 20) {
            String trimmed = text.trim();
            // 严格过滤：必须是学习主题而非闲聊/问句
            boolean looksLikeTopic = !STOP_WORDS.contains(trimmed)
                && !trimmed.matches(".*[a-zA-Z]{1,2}$")         // 不以1-2个字母结尾
                && !trimmed.contains("?") && !trimmed.contains("？")
                && !trimmed.contains("怎么") && !trimmed.contains("什么")
                && !trimmed.contains("如何") && !trimmed.contains("为什么")
                && !trimmed.contains("能不能") && !trimmed.contains("帮我")
                && !trimmed.contains("给我") && !trimmed.contains("请问")
                && !trimmed.contains("对于") && !trimmed.contains("不清楚")
                && !trimmed.contains("学校") && !trimmed.contains("为了")
                && !trimmed.matches(".*[，,。！!].*")           // 不含标点符号(不是完整句子)
                && trimmed.length() >= 3;                       // 至少3个字符
            if (looksLikeTopic) {
                keywords.add(trimmed);
            }
        }

        List<String> result = new ArrayList<>(keywords);
        result.sort((a, b) -> b.length() - a.length());

        // 清除被更长关键词包含的碎片
        List<String> filtered = new ArrayList<>();
        for (String kw : result) {
            boolean isSubstring = false;
            for (String existing : filtered) {
                if (!existing.equals(kw) && existing.contains(kw)) {
                    isSubstring = true;
                    break;
                }
            }
            if (!isSubstring) {
                filtered.add(kw);
            }
        }

        return filtered.size() > 10 ? filtered.subList(0, 10) : filtered;
    }

    public UserProfile updateUserKeywords(Long userId, List<String> newKeywords) {
        UserProfile profile = getProfile(userId);
        
        Set<String> existingKeywords = new LinkedHashSet<>();
        if (profile.getKeywords() != null && !profile.getKeywords().isEmpty()) {
            for (String kw : profile.getKeywords().split(",")) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty()) {
                    existingKeywords.add(trimmed);
                }
            }
        }
        
        for (String kw : newKeywords) {
            if (kw != null && !kw.trim().isEmpty()) {
                existingKeywords.add(kw.trim());
            }
        }
        
        List<String> sortedKeywords = new ArrayList<>(existingKeywords);
        sortedKeywords.sort((a, b) -> b.length() - a.length());
        
        if (sortedKeywords.size() > 20) {
            sortedKeywords = sortedKeywords.subList(0, 20);
        }
        
        profile.setKeywords(String.join(",", sortedKeywords));
        profile.setUpdatedAt(LocalDateTime.now());
        try {
            profileMapper.updateById(profile);
        } catch (Exception e) {
            log.error("Error updating keywords for userId {}: {}", userId, e.getMessage());
        }
        
        return profile;
    }

    public UserProfile updateUserInterests(Long userId, String interest) {
        UserProfile profile = getProfile(userId);
        
        Set<String> interests = new LinkedHashSet<>();
        if (profile.getInterests() != null && !profile.getInterests().isEmpty()) {
            for (String i : profile.getInterests().split(",")) {
                String trimmed = i.trim();
                if (!trimmed.isEmpty()) {
                    interests.add(trimmed);
                }
            }
        }
        
        if (interest != null && !interest.trim().isEmpty()) {
            interests.add(interest.trim());
        }
        
        if (interests.size() > 15) {
            List<String> temp = new ArrayList<>(interests);
            interests = new LinkedHashSet<>(temp.subList(0, 15));
        }
        
        profile.setInterests(String.join(",", interests));
        profile.setUpdatedAt(LocalDateTime.now());
        try {
            profileMapper.updateById(profile);
        } catch (Exception e) {
            log.error("Error updating interests for userId {}: {}", userId, e.getMessage());
        }
        
        return profile;
    }

    public Map<String, Object> analyzeProfile(Long userId) {
        UserProfile profile = getProfile(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);

        // 基础维度
        List<String> keywordsList = new ArrayList<>();
        if (profile.getKeywords() != null && !profile.getKeywords().isEmpty()) {
            for (String kw : profile.getKeywords().split(",")) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty()) {
                    keywordsList.add(trimmed);
                }
            }
        }
        result.put("keywords", keywordsList);

        List<String> interestsList = new ArrayList<>();
        if (profile.getInterests() != null && !profile.getInterests().isEmpty()) {
            for (String i : profile.getInterests().split(",")) {
                String trimmed = i.trim();
                if (!trimmed.isEmpty()) {
                    interestsList.add(trimmed);
                }
            }
        }
        result.put("interests", interestsList);
        result.put("preferences", profile.getPreferences());
        result.put("updatedAt", profile.getUpdatedAt());

        // 从ProfileDimension读取真实维度数据（替代preferences字符串解析）
        Map<String, ProfileDimension> dimensions = dimensionService.getUserDimensions(userId);
        result.put("knowledgeLevel",
                getDimValue(dimensions, ProfileDimension.DIM_KNOWLEDGE_LEVEL, "未评估"));
        result.put("cognitiveStyle",
                getDimValue(dimensions, ProfileDimension.DIM_COGNITIVE_STYLE, "未评估"));
        result.put("errorPatterns",
                getDimValue(dimensions, ProfileDimension.DIM_ERROR_PATTERNS, "未评估"));
        result.put("motivation",
                getDimValue(dimensions, ProfileDimension.DIM_MOTIVATION, "未评估"));
        result.put("timePreference",
                getDimValue(dimensions, ProfileDimension.DIM_TIME_PREFERENCE, "未评估"));
        result.put("socialTendency",
                getDimValue(dimensions, ProfileDimension.DIM_SOCIAL_TENDENCY, "未评估"));
        result.put("learningPace",
                getDimValue(dimensions, ProfileDimension.DIM_LEARNING_PACE, "未评估"));
        result.put("completionRate",
                getDimValue(dimensions, ProfileDimension.DIM_COMPLETION_RATE, "0%"));
        result.put("dimensionCount", 8);
        result.put("highConfidenceCount",
                dimensions.values().stream().filter(d -> d.getConfidence() != null && d.getConfidence() >= 0.8).count());

        return result;
    }

    private String getDimValue(Map<String, ProfileDimension> dimensions, String key, String defaultValue) {
        ProfileDimension dim = dimensions.get(key);
        return dim != null ? dim.getDimensionValue() : defaultValue;
    }

    /**
     * 对话式画像分析：通过LLM从对话文本中提取结构化画像特征
     * LLM失败时降级为关键词匹配兜底
     */
    public Map<String, Object> analyzeProfileFromDialogue(Long userId, String dialogueText) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        try {
            log.info("开始LLM画像分析: userId={}, dialogueLength={}", userId, dialogueText.length());

            // 调用LLM进行深度分析
            String llmOutput = aiService.chatWithSystemPrompt(PROFILE_ANALYZER_PROMPT, dialogueText);

            if (llmOutput != null && !llmOutput.isBlank()) {
                JsonNode json = JsonParserUtil.parseJson(llmOutput);

                // 解析维度
                if (json.has("dimensions")) {
                    JsonNode dims = json.get("dimensions");
                    Map<String, String> dimensionMap = new LinkedHashMap<>();
                    dims.fieldNames().forEachRemaining(key -> {
                        String value = dims.get(key).asText();
                        if (!"未提及".equals(value)) {
                            dimensionMap.put(key, value);
                        }
                    });
                    // 持久化到ProfileDimension
                    dimensionService.saveDimensions(userId, dimensionMap,
                            ProfileDimension.SOURCE_DIALOGUE_INFERRED);
                    analysis.put("dimensions", dimensionMap);
                }

                // 解析科目
                if (json.has("subjects") && json.get("subjects").isArray()) {
                    List<String> subjects = new ArrayList<>();
                    for (JsonNode s : json.get("subjects")) {
                        subjects.add(s.asText());
                    }
                    analysis.put("subjects", subjects);
                    updateUserKeywords(userId, subjects);
                }

                // 解析兴趣
                if (json.has("interests") && json.get("interests").isArray()) {
                    List<String> interests = new ArrayList<>();
                    for (JsonNode i : json.get("interests")) {
                        interests.add(i.asText());
                    }
                    analysis.put("interests", interests);
                }

                double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 0.7;
                analysis.put("confidence", confidence);

                if (json.has("summary")) {
                    analysis.put("summary", json.get("summary").asText());
                }

                analysis.put("source", ProfileDimension.SOURCE_DIALOGUE_INFERRED);
                log.info("LLM画像分析完成: userId={}, dimensions={}, confidence={}",
                        userId, analysis.get("dimensions"), confidence);
                return analysis;
            }
        } catch (Exception e) {
            log.error("LLM画像分析失败，降级为关键词匹配: userId={}, error={}", userId, e.getMessage());
        }

        // ===== 降级方案：硬编码关键词匹配 =====
        List<String> extractedKeywords = extractKeywords(dialogueText);
        updateUserKeywords(userId, extractedKeywords);
        analysis.put("extractedKeywords", extractedKeywords);
        analysis.put("dimensions", Map.of());
        analysis.put("source", "FALLBACK_KEYWORD");
        analysis.put("confidence", 0.3);
        log.info("画像分析降级完成(关键词匹配): userId={}, keywords={}", userId, extractedKeywords);
        return analysis;
    }

    /**
     * 将User实体中的画像字段迁移到ProfileDimension表
     * User -> ProfileDimension 映射:
     *   knowledgeLevel -> DIM_KNOWLEDGE_LEVEL
     *   learningStyle  -> DIM_COGNITIVE_STYLE
     *   weakPoints     -> DIM_ERROR_PATTERNS
     *   motivation     -> DIM_MOTIVATION
     *   timeAvailability -> DIM_TIME_PREFERENCE
     *   socialWillingness -> DIM_SOCIAL_TENDENCY
     *   frustrationHandling -> DIM_LEARNING_PACE
     *   examTime       -> 附加时间维度
     */
    public void syncUserToProfileDimensions(User user) {
        if (user == null || user.getId() == null) return;

        Long userId = user.getId();
        Map<String, String> dims = new LinkedHashMap<>();

        putIfNotBlank(dims, ProfileDimension.DIM_KNOWLEDGE_LEVEL, user.getKnowledgeLevel());
        putIfNotBlank(dims, ProfileDimension.DIM_KNOWLEDGE_LEVEL,
                user.getSkillLevel()); // skillLevel也映射到知识基础
        putIfNotBlank(dims, ProfileDimension.DIM_COGNITIVE_STYLE, user.getLearningStyle());
        putIfNotBlank(dims, ProfileDimension.DIM_ERROR_PATTERNS, user.getWeakPoints());
        putIfNotBlank(dims, ProfileDimension.DIM_MOTIVATION, user.getMotivation());
        putIfNotBlank(dims, ProfileDimension.DIM_TIME_PREFERENCE, user.getTimeAvailability());
        putIfNotBlank(dims, ProfileDimension.DIM_SOCIAL_TENDENCY, user.getSocialWillingness());
        putIfNotBlank(dims, ProfileDimension.DIM_LEARNING_PACE, user.getFrustrationHandling());

        if (!dims.isEmpty()) {
            dimensionService.saveDimensions(userId, dims, ProfileDimension.SOURCE_MIGRATED);
            log.info("User画像字段已迁移到ProfileDimension: userId={}, count={}", userId, dims.size());
        }
    }

    private void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank() && !"未评估".equals(value)) {
            map.put(key, value);
        }
    }
}