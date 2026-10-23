package com.aloute.service.wallet;

import com.aloute.dto.wallet.WalletView;
import com.aloute.exception.wallet.WalletException;
import com.aloute.model.wallet.WalletTxType;

import com.aloute.service.notification.NotificationService;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.service.social.BlockService;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Ví Xu ảo (không có thanh toán thật). Mọi thay đổi số dư là MỘT câu UPDATE có điều kiện {@code balance >= ?}
 * nên không bao giờ âm kể cả khi nhiều giao dịch chạy song song; hai ví trong một giao dịch được khóa theo thứ tự
 * id cố định để hai chiều donate ngược nhau không làm nhau kẹt (deadlock). Mỗi thay đổi ghi một dòng sổ cái.
 */
@Service
public class WalletService {

    public static final long SIGNUP_BONUS = 100;
    public static final long DAILY_BONUS = 20;
    public static final long MIN_DONATION = 1;
    public static final long MAX_DONATION = 1000;
    public static final int MAX_UNLOCK_PRICE = 1000;
    private static final int RECENT = 20;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final UserRepository users;
    private final PostService posts;
    private final BlockService blocks;
    private final NotificationService notifications;
    private final PaidContentAccess access;

    public WalletService(JdbcTemplate jdbc, Clock clock, UserRepository users, PostService posts,
                         BlockService blocks, NotificationService notifications, PaidContentAccess access) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.users = users;
        this.posts = posts;
        this.blocks = blocks;
        this.notifications = notifications;
        this.access = access;
    }

    /** Tạo ví nếu chưa có (kèm quà chào mừng) — dùng chung cho người dùng mới lẫn người dùng có từ trước. */
    @Transactional
    public void ensureWallet(UUID userId) {
        int created = jdbc.update("insert into wallets (user_id, balance) values (?, 0) on conflict do nothing", userId);
        if (created == 1) {
            credit(userId, SIGNUP_BONUS, WalletTxType.SIGNUP_BONUS, null, null);
        }
    }

    @Transactional
    public WalletView view(UUID userId) {
        ensureWallet(userId);
        Long balance = jdbc.queryForObject("select balance from wallets where user_id = ?", Long.class, userId);
        LocalDate claimed = jdbc.queryForObject("select daily_claimed_on from wallets where user_id = ?", LocalDate.class, userId);
        boolean canClaim = claimed == null || claimed.isBefore(today());
        List<WalletView.Entry> recent = jdbc.query("""
                select t.type, t.amount, t.created_at, p.display_name
                from wallet_transactions t left join profiles p on p.user_id = t.counterparty_id
                where t.user_id = ? order by t.created_at desc, t.id desc limit ?""",
                (rs, i) -> new WalletView.Entry(WalletTxType.valueOf(rs.getString("type")), rs.getLong("amount"),
                        rs.getString("display_name"), rs.getTimestamp("created_at").toInstant()),
                userId, RECENT);
        return new WalletView(balance == null ? 0 : balance, canClaim, recent);
    }

    /** @throws WalletException đã nhận Xu hôm nay rồi */
    @Transactional
    public long claimDaily(UUID userId) {
        ensureWallet(userId);
        int claimed = jdbc.update("""
                update wallets set daily_claimed_on = ? where user_id = ?
                and (daily_claimed_on is null or daily_claimed_on < ?)""", java.sql.Date.valueOf(today()), userId,
                java.sql.Date.valueOf(today()));
        if (claimed == 0) {
            throw new WalletException("Hôm nay bạn đã nhận Xu rồi, mai quay lại nhé.");
        }
        credit(userId, DAILY_BONUS, WalletTxType.DAILY_BONUS, null, null);
        return balance(userId);
    }

    /**
     * @throws WalletException số Xu ngoài khoảng cho phép, tự donate, người nhận không phải Creator/bị chặn,
     *                         hoặc không đủ Xu
     */
    @Transactional
    public void donate(UUID fromId, UUID toId, long amount) {
        if (amount < MIN_DONATION || amount > MAX_DONATION) {
            throw new WalletException("Mỗi lần tặng từ " + MIN_DONATION + " đến " + MAX_DONATION + " Xu.");
        }
        if (fromId.equals(toId)) {
            throw new WalletException("Bạn không thể tự tặng Xu cho mình.");
        }
        User recipient = users.findById(toId).filter(User::isActive).filter(u -> u.hasRole(Role.CREATOR))
                .orElseThrow(() -> new WalletException("Chỉ tặng Xu được cho Creator."));
        if (blocks.isBlockedEitherWay(fromId, recipient.getId())) {
            throw new WalletException("Bạn không thể tặng Xu cho người này.");
        }
        transfer(fromId, toId, amount, WalletTxType.DONATE_SENT, WalletTxType.DONATE_RECEIVED, null);
        notifications.donated(fromId, toId);
    }

    /**
     * Mở khóa một bài trả phí. Mở lại bài đã mở thì không bị trừ thêm.
     *
     * @return số dư sau giao dịch
     * @throws com.aloute.exception.post.PostNotFoundException bài không tồn tại hoặc không xem được
     * @throws WalletException                       bài không trả phí, hoặc không đủ Xu
     */
    @Transactional
    public long unlock(UUID userId, UUID postId) {
        Post post = posts.getVisible(postId, userId);
        Integer price = post.getUnlockPrice();
        if (price == null) {
            throw new WalletException("Bài này không cần mở khóa.");
        }
        UUID authorId = post.getAuthor().getId();
        if (authorId.equals(userId)) {
            throw new WalletException("Đây là bài của bạn.");
        }
        ensureWallet(userId);
        if (access.record(postId, userId, price)) {
            transfer(userId, authorId, price, WalletTxType.UNLOCK_SPENT, WalletTxType.UNLOCK_EARNED, postId);
        }
        return balance(userId);
    }

    /** Số dư hiện tại; người chưa có ví được tạo ví (kèm quà chào mừng) ngay lúc hỏi. */
    @Transactional
    public long balance(UUID userId) {
        ensureWallet(userId);
        Long balance = jdbc.queryForObject("select balance from wallets where user_id = ?", Long.class, userId);
        return balance == null ? 0 : balance;
    }

    // ---------- Nội bộ ----------

    private void transfer(UUID fromId, UUID toId, long amount, WalletTxType out, WalletTxType in, UUID postId) {
        ensureWallet(fromId);
        ensureWallet(toId);
        // Khóa cả hai ví theo thứ tự id cố định trước khi sửa, để hai giao dịch ngược chiều không chờ nhau mãi
        jdbc.query("select user_id from wallets where user_id in (?, ?) order by user_id for update",
                rs -> { }, fromId, toId);
        int debited = jdbc.update("update wallets set balance = balance - ? where user_id = ? and balance >= ?",
                amount, fromId, amount);
        if (debited == 0) {
            throw new WalletException("Bạn không đủ Xu cho giao dịch này.");
        }
        jdbc.update("update wallets set balance = balance + ? where user_id = ?", amount, toId);
        ledger(fromId, out, amount, toId, postId);
        ledger(toId, in, amount, fromId, postId);
    }

    private void credit(UUID userId, long amount, WalletTxType type, UUID counterpartyId, UUID postId) {
        jdbc.update("update wallets set balance = balance + ? where user_id = ?", amount, userId);
        ledger(userId, type, amount, counterpartyId, postId);
    }

    private void ledger(UUID userId, WalletTxType type, long amount, UUID counterpartyId, UUID postId) {
        jdbc.update("""
                insert into wallet_transactions (id, user_id, type, amount, counterparty_id, post_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), userId, type.name(), amount, counterpartyId, postId,
                Timestamp.from(clock.instant()));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
