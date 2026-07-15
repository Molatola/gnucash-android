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

    /**
     * Creates a preset with a newly generated unique ID.
     */
    public TransactionPreset() {
        mId = BaseModel.generateUID();
    }

    /**
     * Creates a preset with the given unique ID (used when restoring from storage).
     * @param id Existing preset identifier
     */
    public TransactionPreset(@NonNull String id) {
        mId = id;
    }

    /**
     * @return Unique identifier of this preset
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * @param id Unique identifier of this preset
     */
    public void setId(@NonNull String id) {
        mId = id;
    }

    /**
     * @return Button label, or empty string when unset
     */
    @NonNull
    public String getLabel() {
        return mLabel != null ? mLabel : "";
    }

    /**
     * @param label Button label shown on the preset chip
     */
    public void setLabel(@Nullable String label) {
        mLabel = label != null ? label : "";
    }

    /**
     * @return UID of the from/origin account, or {@code null} if unset
     */
    @Nullable
    public String getFromAccountUID() {
        return mFromAccountUID;
    }

    /**
     * @param fromAccountUID UID of the from/origin account
     */
    public void setFromAccountUID(@Nullable String fromAccountUID) {
        mFromAccountUID = fromAccountUID;
    }

    /**
     * @return UID of the to/transfer account, or {@code null} if unset
     */
    @Nullable
    public String getToAccountUID() {
        return mToAccountUID;
    }

    /**
     * @param toAccountUID UID of the to/transfer account
     */
    public void setToAccountUID(@Nullable String toAccountUID) {
        mToAccountUID = toAccountUID;
    }

    /**
     * Default amount as a plain US-locale decimal string, or {@code null} if unset.
     * @return Amount string, or {@code null}
     */
    @Nullable
    public String getAmount() {
        return mAmount;
    }

    /**
     * @param amount Default amount as a plain decimal string, or {@code null} to clear
     */
    public void setAmount(@Nullable String amount) {
        mAmount = amount;
    }

    /**
     * Optional saved direction for the from-account split. When {@code null}, the overlay
     * recomputes the default from the selected account types.
     * @return Saved {@link TransactionType}, or {@code null}
     */
    @Nullable
    public TransactionType getDirectionOverride() {
        return mDirectionOverride;
    }

    /**
     * @param directionOverride Saved direction for the from-account split, or {@code null} to clear
     */
    public void setDirectionOverride(@Nullable TransactionType directionOverride) {
        mDirectionOverride = directionOverride;
    }

    /**
     * @return Transaction description/name, or empty string when unset
     */
    @NonNull
    public String getDescription() {
        return mDescription != null ? mDescription : "";
    }

    /**
     * @param description Transaction description/name
     */
    public void setDescription(@Nullable String description) {
        mDescription = description != null ? description : "";
    }

    /**
     * @return Transaction notes, or empty string when unset
     */
    @NonNull
    public String getNotes() {
        return mNotes != null ? mNotes : "";
    }

    /**
     * @param notes Transaction notes
     */
    public void setNotes(@Nullable String notes) {
        mNotes = notes != null ? notes : "";
    }

    /**
     * Display label for the preset button. Falls back to a from→to hint when label is blank.
     * @param fromName Display name of the from account (may be {@code null})
     * @param toName Display name of the to account (may be {@code null})
     * @return Label to show on the preset button
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

    /**
     * Serializes this preset to a JSON object for SharedPreferences storage.
     * @return JSON representation of the preset
     * @throws JSONException if the object cannot be built
     */
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

    /**
     * Restores a preset from its JSON representation.
     * @param json JSON object previously produced by {@link #toJson()}
     * @return Restored preset instance
     * @throws JSONException if required fields are invalid
     */
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
