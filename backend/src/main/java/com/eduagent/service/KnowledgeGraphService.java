package com.eduagent.service;

import com.eduagent.agent.KnowledgeGraphDTO;
import com.eduagent.agent.KnowledgeGraphDTO.GraphNode;
import com.eduagent.agent.KnowledgeGraphDTO.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

/**
 * Neo4j 知识图谱存储与查询服务
 */
@Slf4j
@Service
public class KnowledgeGraphService {

    @Value("${spring.neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${spring.neo4j.authentication.username:neo4j}")
    private String neo4jUser;

    @Value("${spring.neo4j.authentication.password:password123}")
    private String neo4jPassword;

    private Driver driver;

    @PostConstruct
    public void init() {
        try {
            driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPassword));
            driver.verifyConnectivity();
            log.info("Neo4j连接成功: {}", neo4jUri);
        } catch (Exception e) {
            log.error("Neo4j连接失败: {}", e.getMessage());
            driver = null;
        }
    }

    @PreDestroy
    public void close() {
        if (driver != null) driver.close();
    }

    /**
     * 保存知识图谱到Neo4j
     */
    public void saveKnowledgeGraph(String topic, String userId, KnowledgeGraphDTO graph) {
        if (driver == null) {
            log.warn("Neo4j未连接，跳过图谱保存");
            return;
        }
        try (Session session = driver.session()) {
            // 创建中心主题节点
            session.executeWrite(tx -> {
                tx.run("MERGE (t:Topic {name: $name}) " +
                       "SET t.userId = $userId, t.updatedAt = timestamp()",
                       Map.of("name", topic, "userId", userId));

                // 创建知识节点
                if (graph.getNodes() != null) {
                    for (GraphNode node : graph.getNodes()) {
                        tx.run("MERGE (n:Concept {id: $id}) " +
                               "SET n.label = $label, n.nodeType = $nodeType, " +
                               "n.difficulty = $difficulty, n.description = $description, " +
                               "n.stage = $stage",
                               Map.of("id", node.getId(), "label", node.getLabel(),
                                      "nodeType", node.getNodeType() != null ? node.getNodeType() : "CONCEPT",
                                      "difficulty", node.getDifficulty() != null ? node.getDifficulty() : "基础",
                                      "description", node.getDescription() != null ? node.getDescription() : "",
                                      "stage", node.getStage() != null ? node.getStage() : ""));
                    }
                }

                // 创建边
                if (graph.getEdges() != null) {
                    for (GraphEdge edge : graph.getEdges()) {
                        String relType = edge.getRelationType() != null ? edge.getRelationType() : "RELATED_TO";
                        String query = String.format(
                            "MATCH (a:Concept {id: $src}), (b:Concept {id: $tgt}) " +
                            "MERGE (a)-[r:%s]->(b) " +
                            "SET r.label = $label", relType);
                        tx.run(query, Map.of("src", edge.getSource(), "tgt", edge.getTarget(),
                                             "label", edge.getLabel() != null ? edge.getLabel() : ""));
                    }
                }

                // Topic → 所有概念节点
                tx.run("MATCH (t:Topic {name: $name}), (c:Concept) " +
                       "WHERE c.id STARTS WITH $prefix " +
                       "MERGE (t)-[:CONTAINS]->(c)",
                       Map.of("name", topic, "prefix", topic.replaceAll("[\\s\\-]", "_") + "_"));

                return null;
            });
            log.info("知识图谱已保存到Neo4j: topic={}, nodes={}, edges={}",
                    topic, graph.getNodes() != null ? graph.getNodes().size() : 0,
                    graph.getEdges() != null ? graph.getEdges().size() : 0);
        } catch (Exception e) {
            log.error("保存知识图谱失败: {}", e.getMessage());
        }
    }

    /**
     * 查询某主题的知识图谱
     */
    public Map<String, Object> getKnowledgeGraph(String topic) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (driver == null) {
            result.put("nodes", List.of());
            result.put("edges", List.of());
            return result;
        }

        try (Session session = driver.session()) {
            // 查询节点
            List<Map<String, Object>> nodes = session.executeRead(tx -> {
                var r = tx.run("MATCH (t:Topic {name: $name})-[:CONTAINS]->(c:Concept) " +
                               "RETURN c.id AS id, c.label AS label, c.nodeType AS nodeType, " +
                               "c.difficulty AS difficulty, c.description AS description, " +
                               "c.stage AS stage", Map.of("name", topic));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    list.add(r.next().asMap());
                }
                return list;
            });

            // 查询边
            List<Map<String, Object>> edges = session.executeRead(tx -> {
                var r = tx.run("MATCH (t:Topic {name: $name})-[:CONTAINS]->(c:Concept) " +
                               "MATCH (c)-[rel]->(c2:Concept) " +
                               "RETURN c.id AS source, c2.id AS target, type(rel) AS relationType, rel.label AS label",
                               Map.of("name", topic));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    list.add(r.next().asMap());
                }
                return list;
            });

            result.put("topic", topic);
            result.put("nodes", nodes);
            result.put("edges", edges);
            log.info("查询知识图谱: topic={}, nodes={}, edges={}", topic, nodes.size(), edges.size());
        } catch (Exception e) {
            log.error("查询知识图谱失败: {}", e.getMessage());
            result.put("nodes", List.of());
            result.put("edges", List.of());
        }
        return result;
    }

    /**
     * 获取用户的所有知识图谱主题
     */
    public List<String> getUserTopics(String userId) {
        if (driver == null) return List.of();
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var r = tx.run("MATCH (t:Topic) WHERE t.userId = $userId RETURN t.name ORDER BY t.updatedAt DESC LIMIT 20",
                               Map.of("userId", userId));
                List<String> topics = new ArrayList<>();
                while (r.hasNext()) {
                    topics.add(r.next().get("t.name").asString());
                }
                return topics;
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean isAvailable() {
        return driver != null;
    }
}
