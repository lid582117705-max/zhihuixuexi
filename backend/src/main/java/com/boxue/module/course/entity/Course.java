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
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
