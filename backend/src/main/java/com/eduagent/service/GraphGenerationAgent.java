package com.eduagent.service;

import com.eduagent.agent.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识图谱智能体
 * 通过LLM生成结构化知识图谱(nodes+edges)，存入Neo4j，前端用vis.js渲染交互图
 */
@Slf4j
@Service
public class GraphGenerationAgent {

    private final AIService aiService;
    private final KnowledgeGraphService graphService;

    public GraphGenerationAgent(AIService aiService, KnowledgeGraphService graphService) {
        this.aiService = aiService;
        this.graphService = graphService;
    }

    private static final String FORMAT_INSTRUCTION = """

            ---
            【输出格式要求 - 必须严格遵守】
            你是知识图谱构建专家。根据学习内容生成结构化知识图谱JSON：

            {
              "type": "knowledge_graph",
              "centralTopic": "学习主题名称",
              "nodes": [
                {
                  "id": "唯一ID",
                  "label": "节点名称",
                  "nodeType": "TOPIC/MODULE/CONCEPT/SKILL",
                  "difficulty": "入门/基础/中级/高级",
                  "description": "知识点描述",
                  "stage": "所属阶段"
                }
              ],
              "edges": [
                {
                  "source": "源节点ID",
                  "target": "目标节点ID",
                  "relationType": "PREREQUISITE/CONTAINS/RELATED_TO/NEXT",
                  "label": "关系描述"
                }
              ],
              "textOutline": "纯文本大纲(降级用)"
            }

            规则：nodes至少8个、edges至少6条。PREREQUISITE=前驱必学。直接输出纯JSON，不包裹。内容源自上游数据。
            """;

    public AgentResult generateGraph(String query, Map<String, AgentResult> agentOutputs, AgentContext context) {
        long startTime = System.currentTimeMillis();
        try {
            String topic = extractTopic(query, agentOutputs);
            String userId = context != null && context.getUserId() != null ?
                    context.getUserId().toString() : "anonymous";

            String userMessage = buildGraphPrompt(query, agentOutputs, context);
            String llmOutput = aiService.chatWithSystemPrompt(
                    SystemPrompts.GRAPH_GENERATION + FORMAT_INSTRUCTION, userMessage);

            if (llmOutput == null || llmOutput.trim().isEmpty()) {
                return buildFallback(query, startTime);
            }

            JsonNode json = JsonParserUtil.parseJson(llmOutput);
            List<Map<String, Object>> nodes = extractNodes(json);
            List<Map<String, Object>> edges = extractEdges(json);
            String textOutline = json.has("textOutline") ?
                    json.get("textOutline").asText().replace("\\n", "\n") : "";

            if (nodes.isEmpty()) {
                return buildFallback(query, startTime);
            }

            // 存Neo4j
            if (graphService.isAvailable()) {
                KnowledgeGraphDTO dto = buildDTO(topic, nodes, edges);
                graphService.saveKnowledgeGraph(topic, userId, dto);
            }

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("type", "knowledge_graph");
            resultData.put("centralTopic", topic);
            resultData.put("nodes", nodes);
            resultData.put("edges", edges);
            resultData.put("textOutline", textOutline);
            resultData.put("neo4jAvailable", graphService.isAvailable());

            long duration = System.currentTimeMillis() - startTime;
            log.info("知识图谱生成: topic={}, nodes={}, edges={}, {}ms", topic, nodes.size(), edges.size(), duration);
            return AgentResult.success("图生成智能体", resultData,
                    "知识图谱已生成: " + topic, duration);
        } catch (Exception e) {
            log.error("知识图谱失败: {}", e.getMessage());
            return buildFallback(query, startTime);
        }
    }

    // ---- 解析 ----

