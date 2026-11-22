package com.vivanetpay.ui;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.reporting.HistoryRow;

import java.time.Instant;

public final class UiTestData {
    private UiTestData() {
    }

    public static HistoryRow row(long id, String bank, Long amount, String sender, EventStatus status) {
        return new HistoryRow(id, id, EventSource.ANDROID_NEQUI, "android-1", bank, amount, sender, "ref" + id, status,
                "motivo", Instant.parse("2025-03-01T15:30:00Z"), Instant.parse("2025-03-01T15:30:05Z"), null, null);
    }
}
