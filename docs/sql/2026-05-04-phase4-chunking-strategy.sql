-- Phase 4: 文档摄入增强 — DocumentCategory 新增 default_chunking_strategy
ALTER TABLE document_category
  ADD COLUMN default_chunking_strategy VARCHAR(32) DEFAULT 'FIXED_SIZE'
  COMMENT '默认切片策略: FIXED_SIZE / STRUCTURE_AWARE / SEMANTIC';

-- 为不同分类设置推荐的切片策略
-- 教务处（制度/通知类文档，有明确标题层级）→ 结构感知切片
UPDATE document_category
  SET default_chunking_strategy = 'STRUCTURE_AWARE'
  WHERE code = 'academic';

-- 校级规章制度（制度类文档，有明确标题层级）→ 结构感知切片
UPDATE document_category
  SET default_chunking_strategy = 'STRUCTURE_AWARE'
  WHERE code = 'policy';

-- 招生就业（FAQ/办事指南类文档）→ 语义边界切片
UPDATE document_category
  SET default_chunking_strategy = 'SEMANTIC'
  WHERE code = 'career';
