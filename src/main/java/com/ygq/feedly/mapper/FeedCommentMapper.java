package com.ygq.feedly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ygq.feedly.entity.FeedComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FeedCommentMapper extends BaseMapper<FeedComment> {

    /**
     * 查询动态的评论列表（按时间倒序）
     */
    @Select("SELECT * FROM feed_comment " +
            "WHERE feed_id = #{feedId} AND is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{limit}")
    List<FeedComment> selectByFeedId(@Param("feedId") Long feedId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计评论总数
     */
    @Select("SELECT COUNT(*) FROM feed_comment " +
            "WHERE feed_id = #{feedId} AND is_deleted = 0")
    int countByFeedId(@Param("feedId") Long feedId);
}