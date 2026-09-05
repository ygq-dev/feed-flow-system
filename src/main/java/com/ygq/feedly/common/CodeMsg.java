package com.ygq.feedly.common;

import lombok.Getter;

@Getter
public enum CodeMsg {

    SUCCESS(200, "success"),

    // 通用错误
    SERVER_ERROR(500100, "服务端异常"),
    BAD_REQUEST(400100, "请求参数错误"),

    // 用户模块
    USER_NOT_FOUND(400200, "用户不存在"),

    // 关系模块
    ALREADY_FOLLOWED(400300, "已关注该用户"),
    NOT_FOLLOWED(400301, "未关注该用户"),
    CANNOT_FOLLOW_SELF(400302, "不能关注自己"),

    // Feed模块
    FEED_NOT_FOUND(400400, "动态不存在"),
    FEED_DELETED(400401, "动态已删除"),
    NO_PERMISSION(400402, "无权限查看该动态"),

    // 收件箱
    INBOX_EMPTY(400500, "暂无动态"),
    INBOX_TOO_LARGE(400501, "收件箱已满"),

    ALREADY_LIKED(400600, "已经点过赞了"),
    NOT_LIKED(400601, "尚未点赞"),

    COMMENT_NOT_FOUND(400700, "评论不存在"),

    // MQ
    MQ_SEND_FAILED(500600, "消息发送失败");

    private final int code;
    private final String msg;

    CodeMsg(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}