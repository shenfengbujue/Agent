package com.eduagent.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI配置集中管理
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfig {

    /** API密钥 */
    private String apiKey;

    /** 模型名称 */
    private String model = "qwen-turbo";

    /** API端点 */
    private String endpoint;

    /** 温度参数 (0.0-2.0)，越低越确定 */
    private Double temperature = 0.7;

    /** 最大输出token数 */
    private Integer maxTokens = 4096;

    /** 重试次数 */
    private Integer maxRetries = 2;

    /** 请求超时(秒) */
    private Integer timeoutSeconds = 60;
}
