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

## 面试笔记（Obsidian）
- 位置：C:\Users\李浩栋\Documents\Obsidian Vault\面试准备\博学优课项目-面试题库.md
- 每学完一块内容，把对应面试题和背诵版答案追加进去

## 阶段三：RabbitMQ 延时关单（已完成）
- RabbitMQ 容器：端口 5672（程序）/ 15672（管理界面，guest/guest）
- 队列：order.delay.queue（TTL 15秒）→ 死信交换机 order.dlx.exchange → order.close.queue
- 下单后自动发延迟消息；到期检查订单仍待支付(0)则关闭为已取消(2)
- 新增接口：POST /api/orders/{id}/pay（模拟支付成功）
- 新增类：RabbitConfig、OrderCloseConsumer（删除了 demo 演示类）
