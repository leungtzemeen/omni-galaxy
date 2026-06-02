package com.omnigalaxy.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserCredentialMapper extends BaseMapper<UserCredential> {
}