package com.boxue.module.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boxue.common.exception.BizException;
import com.boxue.module.course.entity.Course;
import com.boxue.module.course.mapper.CourseMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    /** Redis 操作工具：存字符串 */
    private final StringRedisTemplate redisTemplate;
    /** JSON 序列化工具：把对象变成字符串存进 Redis，取出来再变回对象 */
    private final ObjectMapper objectMapper;

    /** 缓存 key：课程列表 */
    private static final String CACHE_LIST_KEY = "boxue:course:list";
    /** 缓存 key：课程详情前缀，如 boxue:course:id:1 */
    private static final String CACHE_DETAIL_PREFIX = "boxue:course:id:";
    /** 缓存过期时间：10 分钟（防止永远不变，保证最终能看到新数据） */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 空值标记：DB 查不到时往缓存写这个字符串，防止同一个不存在的 id 反复打 DB（缓存穿透）。
     * 用 "NULL" 而不是空串，是为了在 redis-cli 里一眼能看出这是标记而不是脏数据。
     */
    private static final String NULL_MARK = "NULL";

    /**
     * 空值缓存时间：必须比正常缓存短。
     * 如果课程刚创建或刚重新上架，缓存里的"不存在"会让它最多 60 秒看不到，这是可接受的延迟；
     * 用 10 分钟就太久了。
     */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    public List<Course> listOnline() {
        // 第 1 步：先查缓存
        String cached = redisTemplate.opsForValue().get(CACHE_LIST_KEY);
        if (cached != null) {
            log.info("[缓存] 课程列表命中缓存");
            try {
                return objectMapper.readValue(cached, new TypeReference<List<Course>>() {});
            } catch (Exception e) {
                log.warn("[缓存] 课程列表反序列化失败，忽略缓存", e);
            }
        }

        // 第 2 步：缓存没命中 → 查数据库
        log.info("[缓存] 课程列表未命中，查数据库");
        List<Course> list = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, 1)
                .orderByDesc(Course::getId));

        // 第 3 步：写回缓存（带 10 分钟过期时间）
        try {
            redisTemplate.opsForValue().set(CACHE_LIST_KEY, objectMapper.writeValueAsString(list), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[缓存] 课程列表写缓存失败", e);
        }
        return list;
    }

    /** 工具调用用：按课程名关键词模糊搜索上架课程；关键词为空则返回全部上架课程 */
    public List<Course> searchByTitle(String keyword) {
        // eq(status,1)：只搜上架课程；like(条件, 列, 值)：关键词为空时不加该条件
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, 1)
                .like(keyword != null && !keyword.isBlank(), Course::getTitle, keyword)
                .orderByDesc(Course::getId));
    }

    /** 批量查课程（服务间内部接口用；简单实现直接查库，场景够用） */
    public List<Course> batchGet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return courseMapper.selectBatchIds(ids);
    }
    public Course getById(Long id) {
        String key = CACHE_DETAIL_PREFIX + id;

        // 第 1 步：先查缓存
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // 命中空值标记：说明之前查过 DB 确实没有，直接拒绝，不再打库
            if (NULL_MARK.equals(cached)) {
                log.info("[缓存] 课程 {} 命中空值标记，直接拒绝", id);
                throw new BizException("课程不存在或已下架");
            }
            log.info("[缓存] 课程 {} 命中缓存", id);
            try {
                return objectMapper.readValue(cached, Course.class);
            } catch (Exception e) {
                log.warn("[缓存] 课程 {} 反序列化失败，忽略缓存", id, e);
            }
        }

        // 第 2 步：缓存没命中 → 查数据库
        log.info("[缓存] 课程 {} 未命中，查数据库", id);
        Course course = courseMapper.selectById(id);
        if (course == null || course.getStatus() == null || course.getStatus() != 1) {
            // 查不到也写一个空值标记（TTL 短），下一次同 id 直接命中，不再穿透到 DB
            try {
                redisTemplate.opsForValue().set(key, NULL_MARK, NULL_TTL);
            } catch (Exception e) {
                // 写缓存失败不能盖掉业务异常，否则用户看到的是 Redis 报错
                log.warn("[缓存] 课程 {} 写空值标记失败", id, e);
            }
            throw new BizException("课程不存在或已下架");
        }

        // 第 3 步：写回缓存
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(course), CACHE_TTL);
        } catch (Exception e) {
            log.warn("[缓存] 课程 {} 写缓存失败", id, e);
        }
        return course;
    }
}