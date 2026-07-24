package com.eduagent.agent;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent基础接口 — 所有Agent必须实现
 *
 * 实现InitializingBean接口，在Spring初始化完成后自动注册到AgentRegistry。
 * 子类只需实现业务方法，无需手动调用register()。
 */
public interface BaseAgent extends InitializingBean {

    /**
     * Agent唯一名称（中文，如"知识库检索智能体"）
     */
    String getName();

    /**
     * Agent类型
     */
    AgentRegistry.AgentType getType();

    /**
     * Agent描述
     */
    default String getDescription() {
        return getName();
    }

    /**
     * Agent版本号
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * 执行Agent核心逻辑
     *
     * @param context 共享上下文
     * @return Agent执行结果
     */
    AgentResult execute(AgentContext context);

    /**
     * Spring初始化完成后自动注册到AgentRegistry
     */
    @Override
    default void afterPropertiesSet() throws Exception {
        // 由子类通过 @Autowired 注入 AgentRegistry 后调用 register
        // 此处为默认空实现，具体注册逻辑在各Agent子类中完成
    }
}
