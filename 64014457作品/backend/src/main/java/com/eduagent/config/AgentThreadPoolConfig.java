package com.eduagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent线程池配置
 *
 * 替代原 CoordinatorAgent.executeParallelAgents() 中的 new Thread() 方式，
 * 提供可控的线程池管理，避免线程泄漏和资源耗尽。
 *
 * 配置说明:
 * - agentExecutor: 通用Agent执行线程池（知识检索、联网搜索、图谱生成等）
 * - imageGenExecutor: 图片生成专用线程池（超时更长，60s）
 * - reviewExecutor: 审核Agent专用线程池（低延迟，temperature=0保证一致性）
 */
@Slf4j
@Configuration
public class AgentThreadPoolConfig {

    /** 通用Agent线程池 — 核心配置 */
    private static final int AGENT_CORE_POOL_SIZE = 4;
    private static final int AGENT_MAX_POOL_SIZE = 8;
    private static final int AGENT_QUEUE_CAPACITY = 100;
    private static final long AGENT_KEEP_ALIVE_SECONDS = 60;

    /** 图片生成线程池 — 核心配置 */
    private static final int IMAGE_CORE_POOL_SIZE = 2;
    private static final int IMAGE_MAX_POOL_SIZE = 4;
    private static final int IMAGE_QUEUE_CAPACITY = 20;
    private static final long IMAGE_KEEP_ALIVE_SECONDS = 120;

    /** 审核线程池 — 核心配置 */
    private static final int REVIEW_CORE_POOL_SIZE = 2;
    private static final int REVIEW_MAX_POOL_SIZE = 4;
    private static final int REVIEW_QUEUE_CAPACITY = 50;
    private static final long REVIEW_KEEP_ALIVE_SECONDS = 60;

    /**
     * 通用Agent执行线程池
     * 用于: KnowledgeAgent、WebSearchAgent、GraphGenerationAgent
     */
    @Bean("agentExecutor")
    public ThreadPoolExecutor agentExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                AGENT_CORE_POOL_SIZE,
                AGENT_MAX_POOL_SIZE,
                AGENT_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(AGENT_QUEUE_CAPACITY),
                new AgentThreadFactory("agent"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("Agent通用线程池已创建: core={}, max={}, queue={}",
                AGENT_CORE_POOL_SIZE, AGENT_MAX_POOL_SIZE, AGENT_QUEUE_CAPACITY);
        return executor;
    }

    /**
     * 图片生成专用线程池（超时60s，区别于通用线程池30s）
     * 用于: ImageGenerationAgent
     */
    @Bean("imageGenExecutor")
    public ThreadPoolExecutor imageGenExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                IMAGE_CORE_POOL_SIZE,
                IMAGE_MAX_POOL_SIZE,
                IMAGE_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(IMAGE_QUEUE_CAPACITY),
                new AgentThreadFactory("image-gen"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("图片生成线程池已创建: core={}, max={}, queue={}",
                IMAGE_CORE_POOL_SIZE, IMAGE_MAX_POOL_SIZE, IMAGE_QUEUE_CAPACITY);
        return executor;
    }

    /**
     * 审核Agent专用线程池
     * 用于: ReviewAgent
     */
    @Bean("reviewExecutor")
    public ThreadPoolExecutor reviewExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                REVIEW_CORE_POOL_SIZE,
                REVIEW_MAX_POOL_SIZE,
                REVIEW_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(REVIEW_QUEUE_CAPACITY),
                new AgentThreadFactory("review"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        log.info("审核线程池已创建: core={}, max={}, queue={}",
                REVIEW_CORE_POOL_SIZE, REVIEW_MAX_POOL_SIZE, REVIEW_QUEUE_CAPACITY);
        return executor;
    }

    /**
     * Agent线程工厂 — 统一命名便于问题排查
     */
    private static class AgentThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        AgentThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(false);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    log.error("Agent线程未捕获异常: thread={}, error={}", thread.getName(), ex.getMessage(), ex));
            return t;
        }
    }
}
