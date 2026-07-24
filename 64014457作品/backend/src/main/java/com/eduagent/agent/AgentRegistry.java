package com.eduagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent统一注册中心（轻量级Spring Bean注入模式）
 *
 * 所有实现BaseAgent接口的Agent在Spring初始化完成后自动注册，
 * 通过 @Autowired List<BaseAgent> 实现自动发现。
 *
 * 改造要点:
 * 1. 替代CoordinatorAgent中硬编码的Agent注入
 * 2. 支持按名称/类型查找Agent
 * 3. 线程安全（ConcurrentHashMap）
 */
@Slf4j
@Component
public class AgentRegistry {

    /** Agent名称 → Agent实例映射 */
    private final Map<String, BaseAgent> agentsByName = new ConcurrentHashMap<>();

    /** Agent类型 → Agent实例列表映射 */
    private final Map<AgentType, List<BaseAgent>> agentsByType = new ConcurrentHashMap<>();

    /**
     * 注册Agent（由BaseAgent的afterPropertiesSet自动调用）
     */
    public void register(BaseAgent agent) {
        agentsByName.put(agent.getName(), agent);
        agentsByType.computeIfAbsent(agent.getType(), k -> new ArrayList<>()).add(agent);
        log.info("Agent已注册: name={}, type={}, class={}",
                agent.getName(), agent.getType(), agent.getClass().getSimpleName());
    }

    /**
     * 按名称获取Agent
     */
    public Optional<BaseAgent> getAgent(String name) {
        return Optional.ofNullable(agentsByName.get(name));
    }

    /**
     * 按类型获取所有Agent
     */
    public List<BaseAgent> getAgentsByType(AgentType type) {
        return agentsByType.getOrDefault(type, List.of());
    }

    /**
     * 获取所有已注册Agent
     */
    public Collection<BaseAgent> getAllAgents() {
        return Collections.unmodifiableCollection(agentsByName.values());
    }

    /**
     * 获取所有Agent名称
     */
    public Set<String> getAgentNames() {
        return Collections.unmodifiableSet(agentsByName.keySet());
    }

    /**
     * 获取已注册Agent数量
     */
    public int getAgentCount() {
        return agentsByName.size();
    }

    /**
     * 检查Agent是否已注册
     */
    public boolean isRegistered(String name) {
        return agentsByName.containsKey(name);
    }

    /**
     * Agent类型枚举
     */
    public enum AgentType {
        /** 解析类：需求解析、意图识别 */
        PARSER,
        /** 生成类：知识生成、练习题生成、图表生成、图片生成 */
        GENERATOR,
        /** 规划类：路径规划 */
        PLANNER,
        /** 审核类：内容审核、质量评估 */
        REVIEWER,
        /** 聚合类：总结、格式化 */
        AGGREGATOR,
        /** 检索类：知识库检索、联网搜索 */
        RETRIEVER
    }
}