    private List<Map<String, Object>> extractNodes(JsonNode json) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (json.has("nodes")) {
            for (JsonNode n : json.get("nodes")) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", n.has("id") ? n.get("id").asText() : "n" + list.size());
                node.put("label", n.has("label") ? n.get("label").asText() : "");
                node.put("nodeType", n.has("nodeType") ? n.get("nodeType").asText() : "CONCEPT");
                node.put("difficulty", n.has("difficulty") ? n.get("difficulty").asText() : "基础");
                node.put("description", n.has("description") ? n.get("description").asText() : "");
                node.put("stage", n.has("stage") ? n.get("stage").asText() : "");
                list.add(node);
            }
        }
        return list;
    }

    private List<Map<String, Object>> extractEdges(JsonNode json) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (json.has("edges")) {
            for (JsonNode e : json.get("edges")) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", e.has("source") ? e.get("source").asText() : "");
                edge.put("target", e.has("target") ? e.get("target").asText() : "");
                edge.put("relationType", e.has("relationType") ? e.get("relationType").asText() : "RELATED_TO");
                edge.put("label", e.has("label") ? e.get("label").asText() : "");
                list.add(edge);
            }
        }
        return list;
    }

    private KnowledgeGraphDTO buildDTO(String topic, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        List<KnowledgeGraphDTO.GraphNode> gn = new ArrayList<>();
        for (Map<String, Object> n : nodes) {
            gn.add(KnowledgeGraphDTO.GraphNode.builder()
                    .id((String) n.get("id")).label((String) n.get("label"))
                    .nodeType((String) n.get("nodeType")).difficulty((String) n.get("difficulty"))
                    .description((String) n.get("description")).stage((String) n.get("stage")).build());
        }
        List<KnowledgeGraphDTO.GraphEdge> ge = new ArrayList<>();
        for (Map<String, Object> e : edges) {
            ge.add(KnowledgeGraphDTO.GraphEdge.builder()
                    .source((String) e.get("source")).target((String) e.get("target"))
                    .relationType((String) e.get("relationType")).label((String) e.get("label")).build());
        }
        return KnowledgeGraphDTO.builder().type("knowledge_graph").centralTopic(topic).nodes(gn).edges(ge).build();
    }

    // ---- 降级 ----

    private AgentResult buildFallback(String query, long startTime) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of("id", "root", "label", query, "nodeType", "TOPIC", "difficulty", "基础"));
        nodes.add(Map.of("id", "concept_1", "label", "基础知识", "nodeType", "CONCEPT", "difficulty", "入门"));
        nodes.add(Map.of("id", "concept_2", "label", "核心重点", "nodeType", "CONCEPT", "difficulty", "基础"));
        nodes.add(Map.of("id", "concept_3", "label", "实践应用", "nodeType", "SKILL", "difficulty", "中级"));
        List<Map<String, Object>> edges = new ArrayList<>();
        edges.add(Map.of("source", "root", "target", "concept_1", "relationType", "CONTAINS", "label", "包含"));
        edges.add(Map.of("source", "concept_1", "target", "concept_2", "relationType", "PREREQUISITE", "label", "前驱"));
        edges.add(Map.of("source", "concept_2", "target", "concept_3", "relationType", "NEXT", "label", "进阶"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "knowledge_graph");
        data.put("centralTopic", query);
        data.put("nodes", nodes);
        data.put("edges", edges);
        data.put("textOutline", "知识图谱降级方案");
        data.put("neo4jAvailable", graphService.isAvailable());

        long d = System.currentTimeMillis() - startTime;
        return AgentResult.degraded("图生成智能体", data, "知识图谱(降级)", d);
    }

    // ---- 保留旧接口兼容 + Prompt构建 ----

    @Deprecated
    public Map<String, Object> generateGraph(String query, Map<String, Object> knowledgeResult, Map<String, Object> webResult) {
        return Map.of("agent", "生成图智能体", "status", "deprecated", "data", List.of());
    }

    private String extractTopic(String query, Map<String, AgentResult> agentOutputs) {
        if (agentOutputs != null) {
            AgentResult req = agentOutputs.get("统筹解析智能体");
            if (req != null && req.getData() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) req.getData();
                Object s = m.get("subject");
                if (s != null && !s.toString().isEmpty()) return s.toString();
            }
        }
        return query.length() > 30 ? query.substring(0, 30) : query;
    }

    private String buildGraphPrompt(String query, Map<String, AgentResult> agentOutputs, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        String topic = extractTopic(query, agentOutputs);
        sb.append("你必须围绕 ").append(topic).append(" 生成知识图谱，所有节点和边必须与此主题直接相关。\n\n");

        if (agentOutputs != null) {
            AgentResult knowledge = agentOutputs.get("知识库检索智能体");
            if (knowledge != null && knowledge.getData() != null) {
                String knowledgeStr = knowledge.getData().toString();
                sb.append("以下知识内容作为唯一参考源，请从中提取核心模块/概念作为节点标签：\n");
                sb.append(knowledgeStr.substring(0, Math.min(2000, knowledgeStr.length()))).append("\n\n");
            }
        }
        return sb.toString();
    }
}
