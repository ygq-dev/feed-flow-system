package com.ygq.feedly.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sk_user")
public class UserInfo {

    private Long id;
    private String nickname;
    private String avatar;
    private LocalDateTime createTime;
}