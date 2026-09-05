package com.ygq.feedly.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FanoutMessage {

    private Long feedId;
    private Long userId;
    private Long createTime;
    private Integer visibleRange;
}