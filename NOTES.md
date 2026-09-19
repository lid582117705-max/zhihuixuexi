# 阶段一学习笔记（跟着写，不懂就问）

## 当前目录
- docker-compose.yml：一键启动 MySQL + Redis
- docker/mysql/init.sql：建表 + 演示课程数据
- backend/：Spring Boot 单体后端

## 本机端口（避开冲突）
- MySQL：3307（用户 boxue / 密码 boxue123，库名 boxue）
- Redis：6379
- 后端：8081

## 常用命令
```bash
# 在项目根目录启动数据库
docker compose up -d

# 启动后端（需先装好 JDK17）
cd backend
mvn spring-boot:run
```

## 已实现接口
- GET  /api/health
- POST /api/users/register
- POST /api/users/login          # 返回 token，会话写入 Redis：boxue:auth:{userId}
- GET  /api/users/me             # 需要 Header: Authorization: Bearer {token}
- POST /api/users/logout         # 需要登录
- GET  /api/courses
- GET  /api/courses/{id}
- POST /api/orders               # 需要登录，body 只传 courseId
- GET  /api/orders/{id}          # 需要登录，只能看自己的订单
- GET  /api/orders/mine          # 需要登录，我的已购课程（已支付订单+课程信息批量组装）

## 面试笔记（Obsidian）
- 位置：C:\Users\李浩栋\Documents\Obsidian Vault\Notebook导入\博学优课项目-面试题库.md
- 每学完一块内容，把对应面试题和背诵版答案追加进去

## 阶段三：RabbitMQ 延时关单（已完成）
- RabbitMQ 容器：端口 5672（程序）/ 15672（管理界面，guest/guest）
- 队列：order.delay.queue（TTL 15秒）→ 死信交换机 order.dlx.exchange → order.close.queue
- 下单后自动发延迟消息；到期检查订单仍待支付(0)则关闭为已取消(2)
- 新增接口：POST /api/orders/{id}/pay（模拟支付成功）
- 新增类：RabbitConfig、OrderCloseConsumer（删除了 demo 演示类）

## 进度存档（2026-09-19）
- 阶段0-2：环境 + 单体骨架 + JWT登录鉴权 ✅
- 阶段3：RabbitMQ 延迟队列超时自动关单（TTL+死信+模拟支付，含幂等原子更新）✅
- 阶段4：课程接口 Redis 缓存（Cache Aside）+ 学习对话 List（learn 模块）✅
- 阶段5：Vue3 前端骨架 ✅；登录/注册页 + axios 封装 + 路由守卫 ✅；课程商城页 + 下单（5-3）✅（模拟支付 + 15 秒关单倒计时轮询）；我的课程 + 学习对话（5-4）✅（新增 GET /api/orders/mine 用 selectBatchIds 批量组装课程信息，避免 N+1；抽 AppHeader 组件三页复用；学习对话接阶段4的 Redis List）；下一步=加餐C MQ 进阶
- 加餐顺序（已定 C→A→B）：加餐C MQ 进阶（生产者确认+消费者手动ACK+持久化+失败重试进真死信+幂等总结）→ 加餐A 分布式锁（SETNX+Lua）+ 单元测试@SpringBootTest 穿插在各加餐验收里 → 加餐B 秒杀综合实战（Redis 预扣库存+Lua原子+MQ削峰落库）
- 后端启动：cd backend && mvn spring-boot:run（端口8081）；前端：cd frontend && npm run dev（端口5173）
- 面试笔记：C:\Users\李浩栋\Documents\Obsidian Vault\Notebook导入\博学优课项目-面试题库.md

## 学习约定（你说过的，新线程必须遵守）
- 节奏：先跑通 → 再加深（两轮制）；每阶段产出「简历一句话」
- 改完代码必须带我把具体改动逐段看一遍、新方法逐个讲（你说过：光看说了哪些地方，找不到具体改了哪些代码）
- 面试题只讲不记；只有你说「记进去」才写入 Obsidian：Notebook导入\博学优课项目-面试题库.md
- 测试工具：接口测试用 Postman；Redis 用 Another Redis Desktop Manager；后端在 IDEA 跑（SDK 用 JDK17）；命令在 Cursor 终端敲
- 目标：项目要能锻炼基础功 + 简历含金量（对标黑马点评，但缓存三大问题已练过不重复教）

## 环境雷区（踩过的坑，先看这条）
- Docker Desktop 要手动打开；报 npipe 连不上 = 发动机没开
- 拉 Docker 镜像前先关 VPN（否则报 failed size validation / 下载损坏）
- JDK 一致性：项目=17，Maven=17，IDEA 项目 SDK 也必须 17（曾误配 JDK25 导致 Lombok 编译崩 TypeTag UNKNOWN）
- npm 已配淘宝镜像 registry.npmmirror.com
- IDEA 曾因损坏的 .port 锁文件起不来：已把缓存目录改名备份 AppData\Local\JetBrains\IntelliJIdea2026.1.broken-20260918173903（确认稳定后可删）

## 老线程为何弃用（Codex 编辑器的 bug）
- 旧线程右侧编辑器出现幽灵标签 RabbitListener(queues = ...)，无法关闭、挡住正常操作
- 根因：标签 id 含英文双引号，Codex closeTab 拼 CSS 选择器时抛 SyntaxError: not a valid selector（应用 bug）
- 处理：另开新线程继续项目；旧线程未删除、聊天记录保留，可在列表里找回
## 全路线图一览（新线程按这个走）
| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 0 环境 | JDK / Maven / Node / MySQL / Redis / IDE + Docker Compose | ✅ |
| 1 单体骨架 | Spring Boot + MyBatis Plus + 用户/课程/订单（CRUD、分层、表设计） | ✅ |
| 2 登录鉴权 | BCrypt + JWT + 拦截器 + Redis 会话白名单（含退出） | ✅ |
| 3 订单延时 | RabbitMQ 延时关单（TTL+死信队列）+ 模拟支付 + 幂等原子更新 | ✅ |
| 4 缓存会话 | 课程接口 Redis 缓存（Cache Aside）+ 学习对话 List | ✅ |
| 5 Vue3 前端 | 登录/注册 + axios + 路由守卫 ✅；商城 + 下单 ✅；我的课程 + 学习对话 ✅ | ✅ |
| 加餐A | 分布式锁（SETNX+Lua）→ 单元测试（@SpringBootTest） | 待做（前端后） |
| 加餐B | 秒杀综合实战：Redis 预扣库存 + Lua 原子 + MQ 削峰落库 | 待做（微服务前） |
| 加餐C | MQ 进阶：生产者确认 + 消费者手动ACK + 持久化 + 失败重试进真死信 + 幂等总结 | 待做（建议在秒杀前） |
| 6 微服务 | Gateway + 用户/课程/订单服务；服务拆分、网关统一鉴权 | 未来 |
| 7 智能助手 | Spring AI + Tool Calling + RAG（Prompt、工具调用、检索增强） | 未来 |
| 8 MCP 可选 | 把查询能力封装成 MCP 协议 | 可选 |

- 每阶段固定节奏：讲清为什么 → 一起写最小可运行代码 → 你动手改一处 → 验收 + 面试怎么答；产出「简历一句话」