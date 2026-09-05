package com.ygq.feedly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.feedly.entity.UserInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    @Select("<script>" +
            "SELECT id, nickname, avatar FROM sk_user WHERE id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<UserInfo> selectByIds(@Param("userIds") List<Long> userIds);

    @Insert("<script>" +
            "INSERT INTO sk_user (id, nickname, avatar, create_time) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.nickname}, #{item.avatar}, #{item.createTime})" +
            "</foreach>" +
            "</script>")
    void insertBatch(@Param("list") List<UserInfo> users);

}