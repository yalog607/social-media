package com.aloute.service.season;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reconstructs a season's score at a cutoff from its ordered, append-only event history. */
public final class SeasonScoring {
    private SeasonScoring() {}

    public enum Kind { ELIGIBILITY, REACTION, COMMENT, SHARE }
    public record Entry(UUID postId, UUID authorId, Instant publishedAt, boolean excluded) {}
    public record Event(long id, UUID postId, UUID actorId, Kind kind, UUID sourceId,
                        boolean present, Instant occurredAt) {}
    public record PostScore(UUID postId, UUID authorId, Instant publishedAt, long score) {}
    public record Ranking(int rank, UUID authorId, long score, Instant firstPostAt) {}
    private record EventKey(UUID postId, Kind kind, UUID sourceId) {}
    private record Contribution(UUID postId, UUID actorId, Kind kind) {}

    /** The end instant is exclusive; changes at or after it do not alter the frozen result. */
    public static List<PostScore> posts(List<Entry> entries, List<Event> events, Instant start, Instant end) {
        Map<UUID, Entry> byPost = new HashMap<>();
        for (Entry entry : entries) {
            if (!entry.excluded() && !entry.publishedAt().isBefore(start) && entry.publishedAt().isBefore(end)) {
                byPost.put(entry.postId(), entry);
            }
        }
        Map<EventKey, Event> latest = new HashMap<>();
        Comparator<Event> order = Comparator.comparing(Event::occurredAt).thenComparingLong(Event::id);
        for (Event event : events) {
            if (event.occurredAt().isBefore(start) || !event.occurredAt().isBefore(end) || !byPost.containsKey(event.postId())) continue;
            EventKey key = new EventKey(event.postId(), event.kind(), event.sourceId());
            latest.merge(key, event, (left, right) -> order.compare(left, right) >= 0 ? left : right);
        }
        Set<UUID> eligible = new HashSet<>();
        Set<Contribution> contributions = new HashSet<>();
        for (Event event : latest.values()) {
            if (!event.present()) continue;
            if (event.kind() == Kind.ELIGIBILITY) eligible.add(event.postId());
            else if (event.actorId() != null && !event.actorId().equals(byPost.get(event.postId()).authorId())) {
                contributions.add(new Contribution(event.postId(), event.actorId(), event.kind()));
            }
        }
        Map<UUID, Long> scores = new HashMap<>();
        for (Contribution contribution : contributions) {
            int value = switch (contribution.kind()) {
                case REACTION -> 1;
                case COMMENT -> 2;
                case SHARE -> 3;
                default -> 0;
            };
            scores.merge(contribution.postId(), (long) value, Long::sum);
        }
        return byPost.values().stream().filter(entry -> eligible.contains(entry.postId()))
                .map(entry -> new PostScore(entry.postId(), entry.authorId(), entry.publishedAt(), scores.getOrDefault(entry.postId(), 0L)))
                .sorted(Comparator.comparing(PostScore::publishedAt).thenComparing(PostScore::postId)).toList();
    }

    public static List<Ranking> ranking(List<PostScore> posts) {
        record Aggregate(UUID authorId, long score, Instant firstPostAt) {}
        Map<UUID, Aggregate> totals = new HashMap<>();
        for (PostScore post : posts) {
            totals.merge(post.authorId(), new Aggregate(post.authorId(), post.score(), post.publishedAt()),
                    (a, b) -> new Aggregate(a.authorId(), Math.max(a.score(), b.score()),
                            a.firstPostAt().isBefore(b.firstPostAt()) ? a.firstPostAt() : b.firstPostAt()));
        }
        List<Aggregate> sorted = totals.values().stream()
                .sorted(Comparator.comparingLong(Aggregate::score).reversed()
                        .thenComparing(Aggregate::firstPostAt).thenComparing(Aggregate::authorId)).toList();
        return java.util.stream.IntStream.range(0, sorted.size())
                .mapToObj(i -> new Ranking(i + 1, sorted.get(i).authorId(), sorted.get(i).score(), sorted.get(i).firstPostAt())).toList();
    }
}
