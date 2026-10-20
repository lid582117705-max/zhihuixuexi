package com.boxue.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.boxue.module.order.entity.DeadLetter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterMapper extends BaseMapper<DeadLetter> {
}
