-- Phase 5: 系统配置表
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value VARCHAR(512) NOT NULL,
    description VARCHAR(256) DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认配置项
INSERT INTO system_config (config_key, config_value, description) VALUES
('rag.retrieval.min-score', '0.65', 'RAG 检索相似度最低阈值'),
('rag.retrieval.top-k', '3', 'RAG 检索返回的最大片段数'),
('rate.limit.max-requests', '30', '每分钟每用户最大问答请求数');
