package com.ygq.feedly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.feedly.entity.UserRelation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserRelationMapper extends BaseMapper<UserRelation> {

    /**
     * 查询用户的关注列表
     */
    @Select("SELECT followee_id FROM user_relation " +
            "WHERE follower_id = #{userId} AND is_deleted = 0")
    List<Long> selectFolloweeIds(@Param("userId") Long userId);

    /**
     * 查询用户的粉丝列表（分页）
     */
    @Select("SELECT follower_id FROM user_relation " +
            "WHERE followee_id = #{userId} AND is_deleted = 0 " +
            "LIMIT #{offset}, #{limit}")
    List<Long> selectFollowerIds(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /**
     * 统计粉丝数量
     */
    @Select("SELECT COUNT(*) FROM user_relation " +
            "WHERE followee_id = #{userId} AND is_deleted = 0")
    int countFollowers(@Param("userId") Long userId);

    /**
     * 检查是否已关注
     */
    @Select("SELECT COUNT(*) > 0 FROM user_relation " +
            "WHERE follower_id = #{followerId} AND followee_id = #{followeeId} AND is_deleted = 0")
    boolean exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    // UserRelationMapper.java
    @Insert("<script>" +
            "INSERT IGNORE INTO user_relation (follower_id, followee_id, create_time, is_deleted) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.followerId}, #{item.followeeId}, #{item.createTime}, #{item.isDeleted})" +
            "</foreach>" +
            "</script>")
    void insertIgnoreBatch(@Param("list") List<UserRelation> list);
}