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
package org.gnucash.android.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * In-memory wrapper for quick-entry button customization.
 * <p>Presets are persisted in SharedPreferences as JSON — not as database rows.</p>
 */
public class TransactionPreset {

    private static final String KEY_ID = "id";
    private static final String KEY_LABEL = "label";
    private static final String KEY_FROM_ACCOUNT_UID = "fromAccountUid";
    private static final String KEY_TO_ACCOUNT_UID = "toAccountUid";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_DIRECTION_OVERRIDE = "directionOverride";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_NOTES = "notes";

    private String mId;
    private String mLabel = "";
    private String mFromAccountUID;
    private String mToAccountUID;
    private String mAmount;
    private TransactionType mDirectionOverride;
    private String mDescription = "";
    private String mNotes = "";

    public TransactionPreset() {
        mId = BaseModel.generateUID();
    }

    public TransactionPreset(@NonNull String id) {
        mId = id;
    }

    @NonNull
    public String getId() {
        return mId;
    }

    public void setId(@NonNull String id) {
        mId = id;
    }

    @NonNull
    public String getLabel() {
        return mLabel != null ? mLabel : "";
    }

    public void setLabel(@Nullable String label) {
        mLabel = label != null ? label : "";
    }

    @Nullable
    public String getFromAccountUID() {
        return mFromAccountUID;
    }

    public void setFromAccountUID(@Nullable String fromAccountUID) {
        mFromAccountUID = fromAccountUID;
    }

    @Nullable
    public String getToAccountUID() {
        return mToAccountUID;
    }

    public void setToAccountUID(@Nullable String toAccountUID) {
        mToAccountUID = toAccountUID;
    }

    /**
     * Default amount as a plain US-locale decimal string, or {@code null} if unset.
     */
    @Nullable
    public String getAmount() {
        return mAmount;
    }

    public void setAmount(@Nullable String amount) {
        mAmount = amount;
    }

    /**
     * Optional saved direction for the from-account split. When {@code null}, the overlay
     * recomputes the default from the selected account types.
     */
    @Nullable
    public TransactionType getDirectionOverride() {
        return mDirectionOverride;
    }

    public void setDirectionOverride(@Nullable TransactionType directionOverride) {
        mDirectionOverride = directionOverride;
    }

    @NonNull
    public String getDescription() {
        return mDescription != null ? mDescription : "";
    }

    public void setDescription(@Nullable String description) {
        mDescription = description != null ? description : "";
    }

    @NonNull
    public String getNotes() {
        return mNotes != null ? mNotes : "";
    }

    public void setNotes(@Nullable String notes) {
        mNotes = notes != null ? notes : "";
    }

    /**
     * Display label for the preset button. Falls back to a from→to hint when label is blank.
     */
    @NonNull
    public String getDisplayLabel(@Nullable String fromName, @Nullable String toName) {
        if (mLabel != null && !mLabel.trim().isEmpty()) {
            return mLabel.trim();
        }
        String from = fromName != null ? fromName : "?";
        String to = toName != null ? toName : "?";
        return from + " → " + to;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_ID, getId());
        json.put(KEY_LABEL, getLabel());
        json.put(KEY_FROM_ACCOUNT_UID, mFromAccountUID);
        json.put(KEY_TO_ACCOUNT_UID, mToAccountUID);
        json.put(KEY_AMOUNT, mAmount);
        json.put(KEY_DIRECTION_OVERRIDE,
                mDirectionOverride != null ? mDirectionOverride.name() : null);
        json.put(KEY_DESCRIPTION, getDescription());
        json.put(KEY_NOTES, getNotes());
        return json;
    }

    @NonNull
    public static TransactionPreset fromJson(@NonNull JSONObject json) throws JSONException {
        String id = json.optString(KEY_ID, null);
        TransactionPreset preset = (id != null && !id.isEmpty())
                ? new TransactionPreset(id)
                : new TransactionPreset();
        preset.setLabel(json.optString(KEY_LABEL, ""));
        preset.setFromAccountUID(optNullableString(json, KEY_FROM_ACCOUNT_UID));
        preset.setToAccountUID(optNullableString(json, KEY_TO_ACCOUNT_UID));
        preset.setAmount(optNullableString(json, KEY_AMOUNT));
        String direction = optNullableString(json, KEY_DIRECTION_OVERRIDE);
        if (direction != null) {
            preset.setDirectionOverride(TransactionType.valueOf(direction));
        }
        preset.setDescription(json.optString(KEY_DESCRIPTION, ""));
        preset.setNotes(json.optString(KEY_NOTES, ""));
        return preset;
    }

    @Nullable
    private static String optNullableString(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        String value = json.optString(key, null);
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        return value;
    }
}
