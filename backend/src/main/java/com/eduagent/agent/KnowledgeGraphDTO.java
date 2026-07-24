package com.eduagent.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识图谱数据结构
 * LLM生成的知识图谱JSON对应的Java对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphDTO {

    /** 图类型: knowledge_graph / mindmap / flowchart */
    private String type;

    /** 中心主题 */
    private String centralTopic;

    /** 节点列表 */
    private List<GraphNode> nodes;

    /** 边列表 */
    private List<GraphEdge> edges;

    /** 文本大纲（降级兜底） */
    private String textOutline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {
        /** 节点唯一ID */
        private String id;
        /** 显示标签 */
        private String label;
        /** 节点类型: TOPIC / MODULE / CONCEPT / SKILL / PREREQUISITE */
        private String nodeType;
        /** 难度: 入门/基础/中级/高级 */
        private String difficulty;
        /** 描述/知识点内容 */
        private String description;
        /** 所属阶段（如 "阶段1"） */
        private String stage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {
        /** 源节点ID */
        private String source;
        /** 目标节点ID */
        private String target;
        /** 关系类型: PREREQUISITE / CONTAINS / RELATED_TO / NEXT */
        private String relationType;
        /** 关系标签（可选） */
        private String label;
    }
}
