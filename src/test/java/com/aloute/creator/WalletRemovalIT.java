package com.aloute.creator;

import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Sau V46: ví Xu, mở khóa bài trả phí và cột giá không còn trong schema. */
class WalletRemovalIT extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void walletTablesAndUnlockPriceAreGone() {
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('wallets', 'wallet_transactions', 'paid_content_unlocks')""", Integer.class);
        Integer columns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'posts' and column_name = 'unlock_price'""",
                Integer.class);
        assertThat(tables).isZero();
        assertThat(columns).isZero();
    }
}
