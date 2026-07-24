package com.eduagent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM JSON输出解析工具
 * 处理模型偶尔输出markdown包裹的JSON、尾部逗号、缺少引号等问题
 */
@Slf4j
public class JsonParserUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 匹配markdown代码块中的JSON: ```json ... ``` */
    private static final Pattern MARKDOWN_JSON_PATTERN =
            Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```", Pattern.CASE_INSENSITIVE);

    /** 匹配纯JSON对象 */
    private static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("\\{[\\s\\S]*\\}");

    /**
     * 从LLM输出中解析JSON对象
     * 自动处理markdown包裹、尾部逗号等问题，带重试机制
     */
    public static JsonNode parseJson(String llmOutput) {
        if (llmOutput == null || llmOutput.trim().isEmpty()) {
            log.warn("LLM输出为空");
            return MAPPER.createObjectNode();
        }

        // 第0步：先尝试直接解析（最快路径）
        try {
            String raw = llmOutput.trim();
            // 去掉可能的```json包裹
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            // 弯引号归一化
            raw = raw.replace('“', '"').replace('”', '"')
                     .replace('‘', '\'').replace('’', '\'')
                     .replace('，', ',').replace('：', ':').replace('、', ',');
            return MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            // 直接解析失败，走传统流程
        }

        String cleaned = extractJson(llmOutput);
        cleaned = fixCommonJsonErrors(cleaned);

        try {
            return MAPPER.readTree(cleaned);
        } catch (JsonProcessingException e) {
            log.warn("JSON解析失败，尝试更激进的修复: {}", e.getMessage());
            cleaned = aggressiveFix(cleaned);
            try {
                return MAPPER.readTree(cleaned);
            } catch (JsonProcessingException ex) {
                log.error("JSON解析彻底失败，原始输出前200字符: {}",
                        llmOutput.substring(0, Math.min(200, llmOutput.length())));
                return MAPPER.createObjectNode();
            }
        }
    }

    /**
     * 解析为指定Java类型
     */
    public static <T> T parseJson(String llmOutput, Class<T> clazz) {
        JsonNode node = parseJson(llmOutput);
        try {
            return MAPPER.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON转对象失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析为List
     */
    public static List<Map<String, Object>> parseJsonArray(String llmOutput) {
        String cleaned = extractJson(llmOutput);
        cleaned = fixCommonJsonErrors(cleaned);
        try {
            JsonNode node = MAPPER.readTree(cleaned);
            if (node.isArray()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = MAPPER.convertValue(node, List.class);
                return result;
            }
            // 如果是对象，尝试找第一个数组字段
            if (node.isObject()) {
                var fields = node.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (field.getValue().isArray()) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> result = MAPPER.convertValue(field.getValue(), List.class);
                        return result;
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("JSON数组解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 从LLM输出中提取JSON部分
     */
    private static final Pattern JSON_ARRAY_PATTERN =
            Pattern.compile("\\[[\\\\s\\\\S]*\\]");

    public static String extractJson(String llmOutput) {
        Matcher mdMatcher = MARKDOWN_JSON_PATTERN.matcher(llmOutput);
        if (mdMatcher.find()) return mdMatcher.group(1).trim();

        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(llmOutput);
        if (jsonMatcher.find()) return jsonMatcher.group();

        Matcher arrMatcher = JSON_ARRAY_PATTERN.matcher(llmOutput);
        if (arrMatcher.find()) return arrMatcher.group();

        return llmOutput.trim();
    }

    /**
     * 修复常见的JSON格式错误
     */
    private static String fixCommonJsonErrors(String json) {
        // 1. 移除尾部逗号（在 } 或 ] 之前）
        json = json.replaceAll(",\\s*([}\\]])", "$1");

        // 2. 移除注释（// 和 /* */）
        json = json.replaceAll("//[^\n]*", "");
        json = json.replaceAll("/\\*[\\s\\S]*?\\*/", "");

        // 3. 修复单引号（将属性名的单引号替换为双引号）
        // 注意：这只处理明显的模式
        json = json.replaceAll("'([^']*?)'\\s*:", "\"$1\":");

        // 4. Unicode弯引号归一化
        json = json.replaceAll("[\\u201c\\u201d\\u2018\\u2019\\u2033\\u2036]", "\"");
        json = json.replaceAll("[\\u300c\\u300d\\u300e\\u300f]", "\"");
        json = json.replaceAll("[\\uff0c\\uff1a\\u3001]", ",");
        json = json.replaceAll("[\\u2014\\u2013\\uff08\\uff09]", "-");
        // 5. 移除BOM和不可见字符
        json = json.replaceAll("[\\x00-\\x1F&&[^\\x09\\x0A\\x0D]]", "");

        return json.trim();
    }

    /**
     * 激进修复：当标准修复失败时使用
     * 修复了原先过滤掉合法数组元素行的bug
     */
    private static String aggressiveFix(String json) {
        // 找到第一个 { 和最后一个 }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        // 逐行修复：保留看起来像JSON的行
        StringBuilder sb = new StringBuilder();
        for (String line : json.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 保留：含冒号的行 / 括号行 / 以逗号结尾的行(array元素) / 引号包裹的值
            if (trimmed.contains(":") || trimmed.equals("{") || trimmed.equals("}")
                    || trimmed.equals("[") || trimmed.equals("]")
                    || trimmed.endsWith(",") || trimmed.startsWith("\"")) {
                sb.append(trimmed).append("\n");
            }
        }

        return fixCommonJsonErrors(sb.toString());
    }

    /**
     * 构建输出格式指令（追加到System Prompt末尾）
     */
    public static String buildFormatInstruction(String jsonSchema, String jsonExample) {
        return String.format("""

                ---
                【输出格式要求 - 必须严格遵守】
                你必须只输出以下JSON格式，不要输出任何其他内容（不要用markdown代码块包裹，不要加解释文字）：

                JSON Schema:
                %s

                输出示例:
                %s

                注意:
                1. 直接输出纯JSON，不要用 ```json 包裹
                2. 确保所有字符串用双引号
                3. 数组和对象的最后一个元素后面不要加逗号
                4. 没有信息的字段填写 "未指定" 或 []
                """, jsonSchema, jsonExample);
    }
}
