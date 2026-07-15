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
 * <p>Presets are cached in-memory as an ordered ID-to-preset map so lookups and
 * mutations avoid re-parsing the stored JSON. The cache is only updated after a
 * successful write, and all presets returned to callers are defensive copies.</p>
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
     * Raw entries that failed to parse during load. They are re-appended verbatim on
     * every persist so a corrupt entry is never silently dropped from storage.
     */
    private final List<Object> mUnparseableEntries = new ArrayList<>();

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
        mUnparseableEntries.clear();
        String json = mPreferences.getString(PREFS_KEY, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        mUnparseableEntries.add(array.opt(i));
                        continue;
                    }
                    try {
                        TransactionPreset preset = TransactionPreset.fromJson(object);
                        presets.put(preset.getId(), preset);
                    } catch (JSONException | IllegalArgumentException e) {
                        Log.w(LOG_TAG, "Preserving unparseable transaction preset at index " + i, e);
                        mUnparseableEntries.add(object);
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
     * Serializes {@code presets} (plus any preserved unparseable entries) to
     * SharedPreferences without touching the cache.
     * <p>Uses {@code commit()} so the returned boolean reflects durable success.</p>
     * @param presets Candidate preset map to write
     * @return {@code true} if the presets were written, {@code false} on failure
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
        for (Object rawEntry : mUnparseableEntries) {
            array.put(rawEntry);
        }
        return mPreferences.edit().putString(PREFS_KEY, array.toString()).commit();
    }

    /**
     * Replaces the stored preset list with {@code presets}.
     * <p>Unparseable entries preserved from a previous load are kept in storage.</p>
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

    /**
     * Inserts or replaces the preset with a matching ID and persists the result.
     * <p>The cache is only updated after a successful write, so a failed persist
     * never leaves the in-memory state diverged from storage.</p>
     * @param preset Preset to add or update (copied; the caller's instance is not retained)
     * @return {@code true} if the preset was written, {@code false} on serialization failure
     */
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
     * <p>The cache is only updated after a successful write.</p>
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
