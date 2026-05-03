CREATE TABLE IF NOT EXISTS document_category (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL COMMENT '分类名称',
  code VARCHAR(64) NOT NULL COMMENT '分类编码，用于前后端稳定传参',
  description VARCHAR(255) NULL COMMENT '分类说明',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_document_category_code (code),
  INDEX idx_document_category_enabled_sort (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档分类';

INSERT INTO document_category (name, code, description, sort_order, enabled, create_time, update_time)
SELECT '教务处', 'academic', '课程、考试、学籍、培养方案等教务资料', 10, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_category WHERE code = 'academic');

INSERT INTO document_category (name, code, description, sort_order, enabled, create_time, update_time)
SELECT '学生工作处', 'student_affairs', '奖助学金、评优评先、学生管理等资料', 20, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_category WHERE code = 'student_affairs');

INSERT INTO document_category (name, code, description, sort_order, enabled, create_time, update_time)
SELECT '校级规章制度', 'policy', '学校层面的制度、通知和办事规范', 30, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_category WHERE code = 'policy');

INSERT INTO document_category (name, code, description, sort_order, enabled, create_time, update_time)
SELECT '招生就业', 'career', '招生、就业、实习和毕业相关资料', 40, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_category WHERE code = 'career');

INSERT INTO document_category (name, code, description, sort_order, enabled, create_time, update_time)
SELECT '其他', 'other', '暂未归类的校园资料', 99, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_category WHERE code = 'other');

ALTER TABLE document
  ADD COLUMN category_id BIGINT NULL COMMENT '文档分类ID' AFTER user_id;

CREATE INDEX idx_document_category_id ON document (category_id);
