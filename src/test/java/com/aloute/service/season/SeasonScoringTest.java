package com.aloute.service.season;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static com.aloute.service.season.SeasonScoring.*;

class SeasonScoringTest {
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant end = start.plusSeconds(100);
    private final UUID post = UUID.randomUUID(), author = UUID.randomUUID(), other = UUID.randomUUID();
    private Event event(long id, UUID actor, Kind kind, UUID source, boolean present, long seconds) {
        return new Event(id, post, actor, kind, source, present, start.plusSeconds(seconds));
    }

    @Test void freezesAtEndAndCountsAtMostOneOfEachTypePerPerson() {
        UUID reaction = UUID.randomUUID(), comment = UUID.randomUUID();
        var events = List.of(event(1, author, Kind.ELIGIBILITY, post, true, 1),
                event(2, other, Kind.REACTION, reaction, true, 2),
                event(3, other, Kind.REACTION, reaction, false, 100),
                event(4, other, Kind.COMMENT, comment, true, 3),
                event(5, other, Kind.COMMENT, UUID.randomUUID(), true, 4),
                event(6, other, Kind.SHARE, UUID.randomUUID(), true, 5),
                event(7, author, Kind.SHARE, UUID.randomUUID(), true, 6));
        var scores = posts(List.of(new Entry(post, author, start.plusSeconds(1), false)), events, start, end);
        assertThat(scores).singleElement().extracting(PostScore::score).isEqualTo(6L);
    }

    @Test void excludesRemovedPostsAndReactionsAndOrdersTiesByFirstPost() {
        UUID reaction = UUID.randomUUID();
        var events = List.of(event(1, author, Kind.ELIGIBILITY, post, true, 1),
                event(2, other, Kind.REACTION, reaction, true, 2),
                event(3, other, Kind.REACTION, reaction, false, 3));
        var scores = posts(List.of(new Entry(post, author, start.plusSeconds(1), false)), events, start, end);
        assertThat(scores).singleElement().extracting(PostScore::score).isEqualTo(0L);
        assertThat(posts(List.of(new Entry(post, author, start.plusSeconds(1), true)), events, start, end)).isEmpty();
        UUID nextAuthor = UUID.randomUUID();
        var rankings = ranking(List.of(scores.get(0), new PostScore(UUID.randomUUID(), nextAuthor, start.plusSeconds(2), 0)));
        assertThat(rankings).extracting(Ranking::authorId).containsExactly(author, nextAuthor);
    }

    @Test void lastEligibilityBeforeCutoffDeterminesWhetherPostScores() {
        var entry = new Entry(post, author, start.plusSeconds(1), false);
        var events = List.of(event(1, author, Kind.ELIGIBILITY, post, true, 1),
                event(2, author, Kind.ELIGIBILITY, post, false, 50),
                event(3, author, Kind.ELIGIBILITY, post, true, 100));
        assertThat(posts(List.of(entry), events, start, end)).isEmpty();
        assertThat(posts(List.of(entry), events, start, end.plusSeconds(1))).hasSize(1);
    }

    @Test void rankingUsesOnlyHighestScoringPostPerAuthor() {
        UUID second = UUID.randomUUID(), rival = UUID.randomUUID();
        var ranking = ranking(List.of(
                new PostScore(post, author, start.plusSeconds(1), 3),
                new PostScore(second, author, start.plusSeconds(2), 7),
                new PostScore(UUID.randomUUID(), rival, start.plusSeconds(3), 8)));
        assertThat(ranking).extracting(Ranking::score).containsExactly(8L, 7L);
        assertThat(ranking).extracting(Ranking::authorId).containsExactly(rival, author);
        assertThat(ranking.get(1).firstPostAt()).isEqualTo(start.plusSeconds(1));
    }
}
