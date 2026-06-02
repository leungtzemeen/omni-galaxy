package com.omnigalaxy.platform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnigalaxy.platform.user.domain.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}