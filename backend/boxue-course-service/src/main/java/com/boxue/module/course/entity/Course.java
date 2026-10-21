package com.boxue.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String cover;
    private BigDecimal price;
    private String description;
    private String teacherName;
    /** 课程分类，商城页按它筛选（如：后端开发 / 前端开发 / 数据库 / AI 应用 / 架构与中间件） */
    private String category;
    /** 课时数 */
    private Integer lessonCount;
    /** 学习人数 */
    private Integer studentCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
