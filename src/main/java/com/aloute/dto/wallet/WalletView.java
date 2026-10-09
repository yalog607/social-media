package com.aloute.dto.wallet;

import com.aloute.model.wallet.WalletTxType;

import java.time.Instant;
import java.util.List;

/** Ví của một người: số dư, đã nhận Xu hôm nay chưa, và các giao dịch gần đây. */
public record WalletView(long balance, boolean canClaimDaily, List<Entry> recent) {

    public record Entry(WalletTxType type, long amount, String counterparty, Instant createdAt) {

        public String signedAmount() {
            return (type.credit() ? "+" : "−") + amount;
        }
    }
}
