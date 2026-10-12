package com.boxue.module.learn.config;

import com.boxue.module.course.entity.Course;
import com.boxue.module.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG 资料加载器：应用启动后，把平台上架课程拼成一段段文字（Document），
 * 交给向量库自动向量化，之后用户提问就能按"语义相似度"检索到这些资料。
 * 项目小、课程少：直接一次加载全部；生产里一般是定时任务增量同步资料文档。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRagInitializer implements ApplicationRunner {

    private final CourseService courseService;
    private final VectorStore vectorStore;

    @Override
    public void run(ApplicationArguments args) {
        List<Course> courses = courseService.listOnline();
        if (courses.isEmpty()) {
            log.info("[RAG] 没有可加载的课程资料");
            return;
        }

        // 每门课 -> 一段纯文本 Document（带 metadata 方便以后过滤/定位）
        List<Document> documents = courses.stream()
                .map(course -> new Document(
                        "课程《" + course.getTitle() + "》讲师：" + safe(course.getTeacherName())
                                + "，价格：" + course.getPrice() + " 元。课程简介：" + safe(course.getDescription()),
                        Map.of("courseId", course.getId(), "title", safe(course.getTitle()))
                ))
                .toList();

        // add() 内部会对每段文本调用 EmbeddingModel 生成向量再入库
        vectorStore.add(documents);
        log.info("[RAG] 已向量化加载 {} 门课程资料", documents.size());
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}