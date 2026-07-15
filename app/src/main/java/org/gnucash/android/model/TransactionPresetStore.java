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
import java.util.Map;

/**
 * Persists {@link TransactionPreset} button customizations in book-scoped SharedPreferences.
 * <p>No database tables are used. Presets are cached in-memory as an ordered
 * ID-to-preset map so lookups and mutations avoid re-parsing the stored JSON.</p>
 */
public class TransactionPresetStore {

    public static final String PREFS_KEY = "transaction_presets_json";
    private static final String LOG_TAG = "TransactionPresetStore";

    private final SharedPreferences mPreferences;

    /**
     * Ordered cache of presets keyed by ID. {@code null} until first loaded.
     */
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
     * <p>A corrupt top-level document yields an empty map; individual entries that
     * fail to parse are logged and skipped so one bad preset cannot hide the rest.</p>
     * @return Ordered ID-to-preset cache (never {@code null})
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
     * @return Mutable list of presets in stored order (never {@code null})
     */
    @NonNull
    public List<TransactionPreset> loadAll() {
        return new ArrayList<>(ensureLoaded().values());
    }

    /**
     * Serializes the current cache to SharedPreferences.
     * @return {@code true} if the presets were written, {@code false} on serialization failure
     */
    private boolean persist() {
        JSONArray array = new JSONArray();
        try {
            for (TransactionPreset preset : ensureLoaded().values()) {
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
        LinkedHashMap<String, TransactionPreset> map = new LinkedHashMap<>();
        for (TransactionPreset preset : presets) {
            map.put(preset.getId(), preset);
        }
        mPresetsById = map;
        return persist();
    }

    /**
     * Appends a preset to the stored list.
     * @param preset Preset to add
     * @return {@code true} if the preset was written, {@code false} on serialization failure
     */
    public boolean add(@NonNull TransactionPreset preset) {
        ensureLoaded().put(preset.getId(), preset);
        return persist();
    }

    /**
     * Updates a preset with a matching ID, or appends it if not found.
     * @param preset Preset to update
     * @return {@code true} if the preset was written, {@code false} on serialization failure
     */
    public boolean update(@NonNull TransactionPreset preset) {
        ensureLoaded().put(preset.getId(), preset);
        return persist();
    }

    /**
     * Deletes the preset with the given ID.
     * @param presetId ID of the preset to remove
     * @return {@code true} if a preset was removed and persisted, {@code false} otherwise
     */
    public boolean delete(@NonNull String presetId) {
        if (ensureLoaded().remove(presetId) == null) {
            return false;
        }
        return persist();
    }

    /**
     * Finds a preset by ID.
     * @param presetId ID to look up
     * @return Matching preset, or {@code null} if not found
     */
    @Nullable
    public TransactionPreset findById(@NonNull String presetId) {
        return ensureLoaded().get(presetId);
    }
}
