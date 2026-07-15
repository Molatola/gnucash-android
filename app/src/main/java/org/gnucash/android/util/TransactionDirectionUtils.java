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

import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;

/**
 * Helpers for inferring transaction direction from selected accounts.
 */
public final class TransactionDirectionUtils {

    private TransactionDirectionUtils() {
        // prevent instantiation
    }

    /**
     * Returns the default {@link TransactionType} for the <em>from</em> account split,
     * based on the selected account types.
     * <ul>
     *   <li>Transfer to {@link AccountType#EXPENSE} / {@link AccountType#PAYABLE} → from decreases</li>
     *   <li>Transfer to {@link AccountType#INCOME} / {@link AccountType#RECEIVABLE} → from increases</li>
     *   <li>Otherwise (e.g. asset transfer) → from decreases by default</li>
     * </ul>
     *
     * @param fromType Type of the origin account
     * @param toType   Type of the transfer account; when {@code null}, uses {@code fallbackType}
     * @param fallbackType Used when {@code toType} is null (typically book default)
     */
    @NonNull
    public static TransactionType defaultTypeForAccounts(@NonNull AccountType fromType,
                                                         @Nullable AccountType toType,
                                                         @NonNull TransactionType fallbackType) {
        if (toType == null) {
            return fallbackType;
        }

        final boolean shouldDecreaseFrom;
        switch (toType) {
            case EXPENSE:
            case PAYABLE:
                shouldDecreaseFrom = true;
                break;
            case INCOME:
            case RECEIVABLE:
                shouldDecreaseFrom = false;
                break;
            default:
                shouldDecreaseFrom = true;
                break;
        }
        return Transaction.getTypeForBalance(fromType, shouldDecreaseFrom);
    }

    /**
     * Convenience overload that falls back to {@link TransactionType#DEBIT} when {@code toType} is null.
     */
    @NonNull
    public static TransactionType defaultTypeForAccounts(@NonNull AccountType fromType,
                                                         @Nullable AccountType toType) {
        return defaultTypeForAccounts(fromType, toType, TransactionType.DEBIT);
    }
}
