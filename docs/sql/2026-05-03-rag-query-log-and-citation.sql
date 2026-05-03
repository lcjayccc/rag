CREATE TABLE IF NOT EXISTS rag_query_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL COMMENT '提问用户ID',
  question TEXT NOT NULL COMMENT '用户原始问题',
  retrieved_count INT NOT NULL DEFAULT 0 COMMENT '召回的知识切片数量',
  top_score DOUBLE NULL COMMENT '最高相似度分数',
  is_rag_hit TINYINT NOT NULL DEFAULT 0 COMMENT '1=命中知识库 0=未命中',
  is_rejected TINYINT NOT NULL DEFAULT 0 COMMENT '1=因无相关知识拒答',
  doc_ids_hit JSON NULL COMMENT '命中的文档ID列表',
  min_score_used DOUBLE NULL COMMENT '本次检索使用的最低相似度阈值',
  latency_ms INT NOT NULL DEFAULT 0 COMMENT '从接收问题到回答完成的总耗时',
  create_time DATETIME NOT NULL,
  INDEX idx_user_id (user_id),
  INDEX idx_is_rag_hit (is_rag_hit),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 查询日志';
