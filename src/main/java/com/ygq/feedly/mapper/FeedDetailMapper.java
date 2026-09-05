package com.ygq.feedly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.feedly.entity.FeedDetail;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FeedDetailMapper extends BaseMapper<FeedDetail> {

    /**
     * 查询用户发布的动态（按时间倒序）
     */
    @Select("SELECT * FROM feed_detail " +
            "WHERE user_id = #{userId} AND is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}")
    List<FeedDetail> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 根据用户ID列表批量查询动态（用于拉模式）
     */
    @Select("<script>" +
            "SELECT * FROM feed_detail " +
            "WHERE user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +  // ← 这里加空格
            "AND is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<FeedDetail> selectByUserIds(@Param("userIds") List<Long> userIds, @Param("limit") int limit);
//    @Select("<script>" +
//            "SELECT * FROM feed_detail " +
//            "WHERE user_id IN " +
//            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
//            "#{id}" +
//            "</foreach>" +
//            "AND is_deleted = 0 " +
//            "ORDER BY create_time DESC " +
//            "LIMIT #{limit}" +
//            "</script>")
//    List<FeedDetail> selectByUserIds(@Param("userIds") List<Long> userIds, @Param("limit") int limit);

    /**
     * 根据feedId列表批量查询
     */
    @Select("<script>" +
            "SELECT * FROM feed_detail WHERE id IN " +
            "<foreach collection='feedIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "AND is_deleted = 0" +
            "</script>")
    List<FeedDetail> selectByFeedIds(@Param("feedIds") List<Long> feedIds);

    @Select("SELECT id FROM feed_detail " +
            "WHERE user_id = #{userId} AND is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}")
    List<Long> selectRecentFeedIdsByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Update("UPDATE feed_detail SET like_count = like_count + 1 WHERE id = #{feedId}")
    void incrementLikeCount(@Param("feedId") Long feedId);

    @Update("UPDATE feed_detail SET comment_count = GREATEST(0, comment_count - 1) WHERE id = #{feedId}")
    void decrementCommentCount(@Param("feedId") Long feedId);

    @Update("UPDATE feed_detail SET like_count = GREATEST(0, like_count - 1) WHERE id = #{feedId}")
    void decrementLikeCount(@Param("feedId") Long feedId);

    @Update("UPDATE feed_detail SET comment_count = comment_count + 1 WHERE id = #{feedId}")
    void incrementCommentCount(@Param("feedId") Long feedId);

    @Insert("<script>" +
            "INSERT INTO feed_detail (id, user_id, content, images, video_url, visible_range, " +
            "like_count, comment_count, forward_count, create_time, is_deleted) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.content}, #{item.images}, #{item.videoUrl}, " +
            "#{item.visibleRange}, #{item.likeCount}, #{item.commentCount}, #{item.forwardCount}, " +
            "#{item.createTime}, #{item.isDeleted})" +
            "</foreach>" +
            "</script>")
    void insertBatch(@Param("list") List<FeedDetail> list);

}