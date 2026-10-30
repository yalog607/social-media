package com.aloute.dto.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu một bình chọn để hiển thị. {@code votedOptionId} là lựa chọn người xem đã chọn (null nếu chưa bầu hoặc là khách).
 * Kết quả chỉ nên hiện khi {@link #showResults()}: đã bầu hoặc bình chọn đã kết thúc.
 */
public record PollView(List<Option> options, long totalVotes, UUID votedOptionId, boolean closed, Instant closesAt,
                       boolean viewerLoggedIn) {

    public record Option(UUID id, String label, long votes, int percent) {
    }

    public boolean canVote() {
        return viewerLoggedIn && !closed && votedOptionId == null;
    }

    public boolean showResults() {
        return closed || votedOptionId != null;
    }
}
