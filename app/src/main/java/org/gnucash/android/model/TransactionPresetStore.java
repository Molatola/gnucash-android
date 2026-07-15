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
import java.util.Collections;
import java.util.List;

/**
 * Persists {@link TransactionPreset} button customizations in book-scoped SharedPreferences.
 * <p>No database tables are used.</p>
 */
public class TransactionPresetStore {

    public static final String PREFS_KEY = "transaction_presets_json";
    private static final String LOG_TAG = "TransactionPresetStore";

    private final SharedPreferences mPreferences;

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
     * Loads all presets, or an empty list if none are stored / parsing fails.
     * @return Mutable list of presets (never {@code null})
     */
    @NonNull
    public List<TransactionPreset> loadAll() {
        String json = mPreferences.getString(PREFS_KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = new JSONArray(json);
            List<TransactionPreset> presets = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    presets.add(TransactionPreset.fromJson(object));
                }
            }
            return presets;
        } catch (JSONException | IllegalArgumentException e) {
            Log.w(LOG_TAG, "Failed to parse transaction presets; returning empty list", e);
            return new ArrayList<>();
        }
    }

    /**
     * Replaces the stored preset list with {@code presets}.
     * @param presets Presets to persist
     */
    public void saveAll(@NonNull List<TransactionPreset> presets) {
        JSONArray array = new JSONArray();
        try {
            for (TransactionPreset preset : presets) {
                array.put(preset.toJson());
            }
            mPreferences.edit().putString(PREFS_KEY, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to serialize transaction presets", e);
        }
    }

    /**
     * Appends a preset to the stored list.
     * @param preset Preset to add
     */
    public void add(@NonNull TransactionPreset preset) {
        List<TransactionPreset> presets = loadAll();
        presets.add(preset);
        saveAll(presets);
    }

    /**
     * Updates a preset with a matching ID, or appends it if not found.
     * @param preset Preset to update
     */
    public void update(@NonNull TransactionPreset preset) {
        List<TransactionPreset> presets = loadAll();
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getId().equals(preset.getId())) {
                presets.set(i, preset);
                saveAll(presets);
                return;
            }
        }
        presets.add(preset);
        saveAll(presets);
    }

    /**
     * Deletes the preset with the given ID.
     * @param presetId ID of the preset to remove
     * @return {@code true} if a preset was removed, {@code false} otherwise
     */
    public boolean delete(@NonNull String presetId) {
        List<TransactionPreset> presets = loadAll();
        boolean removed = false;
        for (int i = presets.size() - 1; i >= 0; i--) {
            if (presets.get(i).getId().equals(presetId)) {
                presets.remove(i);
                removed = true;
            }
        }
        if (removed) {
            saveAll(presets);
        }
        return removed;
    }

    /**
     * Finds a preset by ID.
     * @param presetId ID to look up
     * @return Matching preset, or {@code null} if not found
     */
    @Nullable
    public TransactionPreset findById(@NonNull String presetId) {
        for (TransactionPreset preset : loadAll()) {
            if (preset.getId().equals(presetId)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable snapshot of stored presets.
     */
    @NonNull
    public List<TransactionPreset> getPresetsSnapshot() {
        return Collections.unmodifiableList(loadAll());
    }
}
