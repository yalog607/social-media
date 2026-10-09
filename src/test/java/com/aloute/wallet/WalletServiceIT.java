package com.aloute.wallet;

import com.aloute.exception.wallet.WalletException;
import com.aloute.model.wallet.WalletTxType;
import com.aloute.service.wallet.WalletService;

import com.aloute.model.post.Post;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.service.post.PostService;
import com.aloute.dto.post.PostView;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.service.social.BlockService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.MutableClock;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ví Xu: quà chào mừng, nhận hằng ngày, donate, mở khóa bài trả phí, và an toàn khi chạy song song. */
class WalletServiceIT extends IntegrationTest {

    @Autowired WalletService wallet;
    @Autowired PostService posts;
    @Autowired PostViewAssembler assembler;
    @Autowired BlockService blocks;
    @Autowired MutableClock clock;

    private Post paidPost(User creator, int price) {
        return posts.create(creator.getId(), "nội dung bí mật", Visibility.PUBLIC, List.of(), null, List.of(), price);
    }

    @Test
    void newWalletGetsTheSignupBonusOnlyOnce() {
        User user = createUser();

        wallet.ensureWallet(user.getId());
        wallet.ensureWallet(user.getId());

        assertThat(wallet.balance(user.getId())).isEqualTo(WalletService.SIGNUP_BONUS);
        assertThat(wallet.view(user.getId()).recent()).singleElement()
                .satisfies(e -> assertThat(e.type()).isEqualTo(WalletTxType.SIGNUP_BONUS));
    }

    @Test
    void dailyBonusCanBeClaimedOncePerDay() {
        User user = createUser();
        wallet.claimDaily(user.getId());

        assertThatThrownBy(() -> wallet.claimDaily(user.getId())).isInstanceOf(WalletException.class);
        assertThat(wallet.view(user.getId()).canClaimDaily()).isFalse();

        clock.advance(Duration.ofDays(1));
        assertThat(wallet.view(user.getId()).canClaimDaily()).isTrue();
        assertThat(wallet.claimDaily(user.getId()))
                .isEqualTo(WalletService.SIGNUP_BONUS + 2 * WalletService.DAILY_BONUS);
    }

    @Test
    void donationMovesCoinsAndNotifiesTheCreator() {
        User fan = createUser();
        User creator = createUser(Role.CREATOR);

        wallet.donate(fan.getId(), creator.getId(), 30);

        assertThat(wallet.balance(fan.getId())).isEqualTo(WalletService.SIGNUP_BONUS - 30);
        assertThat(wallet.balance(creator.getId())).isEqualTo(WalletService.SIGNUP_BONUS + 30);
    }

