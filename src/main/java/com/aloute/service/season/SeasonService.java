package com.aloute.service.season;

import com.aloute.util.post.Hashtags;
import com.aloute.service.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class SeasonService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;

    public record Season(UUID id, String name, String description, String hashtag, String status,
                         Instant start, Instant end, Instant evaluatedAt, String imageUrl) {
        public String startDate() { return start.atZone(ZONE).format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")); }
        public String endDate() { return end.atZone(ZONE).format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")); }
        public String startInput() { return start.atZone(ZONE).toLocalDateTime().toString(); }
        public String endInput() { return end.atZone(ZONE).toLocalDateTime().toString(); }
        public String statusLabel() { return switch (status) { case "DRAFT" -> "Bản nháp"; case "UPCOMING" -> "Sắp diễn ra"; case "ACTIVE" -> "Đang diễn ra"; case "EVALUATING" -> "Đang rà soát"; default -> "Đã công bố"; }; }
    }
    public record Standing(int rank, UUID authorId, String name, String username, long score) {}
    public record BannedParticipant(UUID userId, String name, String username, String reason) {}
    public record EntryView(UUID id, UUID authorId, String name, String content, long score, boolean excluded, String reason) {}

    public SeasonService(JdbcTemplate jdbc, Clock clock, AuditService audit) { this.jdbc = jdbc; this.clock = clock; this.audit = audit; }

    private Season map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp evaluated = rs.getTimestamp("evaluated_at");
        return new Season(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getString("required_hashtag"), rs.getString("status"), rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(), evaluated == null ? null : evaluated.toInstant(), rs.getString("image_url"));
    }

    @Transactional(readOnly = true)
    public Season get(UUID id) {
        return jdbc.query("select * from seasons where id = ?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mùa giải."));
    }

    @Transactional(readOnly = true)
    public List<Season> list() {
        return jdbc.query("select * from seasons order by start_time desc, id", this::map);
    }

    @Transactional(readOnly = true)
    public Season featured() {
        return jdbc.query("select * from seasons where status in ('ACTIVE','UPCOMING','EVALUATING') order by case status when 'ACTIVE' then 0 when 'UPCOMING' then 1 else 2 end,start_time limit 1", this::map).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Standing> standings(UUID id) {
        Season season = get(id);
        List<SeasonScoring.Ranking> ranks;
        if (season.status().equals("SETTLED")) {
            ranks = jdbc.query("select rank,author_id,score,first_post_at from season_leaderboard_snapshots where season_id=? order by rank",
                    (r, n) -> new SeasonScoring.Ranking(r.getInt(1), r.getObject(2, UUID.class), r.getLong(3), r.getTimestamp(4).toInstant()), id);
        } else ranks = SeasonScoring.ranking(scores(id));
        return ranks.stream().map(rank -> jdbc.queryForObject("select u.username,p.display_name from users u join profiles p on p.user_id=u.id where u.id=?",
                (r, n) -> new Standing(rank.rank(), rank.authorId(), r.getString(2), r.getString(1), rank.score()), rank.authorId())).toList();
    }

    @Transactional(readOnly = true)
    public List<EntryView> entries(UUID id, boolean includeExcluded) {
        var scoresById = new java.util.HashMap<UUID, Long>();
        scores(id).forEach(s -> scoresById.put(s.postId(), s.score()));
        return jdbc.query("select sp.post_id,sp.author_id,p.content,pr.display_name,sp.excluded_at,sp.exclusion_reason from season_posts sp join posts p on p.id=sp.post_id join profiles pr on pr.user_id=sp.author_id where sp.season_id=? order by sp.published_at,sp.post_id",
                (r,n) -> new EntryView(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(4), r.getString(3), scoresById.getOrDefault(r.getObject(1, UUID.class), 0L), r.getTimestamp(5) != null, r.getString(6)), id)
                .stream().filter(e -> scoresById.containsKey(e.id()) || (includeExcluded && e.excluded())).toList();
    }

    @Transactional
    public UUID create(UUID actor, String name, String description, String hashtag, LocalDate start, LocalDate end) {
        return create(actor, name, description, hashtag, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    @Transactional
    public UUID create(UUID actor, String name, String description, String hashtag, LocalDateTime start, LocalDateTime end) {
        String title = name == null ? "" : name.strip();
        String body = description == null ? "" : description.strip();
        String raw = hashtag == null ? "" : hashtag.strip();
        String token = raw.startsWith("#") ? raw : "#" + raw;
        List<String> tags = Hashtags.extract(token);
        if (title.isEmpty() || title.length() > 100 || body.length() > 4000 || tags.size() != 1
                || !token.matches("#[\\p{L}\\p{N}_]{1,50}") || start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Vui lòng kiểm tra tên, hashtag và thời gian mùa giải.");
        }
        Instant begins = start.atZone(ZONE).toInstant();
        Instant ends = end.atZone(ZONE).toInstant();
        if (start.toLocalDate().isBefore(clock.instant().atZone(ZONE).toLocalDate())) throw new IllegalArgumentException("Ngày bắt đầu không được ở trong quá khứ.");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into seasons(id,name,description,required_hashtag,start_time,end_time,created_by) values (?,?,?,?,?,?,?)",
                id, title, body, tags.get(0), Timestamp.from(begins), Timestamp.from(ends), actor);
        return id;
    }

    @Transactional
    public void updateDraft(UUID id, String name, String description, String hashtag, LocalDate start, LocalDate end) {
        updateDraft(id, name, description, hashtag, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    @Transactional
    public void updateDraft(UUID id, String name, String description, String hashtag, LocalDateTime start, LocalDateTime end) {
        Season season = lock(id);
        if (!season.status().equals("DRAFT")) throw new IllegalArgumentException("Chỉ có thể sửa cấu hình bản nháp.");
        String title = name == null ? "" : name.strip();
        String body = description == null ? "" : description.strip();
        String raw = hashtag == null ? "" : hashtag.strip();
        String token = raw.startsWith("#") ? raw : "#" + raw;
        var tags = Hashtags.extract(token);
        if (title.isEmpty() || title.length() > 100 || body.length() > 4000 || tags.size() != 1
                || !token.matches("#[\\p{L}\\p{N}_]{1,50}") || start == null || end == null || !end.isAfter(start)
                || start.toLocalDate().isBefore(clock.instant().atZone(ZONE).toLocalDate())) {
            throw new IllegalArgumentException("Vui lòng kiểm tra tên, hashtag và thời gian mùa giải.");
        }
        jdbc.update("update seasons set name=?,description=?,required_hashtag=?,start_time=?,end_time=? where id=?",
                title, body, tags.get(0), Timestamp.from(start.atZone(ZONE).toInstant()),
                Timestamp.from(end.atZone(ZONE).toInstant()), id);
    }

    @Transactional
    public void updateActive(UUID id, UUID actor, String name, String description, LocalDateTime end) {
        jdbc.execute("select pg_advisory_xact_lock(20261030)");
        Season season = lock(id);
        String title = name == null ? "" : name.strip();
        String body = description == null ? "" : description.strip();
        if (!season.status().equals("ACTIVE") || !season.end().isAfter(clock.instant()))
            throw new IllegalArgumentException("Chỉ sửa được mùa giải đang diễn ra.");
        if (title.isEmpty() || title.length() > 100 || body.length() > 4000 || end == null)
            throw new IllegalArgumentException("Vui lòng kiểm tra tên, mô tả và thời gian kết thúc.");
        Instant ends = end.atZone(ZONE).toInstant();
        if (!ends.isAfter(clock.instant())) throw new IllegalArgumentException("Thời gian kết thúc phải ở trong tương lai.");
        Boolean overlap = jdbc.queryForObject("select exists(select 1 from seasons where id<>? and status in ('UPCOMING','ACTIVE','EVALUATING') and start_time < ? and end_time + interval '24 hours' > ?)", Boolean.class, id, Timestamp.from(ends.plus(Duration.ofHours(24))), Timestamp.from(season.start()));
        if (Boolean.TRUE.equals(overlap)) throw new IllegalArgumentException("Thời gian mới trùng với mùa giải khác hoặc thời gian rà soát.");
        jdbc.update("update seasons set name=?,description=?,end_time=? where id=?", title, body, Timestamp.from(ends), id);
        audit.log(actor, "SEASON_UPDATED", "SEASON", id, "Thay đổi cấu hình mùa đang diễn ra: " + title);
    }

    @Transactional(readOnly = true)
    public List<BannedParticipant> bannedParticipants(UUID id) {
        return jdbc.query("select b.user_id,p.display_name,u.username,b.reason from season_bans b join users u on u.id=b.user_id join profiles p on p.user_id=u.id where b.season_id=? order by b.created_at desc", (r,n) -> new BannedParticipant(r.getObject(1, UUID.class),r.getString(2),r.getString(3),r.getString(4)), id);
    }

    @Transactional
    public void banParticipant(UUID id, UUID userId, UUID actor, String reason) {
        Season season = lock(id);
        String clean = reason == null ? "" : reason.strip();
        if (!season.status().equals("ACTIVE") && !season.status().equals("EVALUATING")) throw new IllegalArgumentException("Chỉ có thể loại người tham gia khi mùa đang diễn ra hoặc rà soát.");
        if (clean.isEmpty() || clean.length() > 1000) throw new IllegalArgumentException("Vui lòng nhập lý do loại người tham gia.");
        Boolean participant = jdbc.queryForObject("select exists(select 1 from season_posts where season_id=? and author_id=?)", Boolean.class, id, userId);
        if (!Boolean.TRUE.equals(participant)) throw new IllegalArgumentException("Người dùng chưa tham gia mùa giải này.");
        int inserted = jdbc.update("insert into season_bans(season_id,user_id,reason,created_by,created_at) values (?,?,?,?,?) on conflict do nothing", id, userId, clean, actor, Timestamp.from(clock.instant()));
        if (inserted == 0) throw new IllegalArgumentException("Người này đã bị loại khỏi mùa giải.");
        jdbc.update("insert into notifications(id,recipient_id,actor_id,type,reference_id,custom_text,detail,created_at) values (?,?,?,'SEASON_BANNED',?,?,?,?)",
                UUID.randomUUID(), userId, actor, id, "Bạn đã bị loại khỏi mùa giải " + season.name() + ".", "Lý do: " + clean, Timestamp.from(clock.instant()));
        audit.log(actor, "SEASON_PARTICIPANT_BANNED", "USER", userId, "Mùa " + season.name() + ": " + clean);
    }

    @Transactional
    public void unbanParticipant(UUID id, UUID userId, UUID actor) {
        Season season = lock(id);
        if (!season.status().equals("ACTIVE") && !season.status().equals("EVALUATING")) throw new IllegalArgumentException("Chỉ có thể khôi phục khi mùa đang diễn ra hoặc rà soát.");
        if (jdbc.update("delete from season_bans where season_id=? and user_id=?", id, userId) == 0) throw new IllegalArgumentException("Người này không bị loại khỏi mùa giải.");
        audit.log(actor, "SEASON_PARTICIPANT_RESTORED", "USER", userId, "Mùa " + season.name());
    }

    @Transactional
    public void setImage(UUID id, String url) {
        lock(id);
        jdbc.update("update seasons set image_url=? where id=?", url, id);
    }

    @Transactional
    public void publish(UUID id) {
        jdbc.execute("select pg_advisory_xact_lock(20261030)");
        Season season = lock(id);
        if (!season.status().equals("DRAFT")) return;
        if (!season.status().equals("DRAFT") || !season.end().isAfter(clock.instant()))
            throw new IllegalArgumentException("Chỉ công bố bản nháp còn trong thời gian diễn ra.");
        Boolean overlap = jdbc.queryForObject("select exists(select 1 from seasons where id<>? and status in ('UPCOMING','ACTIVE','EVALUATING') and start_time < ? and end_time + interval '24 hours' > ?)",
                Boolean.class, id, Timestamp.from(season.end().plus(Duration.ofHours(24))), Timestamp.from(season.start()));
        if (Boolean.TRUE.equals(overlap)) throw new IllegalArgumentException("Mùa giải trùng với thời gian diễn ra hoặc rà soát của mùa khác.");
        if (!season.start().isAfter(clock.instant()) && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from seasons where status in ('ACTIVE','EVALUATING'))", Boolean.class)))
            throw new IllegalArgumentException("Cần hoàn tất mùa đang diễn ra hoặc rà soát trước khi bắt đầu mùa mới.");
        Instant published = clock.instant();
        boolean startsNow = !season.start().isAfter(published);
        jdbc.update("update seasons set status=?, published_at=?,start_time=? where id=?",
                startsNow ? "ACTIVE" : "UPCOMING", Timestamp.from(published),
                Timestamp.from(startsNow ? published : season.start()), id);
    }

    private Season lock(UUID id) {
        return jdbc.query("select * from seasons where id=? for update", this::map, id).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mùa giải."));
    }

    @Transactional
    public void delete(UUID id, UUID actor) {
        // Match the lock order used by publication and the scheduler.
        jdbc.execute("select pg_advisory_xact_lock(20261030)");
        Season season = lock(id);
        jdbc.update("delete from notifications where reference_id=? and type in ('SEASON_JOIN','SEASON_RESULT','SEASON_BANNED')", id);
        jdbc.update("delete from season_leaderboard_snapshots where season_id=?", id);
        jdbc.update("delete from season_events where season_id=?", id);
        jdbc.update("delete from season_posts where season_id=?", id);
        jdbc.update("delete from season_bans where season_id=?", id);
        jdbc.update("delete from seasons where id=?", id);
        audit.log(actor, "SEASON_DELETED", "SEASON", id, season.name() + " · " + season.status());
    }

    @Transactional(readOnly = true)
    public List<SeasonScoring.PostScore> scores(UUID id) {
        Season season = get(id);
        List<SeasonScoring.Entry> entries = jdbc.query("select sp.post_id, sp.author_id, sp.published_at, sp.excluded_at from season_posts sp where sp.season_id=? and not exists (select 1 from season_bans b where b.season_id=sp.season_id and b.user_id=sp.author_id)",
                (r, n) -> new SeasonScoring.Entry(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getTimestamp(3).toInstant(), r.getTimestamp(4) != null), id);
        List<SeasonScoring.Event> events = jdbc.query("select id,post_id,actor_id,kind,source_id,present,occurred_at from season_events where season_id=?",
                (r, n) -> new SeasonScoring.Event(r.getLong(1), r.getObject(2, UUID.class), r.getObject(3, UUID.class),
                        SeasonScoring.Kind.valueOf(r.getString(4)), r.getObject(5, UUID.class), r.getBoolean(6), r.getTimestamp(7).toInstant()), id);
        Instant cutoff = clock.instant().isBefore(season.end()) ? clock.instant() : season.end();
        return SeasonScoring.posts(entries, events, season.start(), cutoff);
    }

    @Transactional
    public void exclude(UUID seasonId, UUID postId, UUID actor, String reason) {
        Season season = lock(seasonId);
        String clean = reason == null ? "" : reason.strip();
        if (!season.status().equals("EVALUATING") || clean.isEmpty() || clean.length() > 1000)
            throw new IllegalArgumentException("Chỉ loại bài trong giai đoạn rà soát và phải ghi lý do.");
        if (scores(seasonId).stream().noneMatch(post -> post.postId().equals(postId)))
            throw new IllegalArgumentException("Bài không thuộc danh sách dự thi hợp lệ.");
        int count = jdbc.update("update season_posts set excluded_at=?,excluded_by=?,exclusion_reason=? where season_id=? and post_id=? and excluded_at is null",
                Timestamp.from(clock.instant()), actor, clean, seasonId, postId);
        if (count == 0) throw new IllegalArgumentException("Không tìm thấy bài hoặc bài đã bị loại.");
    }

    @Transactional
    public void settle(UUID id, UUID actor) {
        Season season = lock(id);
        if (season.status().equals("SETTLED")) return;
        if (!season.status().equals("EVALUATING") || season.evaluatedAt() == null
                || clock.instant().isBefore(season.evaluatedAt().plus(Duration.ofHours(24))))
            throw new IllegalArgumentException("Chỉ duyệt sau ít nhất 24 giờ rà soát.");
        for (SeasonScoring.Ranking row : SeasonScoring.ranking(scores(id))) {
            jdbc.update("insert into season_leaderboard_snapshots(season_id,author_id,rank,score,first_post_at) values (?,?,?,?,?)",
                    id, row.authorId(), row.rank(), row.score(), Timestamp.from(row.firstPostAt()));
            jdbc.update("insert into notifications(id,recipient_id,actor_id,type,reference_id,custom_text,created_at) values (?,?,?,'SEASON_RESULT',?,?,?)",
                    UUID.randomUUID(), row.authorId(), actor, id, "Kết quả Mùa giải " + season.name() + " đã được công bố!", Timestamp.from(clock.instant()));
        }
        jdbc.update("update seasons set status='SETTLED',settled_at=?,settled_by=? where id=?", Timestamp.from(clock.instant()), actor, id);
    }

    @Transactional
    public void advance() {
        Instant now = clock.instant();
        jdbc.execute("select pg_advisory_xact_lock(20261030)");
        List<Season> expired = jdbc.query("select * from seasons where status='ACTIVE' and end_time<=? order by id for update", this::map, Timestamp.from(now));
        for (Season season : expired) freeze(season.id(), now);
        Boolean busy = jdbc.queryForObject("select exists(select 1 from seasons where status in ('ACTIVE','EVALUATING'))", Boolean.class);
        if (!Boolean.TRUE.equals(busy)) {
            var due = jdbc.query("select * from seasons where status='UPCOMING' and start_time<=? order by start_time,id limit 1 for update", this::map, Timestamp.from(now));
            if (!due.isEmpty()) {
                Season season = due.get(0);
                if (!season.end().isAfter(now)) freeze(season.id(), now);
                else jdbc.update("update seasons set status='ACTIVE' where id=?", season.id());
            }
        }
    }

    private void freeze(UUID id, Instant now) {
        List<SeasonScoring.PostScore> frozen = scores(id);
        jdbc.update("update season_posts set eligible=false,score=0 where season_id=?", id);
        for (var post : frozen) jdbc.update("update season_posts set eligible=true,score=? where season_id=? and post_id=?", post.score(), id, post.postId());
        jdbc.update("update seasons set status='EVALUATING',evaluated_at=? where id=?", Timestamp.from(now), id);
    }
}
