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
- 位置：C:\Users\李浩栋\Documents\Obsidian Vault\博学优课项目-面试题库.md
- 每学完一块内容，把对应面试题和背诵版答案追加进去

## 阶段三：RabbitMQ 延时关单（已完成）
- RabbitMQ 容器：端口 5672（程序）/ 15672（管理界面，guest/guest）
- 队列：order.delay.queue（TTL 15秒）→ 死信交换机 order.dlx.exchange → order.close.queue
- 下单后自动发延迟消息；到期检查订单仍待支付(0)则关闭为已取消(2)
- 新增接口：POST /api/orders/{id}/pay（模拟支付成功）
- 新增类：RabbitConfig、OrderCloseConsumer（删除了 demo 演示类）

## 加餐C：MQ 可靠性（已完成，2026-09-19）
- 生产者确认：application.yml 开 publisher-confirm-type=correlated；发送带 CorrelationData；RabbitTemplate 全局 ConfirmCallback（ack/nack 打日志，nack 需补偿重发）+ ReturnsCallback（mandatory=true，路由不到队列时回调）
- 持久化：交换机/队列 durable=true（RabbitConfig 已有）；消息 deliveryMode=PERSISTENT（Spring AMQP 默认）
- 消费者可靠性：acknowledge-mode=manual，业务成功才 channel.basicAck；失败不 ack → 本地重试 3 次（1s→2s）→ 重试耗尽 reject(requeue=false) → 进业务死信 order.dead.queue（DeadLetterConsumer 打日志兜底）
- 幂等：closeIfUnpaid 原子更新 UPDATE ... WHERE status=0，重复消息/重试无害（阶段3已做）
- 拓扑：delay.queue(TTL15s) → order.dlx.exchange → close.queue（消费失败 → order.business.dlx.exchange → order.dead.queue）
- 测试：管理界面 http://localhost:15672 往 order.close.queue 投递 "abc" → 观察日志重试3次 → 死信队列消息+1 → [真死信] 日志
- 顺序性：本轮没做（关单无强顺序要求），秒杀场景再讲

## 进度存档（2026-09-19）
- 阶段0-2：环境 + 单体骨架 + JWT登录鉴权 ✅
- 阶段3：RabbitMQ 延迟队列超时自动关单（TTL+死信+模拟支付，含幂等原子更新）✅
- 阶段4：课程接口 Redis 缓存（Cache Aside）+ 学习对话 List（learn 模块）✅
- 阶段5：Vue3 前端骨架 ✅；登录/注册页 + axios 封装 + 路由守卫 ✅；课程商城页 + 下单（5-3）✅（模拟支付 + 15 秒关单倒计时轮询）；我的课程 + 学习对话（5-4）✅（新增 GET /api/orders/mine 用 selectBatchIds 批量组装课程信息，避免 N+1；抽 AppHeader 组件三页复用；学习对话接阶段4的 Redis List）；下一步=加餐C MQ 进阶
- 加餐C（✅ 已完成并真实验证 2026-09-20）：MQ 可靠性四板斧——生产者确认(correlated+CorrelationData 回调)、持久化(durable+deliveryMode)、消费者手动ACK+本地重试3次(1s→2s 退避，代码自己实现)+显式 basicNack(requeue=false) 进死信；幂等复用原子更新 WHERE status=0
  - 验证结果：下单 → [MQ确认] correlationId 对上；15秒后关单+ack；投 abc 重试3次→拒绝→死信→死信消费者记录并 ack（dead.queue ack=5、messages=0）
  - 实战踩坑3个（详见 Obsidian 知识库 01 文件第九节）：① 队列参数不可变(406)要删队列重建；② manual 模式下 Spring retry 耗尽隐式拒收不可靠→自己实现重试+显式 nack；③ manual 全局生效，死信消费者也要手动 ack