    @Test
    void donationRulesAreEnforced() {
        User fan = createUser();
        User creator = createUser(Role.CREATOR);
        User plain = createUser();
        User blockedCreator = createUser(Role.CREATOR);
        blocks.block(blockedCreator.getId(), fan.getId());

        assertThatThrownBy(() -> wallet.donate(fan.getId(), creator.getId(), 0)).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> wallet.donate(fan.getId(), creator.getId(), WalletService.MAX_DONATION + 1))
                .isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> wallet.donate(creator.getId(), creator.getId(), 5)).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> wallet.donate(fan.getId(), plain.getId(), 5)).hasMessageContaining("Creator");
        assertThatThrownBy(() -> wallet.donate(fan.getId(), blockedCreator.getId(), 5)).isInstanceOf(WalletException.class);
        assertThatThrownBy(() -> wallet.donate(fan.getId(), creator.getId(), WalletService.SIGNUP_BONUS + 1))
                .hasMessageContaining("không đủ Xu");
        assertThat(wallet.balance(fan.getId())).isEqualTo(WalletService.SIGNUP_BONUS);
    }

    @Test
    void lockedPostHidesContentUntilUnlockedAndPaysTheAuthor() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        Post post = paidPost(creator, 40);

        PostView before = assembler.assemble(List.of(post), fan.getId()).get(0);
        assertThat(before.locked()).isTrue();
        assertThat(before.contentHtml()).isEmpty();
        assertThat(assembler.assemble(List.of(post), null).get(0).locked()).as("khách cũng bị khóa").isTrue();
        assertThat(assembler.assemble(List.of(post), creator.getId()).get(0).locked()).as("tác giả luôn xem được").isFalse();

        wallet.unlock(fan.getId(), post.getId());
        long afterFirst = wallet.balance(fan.getId());
        wallet.unlock(fan.getId(), post.getId());

        PostView after = assembler.assemble(List.of(post), fan.getId()).get(0);
        assertThat(after.locked()).isFalse();
        assertThat(after.contentHtml()).contains("nội dung bí mật");
        assertThat(afterFirst).isEqualTo(WalletService.SIGNUP_BONUS - 40);
        assertThat(wallet.balance(fan.getId())).as("mở lại không bị trừ thêm").isEqualTo(afterFirst);
        assertThat(wallet.balance(creator.getId())).isEqualTo(WalletService.SIGNUP_BONUS + 40);
    }

    @Test
    void cannotUnlockWithoutEnoughCoinsAndNothingIsCharged() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        Post post = paidPost(creator, WalletService.MAX_UNLOCK_PRICE);

        assertThatThrownBy(() -> wallet.unlock(fan.getId(), post.getId())).isInstanceOf(WalletException.class);

        assertThat(wallet.balance(fan.getId())).isEqualTo(WalletService.SIGNUP_BONUS);
        assertThat(assembler.assemble(List.of(post), fan.getId()).get(0).locked()).isTrue();
    }

    @Test
    void onlyCreatorsCanPriceAPostAndThePriceIsBounded() {
        User plain = createUser();
        User creator = createUser(Role.CREATOR);

        assertThatThrownBy(() -> paidPost(plain, 10)).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> paidPost(creator, 0)).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> paidPost(creator, WalletService.MAX_UNLOCK_PRICE + 1)).isInstanceOf(InvalidPostException.class);
    }

    @Test
    void parallelDonationsNeverOverdrawTheWallet() throws Exception {
        User fan = createUser();
        User creator = createUser(Role.CREATOR);
        wallet.ensureWallet(fan.getId());
        int attempts = 8;
        long each = 30; // 8 x 30 = 240 > 100: nhiều nhất 3 lần thành công

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            wallet.donate(fan.getId(), creator.getId(), each);
                            return true;
                        } catch (WalletException e) {
                            return false;
                        }
                    }).toList();
            long succeeded = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                succeeded += f.get() ? 1 : 0;
            }
            assertThat(succeeded).isEqualTo(WalletService.SIGNUP_BONUS / each);
            assertThat(wallet.balance(fan.getId())).isEqualTo(WalletService.SIGNUP_BONUS - succeeded * each);
            assertThat(wallet.balance(creator.getId())).isEqualTo(WalletService.SIGNUP_BONUS + succeeded * each);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void oppositeDonationsInParallelDoNotDeadlock() throws Exception {
        User a = createUser(Role.CREATOR);
        User b = createUser(Role.CREATOR);
        wallet.ensureWallet(a.getId());
        wallet.ensureWallet(b.getId());

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, 20)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        if (i % 2 == 0) {
                            wallet.donate(a.getId(), b.getId(), 1);
                        } else {
                            wallet.donate(b.getId(), a.getId(), 1);
                        }
                        return null;
                    }).toList();
            for (Future<Void> f : pool.invokeAll(tasks)) {
                f.get();
            }
            assertThat(wallet.balance(a.getId())).isEqualTo(WalletService.SIGNUP_BONUS);
            assertThat(wallet.balance(b.getId())).isEqualTo(WalletService.SIGNUP_BONUS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void httpEndpointsDonateUnlockAndHideLockedHtml() throws Exception {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        Post post = paidPost(creator, 10);

        mvc.perform(get("/posts/" + post.getId()).with(asUser(fan)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("nội dung bí mật"))))
                .andExpect(content().string(containsString("data-unlock-post")));
        mvc.perform(post("/api/posts/" + post.getId() + "/unlock").with(csrf()).with(asUser(fan)))
                .andExpect(status().isOk());
        mvc.perform(get("/posts/" + post.getId()).with(asUser(fan)))
                .andExpect(content().string(containsString("nội dung bí mật")));
        mvc.perform(post("/api/donations").param("toUserId", creator.getId().toString()).param("amount", "5")
                        .with(csrf()).with(asUser(fan)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/donations").param("toUserId", creator.getId().toString()).param("amount", "999999")
                        .with(csrf()).with(asUser(fan)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/donations").param("toUserId", creator.getId().toString()).param("amount", "5").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/wallet").with(asUser(fan))).andExpect(status().isOk());
    }
}
