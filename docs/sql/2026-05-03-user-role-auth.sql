-- 最小角色管理数据库变更。
-- 执行前请确认当前库为 campusrag，并备份 user 表。

ALTER TABLE user
  ADD COLUMN role ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER' AFTER email,
  ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用' AFTER role,
  ADD COLUMN last_login DATETIME NULL COMMENT '最近登录时间' AFTER status;

-- 将指定账号设置为管理员。请把 admin_username 替换为你的管理员用户名。
-- 推荐流程：先调用 /api/auth/register 创建账号，再执行下面这句把该账号提升为 ADMIN。
-- UPDATE user SET role = 'ADMIN' WHERE username = 'admin_username';

-- 注意：新版本登录只接受 BCrypt 哈希密码。
-- 如果旧用户的 password 字段仍是明文，需要通过注册接口重新创建账号，或在应用内生成 BCrypt 后再更新 password。
