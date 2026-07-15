package org.gnucash.android.test.unit.util;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.util.TransactionComposer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class TransactionComposerTest {

    @Test
    public void compose_buildsBalancedTwoSplitTransaction() {
        Transaction transaction = TransactionComposer.compose(
                "Coffee",
                "Morning",
                new BigDecimal("3.50"),
                Commodity.USD,
                "cash-uid",
                "expense-uid",
                TransactionType.CREDIT);

        assertThat(transaction.getDescription()).isEqualTo("Coffee");
        assertThat(transaction.getNote()).isEqualTo("Morning");
        assertThat(transaction.getCurrencyCode()).isEqualTo(Commodity.USD.getCurrencyCode());

        List<Split> splits = transaction.getSplits();
        assertThat(splits).hasSize(2);

        Split from = splits.get(0);
        Split to = splits.get(1);
        assertThat(from.getAccountUID()).isEqualTo("cash-uid");
        assertThat(from.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(to.getAccountUID()).isEqualTo("expense-uid");
        assertThat(to.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(from.getValue().asBigDecimal()).isEqualByComparingTo("3.50");
        assertThat(to.getValue().asBigDecimal()).isEqualByComparingTo("3.50");
    }

    @Test
    public void compose_rejectsSameAccounts() {
        try {
            TransactionComposer.compose(
                    "Bad",
                    null,
                    BigDecimal.ONE,
                    Commodity.USD,
                    "same",
                    "same",
                    TransactionType.DEBIT);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void compose_usesAbsoluteAmount() {
        Transaction transaction = TransactionComposer.compose(
                "Refund",
                null,
                new BigDecimal("-5.00"),
                Commodity.USD,
                "cash-uid",
                "expense-uid",
                TransactionType.DEBIT);

        assertThat(transaction.getSplits().get(0).getValue().asBigDecimal())
                .isEqualByComparingTo("5.00");
    }
}
