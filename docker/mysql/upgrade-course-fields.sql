-- ======================================================
-- 已有数据卷升级脚本（只执行一次）
--
-- 背景：course 表新增 3 个商城展示字段（分类 / 课时 / 学习人数）。
-- init.sql 只在「数据目录为空」的首次初始化时执行，已经跑过的数据卷不会重跑，
-- 所以老库要手动执行这个脚本。
--
-- 执行：
--   docker exec -i boxue-mysql mysql -uroot -proot123 boxue < docker/mysql/upgrade-course-fields.sql
--
-- 注意：
--   1. 里面的 ALTER TABLE 不可重复执行（第二次会报 Duplicate column），只跑一次。
--   2. INSERT 用 IGNORE + 显式 id，重复执行只会跳过，不会插重复数据。
--   3. 保留原有 id 1~3 的课程，订单表里引用的 course_id 不会失效。
-- ======================================================

USE boxue;

-- 防止 Windows 客户端默认字符集导致中文乱码
SET NAMES utf8mb4;

-- ---------- 1. 加字段 ----------
ALTER TABLE `course`
    ADD COLUMN `category`      VARCHAR(32) NOT NULL DEFAULT '后端开发' COMMENT '课程分类（商城页筛选用）' AFTER `teacher_name`,
    ADD COLUMN `lesson_count`  INT         NOT NULL DEFAULT 0 COMMENT '课时数' AFTER `category`,
    ADD COLUMN `student_count` INT         NOT NULL DEFAULT 0 COMMENT '学习人数' AFTER `lesson_count`,
    ADD KEY `idx_category` (`category`);

-- ---------- 2. 给原有 3 门课补字段 ----------
UPDATE `course` SET category = '后端开发',     lesson_count = 40, student_count = 5120 WHERE title = 'Java 从入门到精通';
UPDATE `course` SET category = '后端开发',     lesson_count = 36, student_count = 4380 WHERE title = 'Spring Boot 实战';
UPDATE `course` SET category = '架构与中间件', lesson_count = 30, student_count = 3020 WHERE title = 'Redis 核心原理与实战';

-- ---------- 3. 追加 17 门课程（id 4~20） ----------
INSERT IGNORE INTO `course`
    (`id`, `title`, `price`, `description`, `teacher_name`, `category`, `lesson_count`, `student_count`, `status`)
VALUES
    (4,  'MySQL 索引与调优',        89.00, '执行计划解读、索引下推、慢查询治理',        '赵老师', '数据库',       32, 4210, 1),
    (5,  '高并发系统设计',         199.00, '限流、降级、缓存、异步化的系统方法论',      '陈老师', '架构与中间件', 48, 2890, 1),
    (6,  'Vue3 组合式 API 实战',   109.00, 'script setup、响应式原理与组件设计',        '刘老师', '前端开发',     36, 3650, 1),
    (7,  'TypeScript 类型体操',    119.00, '泛型、条件类型、类型推导进阶',              '孙老师', '前端开发',     28, 1980, 1),
    (8,  'Spring Cloud 微服务实战', 219.00, '注册中心、网关、Feign、配置中心全链路',      '李老师', '架构与中间件', 52, 2460, 1),
    (9,  'RabbitMQ 消息可靠性',    139.00, '确认机制、死信队列、幂等与顺序性',          '王老师', '架构与中间件', 30, 1720, 1),
    (10, 'JVM 性能调优',           169.00, '内存模型、GC 调参、线上问题排查',           '周老师', '后端开发',     40, 2100, 1),
    (11, 'MyBatis-Plus 高效开发',   89.00, '条件构造器、分页插件、代码生成器',          '张老师', '后端开发',     24, 3150, 1),
    (12, 'Python 数据分析入门',     99.00, 'Pandas、NumPy 与可视化实战',                '吴老师', 'AI 应用',      30, 2780, 1),
    (13, '大模型应用开发实战',     259.00, 'Prompt 工程、工具调用、Agent 编排',          '郑老师', 'AI 应用',      45, 1560, 1),
    (14, 'RAG 检索增强生成',       189.00, '向量化、召回、重排与效果评估',              '郑老师', 'AI 应用',      32,  980, 1),
    (15, 'Docker 与 Kubernetes 入门', 149.00, '镜像构建、编排部署、服务暴露',            '陈老师', '架构与中间件', 38, 2240, 1),
    (16, '前端工程化与 Vite',      129.00, '构建原理、插件机制、按需加载',              '刘老师', '前端开发',     26, 1430, 1),
    (17, 'SQL 优化实战',           109.00, '慢 SQL 定位、执行计划、分页优化',           '赵老师', '数据库',       28, 2670, 1),
    (18, '分布式事务与一致性',     179.00, '2PC、TCC、可靠消息与最终一致',              '周老师', '架构与中间件', 34, 1120, 1),
    (19, 'React 18 核心原理',      139.00, 'Fiber、并发渲染、Hooks 实现原理',           '孙老师', '前端开发',     30, 1890, 1),
    (20, 'Elasticsearch 搜索实战', 159.00, '倒排索引、分词、聚合与相关性打分',          '吴老师', '数据库',       32,  860, 1);
