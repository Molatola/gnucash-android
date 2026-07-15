package org.gnucash.android.test.unit.util;

import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.util.TransactionDirectionUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class TransactionDirectionUtilsTest {

    @Test
    public void cashToExpense_decreasesCash() {
        // Cash has debit normal balance; decreasing ⇒ CREDIT
        TransactionType type = TransactionDirectionUtils.defaultTypeForAccounts(
                AccountType.CASH, AccountType.EXPENSE);
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    public void cashToIncome_increasesCash() {
        // Increasing cash (debit-normal) ⇒ DEBIT
        TransactionType type = TransactionDirectionUtils.defaultTypeForAccounts(
                AccountType.CASH, AccountType.INCOME);
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    public void bankToBank_defaultsToDecreaseFrom() {
        TransactionType type = TransactionDirectionUtils.defaultTypeForAccounts(
                AccountType.BANK, AccountType.BANK);
        assertThat(type).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    public void nullToType_usesFallback() {
        TransactionType type = TransactionDirectionUtils.defaultTypeForAccounts(
                AccountType.CASH, null, TransactionType.DEBIT);
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    public void creditCardToExpense_decreasesLiabilityCreditNormal() {
        // LIABILITY has credit normal balance; decreasing ⇒ DEBIT
        TransactionType type = TransactionDirectionUtils.defaultTypeForAccounts(
                AccountType.CREDIT, AccountType.EXPENSE);
        assertThat(type).isEqualTo(TransactionType.DEBIT);
    }
}