- Redisson（✅ 已完成 2026-09-21）：下单防重改用 Redisson RLock（可重入+看门狗自动续期），自研 RedisLock 保留为原理版；秒杀仍用 Lua（扣库存不是抢锁场景）
- 加餐A 分布式锁 ✅：SETNX+Lua 原理版 + Redisson 生产版 + 并发单元测试（知识库 02）
- 加餐B 秒杀 ✅：Redis Lua 预扣库存 + MQ 削峰落库 + 幂等/补偿/失败进死信（知识库 03）
- 阶段6 微服务（进行中 2026-09-28）：
  - 6-1 多模块拆分 ✅：boxue-common 公共库 + user(8081)/course(8082)/order(8083) 三服务；共享库过渡 + 三服务统一 JWT 密钥
  - 6-2 Nacos 注册中心 ✅：三服务+网关注册，心跳保活/临时实例（面试题已记 Obsidian java\Spring Cloud）
  - 6-3 网关 ✅：Spring Cloud Gateway(8080) lb 路由 + 全局过滤器统一鉴权(JWT+Redis)，服务端改信 X-User-Id 头；踩坑：lb:// 必须加 spring-cloud-starter-loadbalancer（否则 503）
  - 6-4 OpenFeign ✅：订单服务远程调课程服务（单查/批量/列表），共享库过渡退役；遗留：user 表直查可当练习改 Feign
  - 6-5 收尾 ✅（2026-10-07）：前端改走网关 8080 + Vite 代理；网关配 globalcors；踩坑：浏览器直连 8080 被拦(ERR_BLOCKED_BY_CLIENT)→Vite 同源代理解决；axios baseURL 双 /api 404→baseURL 设 /；阶段6 面试题已入 Obsidian（Spring Cloud/Nacos/OpenFeign/网关）
- 启动（微服务时代）：docker compose up -d（含 Nacos）；IDEA 跑 BoxueUserApplication(8081)/BoxueCourseApplication(8082)/BoxueOrderApplication(8083)/BoxueGatewayApplication(8080)；前端 npm run dev(5173) 指向网关 8080
- 加餐顺序（已定 C→A→B）：加餐C ✅ → 加餐A 分布式锁（SETNX+Lua）+ 单元测试@SpringBootTest 穿插在各加餐验收里 → 加餐B 秒杀综合实战（Redis 预扣库存+Lua原子+MQ削峰落库）
- 启动（微服务时代）：见进度存档「启动」行
- 面试笔记：C:\Users\李浩栋\Documents\Obsidian Vault\博学优课项目-面试题库.md

## 学习约定（你说过的，新线程必须遵守）
- 节奏：先跑通 → 再加深（两轮制）
- 教学顺序（2026-09-19/20 你两次纠正后固定，以后每次讲都这样）：
  1. 先讲「要实现什么功能」：业务问题/场景，讲清楚为什么需要它
  2. 再讲「实现思路」：必须大白话，允许用生活比喻；出现任何一个专业术语都要当场解释，禁止术语堆砌
  3. 带看代码：新增/修改的代码「逐行」讲——每一行代码干什么用；里面用到的每个方法/注解/类都要讲（没见过的方法单独解释：它是什么、哪个包、干什么、为什么这里用）；不能只讲个大概
  4. 最后面试拷打：根据这个功能的实现，列出面试官可能问的问题 + 答案（可以出题考我，但答案必须给我）
