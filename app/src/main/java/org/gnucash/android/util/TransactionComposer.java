/*
 * Copyright (c) 2026 GnuCash Android contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;

import java.math.BigDecimal;

/**
 * Builds simple two-split transactions for the quick-entry overlay.
 * <p>Mirrors the happy path in {@code TransactionFormFragment#extractSplitsFromView()}.</p>
 */
public final class TransactionComposer {

    private TransactionComposer() {
        // prevent instantiation
    }

    /**
     * Composes a transaction with two balancing splits.
     *
     * @param description        Transaction description / name
     * @param notes              Optional notes
     * @param amount             Unsigned amount value
     * @param commodity          Commodity/currency of the from account (and value)
     * @param fromAccountUID     Origin account UID
     * @param toAccountUID       Transfer account UID (or imbalance account when double-entry is off)
     * @param fromTransactionType Type of the split belonging to {@code fromAccountUID}
     */
    @NonNull
    public static Transaction compose(@Nullable String description,
                                      @Nullable String notes,
                                      @NonNull BigDecimal amount,
                                      @NonNull Commodity commodity,
                                      @NonNull String fromAccountUID,
                                      @NonNull String toAccountUID,
                                      @NonNull TransactionType fromTransactionType) {
        if (fromAccountUID.equals(toAccountUID)) {
            throw new IllegalArgumentException("From and to accounts must be different");
        }

        BigDecimal absoluteAmount = amount.abs();
        Money value = new Money(absoluteAmount, commodity);

        Split fromSplit = new Split(value, fromAccountUID);
        fromSplit.setType(fromTransactionType);

        Split toSplit = new Split(value, toAccountUID);
        toSplit.setType(fromTransactionType.invert());

        Transaction transaction = new Transaction(description != null ? description : "");
        transaction.setTime(System.currentTimeMillis());
        transaction.setCommodity(commodity);
        transaction.setNote(notes != null ? notes : "");
        transaction.addSplit(fromSplit);
        transaction.addSplit(toSplit);
        transaction.setExported(false);
        return transaction;
    }
}
