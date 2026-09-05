package com.ygq.feedly.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineResult {
    private List<FeedVO> list;
    private Long lastFeedId;
    private boolean hasMore;

    public static TimelineResult empty() {
        return new TimelineResult(Collections.emptyList(), null, false);
    }
}