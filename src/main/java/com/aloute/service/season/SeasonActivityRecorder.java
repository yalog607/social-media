package com.aloute.service.season;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Records changes in the same transaction as the underlying post or interaction. */
@Service
public class SeasonActivityRecorder {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private record Window(UUID id, String tag, Instant start, Instant end, UUID creator, String name) {}

    public SeasonActivityRecorder(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    private List<Window> lockCurrentWindows() {
        Instant now = clock.instant();
        return jdbc.query("select id,required_hashtag,start_time,end_time,created_by,name from seasons s where status in ('UPCOMING','ACTIVE') and start_time<=? and end_time>? and not exists (select 1 from seasons other where other.id<>s.id and other.status in ('ACTIVE','EVALUATING')) order by id for update",
                (r, n) -> new Window(r.getObject(1, UUID.class), r.getString(2), r.getTimestamp(3).toInstant(), r.getTimestamp(4).toInstant(), r.getObject(5, UUID.class), r.getString(6)),
                Timestamp.from(now), Timestamp.from(now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void eligibility(UUID postId, UUID authorId, Instant publishedAt, boolean publicAndVisible, java.util.Collection<String> hashtags) {
        if (publishedAt == null) return;
        for (Window window : lockCurrentWindows()) {
            Instant now = clock.instant();
            if (!now.isBefore(window.end())) continue;
            var existing = jdbc.query("select published_at from season_posts where season_id=? and post_id=?",
                    (r, n) -> r.getTimestamp(1).toInstant(), window.id(), postId);
            Instant effectivePublication = existing.isEmpty() ? publishedAt : existing.get(0);
            if (effectivePublication.isBefore(window.start()) || !effectivePublication.isBefore(window.end())) continue;
            jdbc.update("insert into season_posts(season_id,post_id,author_id,published_at) values (?,?,?,?) on conflict do nothing",
                    window.id(), postId, authorId, Timestamp.from(effectivePublication));
            boolean eligible = publicAndVisible && hashtags.contains(window.tag());
            append(window.id(), postId, authorId, SeasonScoring.Kind.ELIGIBILITY, postId, eligible, now);
            if (eligible && jdbc.update("update season_posts set participation_notified=true where season_id=? and post_id=? and participation_notified=false", window.id(), postId) == 1) {
                jdbc.update("insert into notifications(id,recipient_id,actor_id,type,reference_id,custom_text,created_at) values (?,?,?,'SEASON_JOIN',?,?,?)",
                        UUID.randomUUID(), authorId, window.creator(), window.id(), "Bài viết của bạn đã tham gia Mùa giải " + window.name() + "!", Timestamp.from(now));
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void interaction(UUID postId, UUID actorId, SeasonScoring.Kind kind, UUID sourceId, boolean present) {
        if (kind == SeasonScoring.Kind.ELIGIBILITY) throw new IllegalArgumentException("Loại sự kiện không hợp lệ.");
        for (Window window : lockCurrentWindows()) {
            Instant now = clock.instant();
            if (!now.isBefore(window.end())) continue;
            Boolean registered = jdbc.queryForObject("select exists(select 1 from season_posts where season_id=? and post_id=?)", Boolean.class, window.id(), postId);
            if (Boolean.TRUE.equals(registered)) append(window.id(), postId, actorId, kind, sourceId, present, now);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accountDeleted(UUID userId) {
        for (Window window : lockCurrentWindows()) {
            Instant now = clock.instant();
            if (!now.isBefore(window.end())) continue;
            Timestamp at = Timestamp.from(now);
            jdbc.update("insert into season_events(season_id,post_id,actor_id,kind,source_id,present,occurred_at) "
                            + "select sp.season_id,sp.post_id,?,'ELIGIBILITY',sp.post_id,false,? from season_posts sp "
                            + "join posts p on p.id=sp.post_id where sp.season_id=? and p.author_id=? and p.deleted_at is null",
                    userId, at, window.id(), userId);
            jdbc.update("insert into season_events(season_id,post_id,actor_id,kind,source_id,present,occurred_at) "
                            + "select sp.season_id,sp.post_id,?,'COMMENT',c.id,false,? from season_posts sp "
                            + "join comments c on c.post_id=sp.post_id where sp.season_id=? and c.author_id=? and c.deleted_at is null",
                    userId, at, window.id(), userId);
            jdbc.update("insert into season_events(season_id,post_id,actor_id,kind,source_id,present,occurred_at) "
                            + "select sp.season_id,sp.post_id,?,'SHARE',p.id,false,? from season_posts sp "
                            + "join posts p on p.shared_post_id=sp.post_id where sp.season_id=? and p.author_id=? and p.deleted_at is null",
                    userId, at, window.id(), userId);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void authorVisibility(UUID userId, boolean active) {
        for (Window window : lockCurrentWindows()) {
            Instant now = clock.instant();
            if (!now.isBefore(window.end())) continue;
            jdbc.update("insert into season_events(season_id,post_id,actor_id,kind,source_id,present,occurred_at) "
                            + "select sp.season_id,sp.post_id,?,'ELIGIBILITY',sp.post_id,"
                            + "(? and p.deleted_at is null and p.scheduled_at is null and p.visibility='PUBLIC' "
                            + "and exists(select 1 from post_hashtags h where h.post_id=p.id and h.tag=?)),? "
                            + "from season_posts sp join posts p on p.id=sp.post_id where sp.season_id=? and sp.author_id=?",
                    userId, active, window.tag(), Timestamp.from(now), window.id(), userId);
            jdbc.update("insert into season_events(season_id,post_id,actor_id,kind,source_id,present,occurred_at) "
                            + "select sp.season_id,sp.post_id,?,'SHARE',p.id,(? and p.deleted_at is null and p.visibility='PUBLIC'),? "
                            + "from season_posts sp join posts p on p.shared_post_id=sp.post_id "
                            + "where sp.season_id=? and p.author_id=? and exists "
                            + "(select 1 from season_events e where e.season_id=sp.season_id and e.kind='SHARE' and e.source_id=p.id)",
                    userId, active, Timestamp.from(now), window.id(), userId);
        }
    }

    private void append(UUID season, UUID post, UUID actor, SeasonScoring.Kind kind, UUID source, boolean present, Instant time) {
        jdbc.update("insert into season_events(season_id,post_id,actor_id,kind,source_id,present,occurred_at) values (?,?,?,?,?,?,?)",
                season, post, actor, kind.name(), source, present, Timestamp.from(time));
    }
}