- 不要总结「简历一句话」
- 改完代码必须带我把具体改动逐段看一遍（你说过：光看说了哪些地方，找不到具体改了哪些代码）
- 项目知识点记录（说「记进去」/「记录」才写）：Obsidian「C:\Users\李浩栋\Documents\Obsidian Vault\java\博学优课-知识库」目录下，每次学一个新知识点就新建一个独立文件（完整代码+讲解都存在里面），NOTES.md 不堆代码；面试题另记在「C:\Users\李浩栋\Documents\Obsidian Vault\博学优课项目-面试题库.md」，一条条独立写，不混代码
- 运行/验证的分工（2026-09-21 你明确要求）：你自己动手跑和验证，我只负责教步骤和讲解——启动服务、下单、看日志、投消息这些全由你做；卡住了把现象贴给我，我帮你分析。我不再自己启动服务/帮跑验证
- 面试题只讲不记；只有你说「记进去」才写入 Obsidian：C:\Users\李浩栋\Documents\Obsidian Vault\博学优课项目-面试题库.md
- 测试工具：接口测试用 Postman；Redis 用 Another Redis Desktop Manager；后端在 IDEA 跑（SDK 用 JDK17）；命令在 Cursor 终端敲
- 目标（2026-09-21 你明确）：做一个**企业级项目**、提高简历含金量；教学上先讲原理再上工程框架（分布式锁例子：自研 SETNX+Lua 版保留理解原理，生产选择集成 Redisson）

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
| 加餐A | 分布式锁（SETNX+Lua）→ 单元测试（@SpringBootTest） | ✅（自研原理版+Redisson 生产版） |
| 加餐B | 秒杀综合实战：Redis 预扣库存 + Lua 原子 + MQ 削峰落库 | ✅ |
| 加餐C | MQ 进阶：生产者确认 + 手动ACK + 持久化 + 重试进真死信 + 幂等总结 | ✅ |
| 6 微服务 | Gateway + 用户/课程/订单服务；服务拆分、网关统一鉴权 | ✅（6-1 多模块 / 6-2 Nacos / 6-3 网关统一鉴权 / 6-4 OpenFeign / 6-5 前端走网关+Vite代理） |
| 7 智能助手 | AI 学习助教（手写 Ollama 网关，非 Spring AI 框架）+ Tool Calling + RAG | 进行中（7-1~7-3 ✅ 手写版；**已换 Spring AI 官方框架 + 阿里云 DashScope 通义千问**，待验证） |
| 8 MCP 可选 | 把查询能力封装成 MCP 协议 | 可选 |
## 阶段7 进度（2026-10-08）
- 7-1 ✅：AI 学习助教第一版——POST /api/learn/ai/chat，RestClient 直调本机 Ollama /api/chat（模型 llama3.2，可切 qwen2.5-coder:7b），返回值 Result<String>，异常转 BizException
- 技术决策（重要）：不用 Spring AI 框架——spring-ai-bom 0.8.x 只在 repo.spring.io（现强制登录 401），中央/阿里/华为/腾讯都没有；新版 1.0.0-M6+ 要 Boot 3.4+ 会牵连 Spring Cloud/Nacos 全家升级 → 手写封装（企业也常自己封装 LLM 网关）
- 7-2 ✅：多轮记忆——每次提问把 Redis List 历史（boxue:learn:chat:{userId}）发给模型，并把 user/assistant 一问一答存回 Redis，复用 LearnChatService；已提交 2011e92（7-1+7-2）
- 7-3 ✅ 手写版：Tool Calling——model 声明 query_courses -> 后端查真实课程 -> role=tool 回传 -> 模型组织回答（MAX_TOOL_ROUNDS=5 防死循环）；已提交 883ab5f\n- 【重大切换 2026-10-10】手写调 Ollama 改为 **Spring AI 1.1.2 官方框架 + 阿里云百炼 DashScope（OpenAI 兼容模式，qwen-plus）**；全家桶升级：Boot 3.2.5→3.5.10、Spring Cloud 2023.0.1→2025.0.0、Alibaba 2023.0.1.0→2025.0.0.0、MyBatis-Plus 3.5.6→3.5.17、加 spring-ai-bom 1.1.2 + spring-ai-starter-model-openai；代码改用 ChatClient 链式 + @Tool（CourseTools）；yml 用 ${DASHSCOPE_API_KEY} 环境变量（密钥不提交 git）\n- 7-3b 进行中：加"我的已购课程"工具 list_my_courses——course-service 新增 OpenFeign OrderClient（@RequestHeader X-User-Id 传身份）+ order-service 新增内部接口 /api/orders/mine/course-ids（已支付订单去重 id）；@Tool 自动注册第二个工具\n- 后续：验证后 7-4 RAG、7-5 前端接入（request.js 超时 5s 必须调大）

- 每阶段固定节奏：讲清为什么 → 一起写最小可运行代码 → 你动手改一处 → 验收 + 面试怎么答；产出「简历一句话」