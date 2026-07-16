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

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.gnucash.android.ui.settings.PreferenceActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Persists {@link TransactionPreset} button customizations in book-scoped SharedPreferences.
 * <p>No database tables are used, which means presets are <b>not</b> included in
 * GncXml book backups/exports and are not restored with a book — they live and die
 * with the book's SharedPreferences, like other book-local UI settings.</p>
 */
public class TransactionPresetStore {

    public static final String PREFS_KEY = "transaction_presets_json";
    private static final String LOG_TAG = "TransactionPresetStore";

    private final SharedPreferences mPreferences;

    /** Ordered cache of presets keyed by ID. {@code null} until first loaded. */
    @Nullable
    private LinkedHashMap<String, TransactionPreset> mPresetsById;

    /**
     * @param preferences Book-scoped preferences used to persist presets
     */
    public TransactionPresetStore(@NonNull SharedPreferences preferences) {
        mPreferences = preferences;
    }

    /**
     * Creates a store bound to the currently active book's SharedPreferences.
     * @return Store for the active book
     */
    @NonNull
    public static TransactionPresetStore forActiveBook() {
        return new TransactionPresetStore(PreferenceActivity.getActiveBookSharedPreferences());
    }

    /**
     * Loads and caches the stored presets, keeping their insertion order.
     * Unparseable entries are skipped and dropped on the next persist.
     */
    @NonNull
    private LinkedHashMap<String, TransactionPreset> ensureLoaded() {
        if (mPresetsById != null) {
            return mPresetsById;
        }
        LinkedHashMap<String, TransactionPreset> presets = new LinkedHashMap<>();
        String json = mPreferences.getString(PREFS_KEY, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        Log.w(LOG_TAG, "Skipping non-object transaction preset at index " + i);
                        continue;
                    }
                    try {
                        TransactionPreset preset = TransactionPreset.fromJson(object);
                        presets.put(preset.getId(), preset);
                    } catch (JSONException | IllegalArgumentException e) {
                        Log.w(LOG_TAG, "Skipping unparseable transaction preset at index " + i, e);
                    }
                }
            } catch (JSONException e) {
                Log.w(LOG_TAG, "Failed to parse transaction presets; returning empty list", e);
            }
        }
        mPresetsById = presets;
        return presets;
    }

    /**
     * Loads all presets, or an empty list if none are stored / parsing fails.
     * @return Mutable list of preset copies in stored order (never {@code null})
     */
    @NonNull
    public List<TransactionPreset> loadAll() {
        List<TransactionPreset> presets = new ArrayList<>();
        for (TransactionPreset preset : ensureLoaded().values()) {
            presets.add(preset.copy());
        }
        return presets;
    }

    /**
     * Serializes {@code presets} to SharedPreferences.
     * @return {@code false} only if serialization fails; otherwise queues an {@code apply()}
     */
    private boolean persist(@NonNull LinkedHashMap<String, TransactionPreset> presets) {
        JSONArray array = new JSONArray();
        try {
            for (TransactionPreset preset : presets.values()) {
                array.put(preset.toJson());
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to serialize transaction presets", e);
            return false;
        }
        mPreferences.edit().putString(PREFS_KEY, array.toString()).apply();
        return true;
    }

    /**
     * Replaces the stored preset list with {@code presets}.
     * @param presets Presets to persist
     * @return {@code true} if the presets were written, {@code false} on serialization failure
     */
    public boolean saveAll(@NonNull List<TransactionPreset> presets) {
        ensureLoaded();
        LinkedHashMap<String, TransactionPreset> map = new LinkedHashMap<>();
        for (TransactionPreset preset : presets) {
            map.put(preset.getId(), preset.copy());
        }
        if (!persist(map)) {
            return false;
        }
        mPresetsById = map;
        return true;
    }

    /**
     * Appends a preset to the stored list.
     * @param preset Preset to add
     * @return {@code true} if the preset was written, {@code false} on serialization failure
     */
    public boolean add(@NonNull TransactionPreset preset) {
        return upsert(preset);
    }

    /**
     * Updates a preset with a matching ID, or appends it if not found.
     * @param preset Preset to update
     * @return {@code true} if the preset was written, {@code false} on serialization failure
     */
    public boolean update(@NonNull TransactionPreset preset) {
        return upsert(preset);
    }

    private boolean upsert(@NonNull TransactionPreset preset) {
        LinkedHashMap<String, TransactionPreset> next = new LinkedHashMap<>(ensureLoaded());
        next.put(preset.getId(), preset.copy());
        if (!persist(next)) {
            return false;
        }
        mPresetsById = next;
        return true;
    }

    /**
     * Deletes the preset with the given ID.
     * @param presetId ID of the preset to remove
     * @return {@code true} if a preset was removed and persisted, {@code false} otherwise
     */
    public boolean delete(@NonNull String presetId) {
        LinkedHashMap<String, TransactionPreset> current = ensureLoaded();
        if (!current.containsKey(presetId)) {
            return false;
        }
        LinkedHashMap<String, TransactionPreset> next = new LinkedHashMap<>(current);
        next.remove(presetId);
        if (!persist(next)) {
            return false;
        }
        mPresetsById = next;
        return true;
    }

    /**
     * Finds a preset by ID.
     * @param presetId ID to look up
     * @return Copy of the matching preset, or {@code null} if not found
     */
    @Nullable
    public TransactionPreset findById(@NonNull String presetId) {
        TransactionPreset preset = ensureLoaded().get(presetId);
        return preset != null ? preset.copy() : null;
    }
}
