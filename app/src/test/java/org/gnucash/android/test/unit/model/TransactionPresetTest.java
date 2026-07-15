package org.gnucash.android.test.unit.model;

import android.content.SharedPreferences;

import org.gnucash.android.model.TransactionPreset;
import org.gnucash.android.model.TransactionPresetStore;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class TransactionPresetTest {

    private SharedPreferences mPreferences;
    private TransactionPresetStore mStore;

    @Before
    public void setUp() {
        mPreferences = RuntimeEnvironment.application.getSharedPreferences("preset-test", 0);
        mPreferences.edit().clear().commit();
        mStore = new TransactionPresetStore(mPreferences);
    }

    @Test
    public void jsonRoundTrip_preservesFields() throws Exception {
        TransactionPreset preset = new TransactionPreset();
        preset.setLabel("Coffee");
        preset.setFromAccountUID("cash-uid");
        preset.setToAccountUID("expense-uid");
        preset.setAmount("3.50");
        preset.setDirectionOverride(TransactionType.CREDIT);
        preset.setDescription("Morning coffee");
        preset.setNotes("Starbucks");

        JSONObject json = preset.toJson();
        TransactionPreset restored = TransactionPreset.fromJson(json);

        assertThat(restored.getId()).isEqualTo(preset.getId());
        assertThat(restored.getLabel()).isEqualTo("Coffee");
        assertThat(restored.getFromAccountUID()).isEqualTo("cash-uid");
        assertThat(restored.getToAccountUID()).isEqualTo("expense-uid");
        assertThat(restored.getAmount()).isEqualTo("3.50");
        assertThat(restored.getDirectionOverride()).isEqualTo(TransactionType.CREDIT);
        assertThat(restored.getDescription()).isEqualTo("Morning coffee");
        assertThat(restored.getNotes()).isEqualTo("Starbucks");
    }

    @Test
    public void jsonRoundTrip_omitsOptionalDirection() throws Exception {
        TransactionPreset preset = new TransactionPreset();
        preset.setFromAccountUID("a");
        preset.setToAccountUID("b");

        TransactionPreset restored = TransactionPreset.fromJson(preset.toJson());
        assertThat(restored.getDirectionOverride()).isNull();
        assertThat(restored.getAmount()).isNull();
    }

    @Test
    public void displayLabel_fallsBackToFromTo() {
        TransactionPreset preset = new TransactionPreset();
        assertThat(preset.getDisplayLabel("Cash", "Coffee")).isEqualTo("Cash → Coffee");

        preset.setLabel("  Latte  ");
        assertThat(preset.getDisplayLabel("Cash", "Coffee")).isEqualTo("Latte");
    }

    @Test
    public void store_addUpdateDelete_roundTrips() {
        TransactionPreset coffee = new TransactionPreset();
        coffee.setLabel("Coffee");
        coffee.setFromAccountUID("cash");
        coffee.setToAccountUID("expense");
        coffee.setAmount("3.50");
        mStore.add(coffee);

        List<TransactionPreset> loaded = mStore.loadAll();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getLabel()).isEqualTo("Coffee");
        assertThat(loaded.get(0).getAmount()).isEqualTo("3.50");

        coffee.setAmount("4.00");
        coffee.setNotes("updated");
        mStore.update(coffee);

        TransactionPreset found = mStore.findById(coffee.getId());
        assertThat(found).isNotNull();
        assertThat(found.getAmount()).isEqualTo("4.00");
        assertThat(found.getNotes()).isEqualTo("updated");

        assertThat(mStore.delete(coffee.getId())).isTrue();
        assertThat(mStore.loadAll()).isEmpty();
        assertThat(mStore.delete("missing")).isFalse();
    }

    @Test
    public void store_corruptJson_returnsEmptyList() {
        mPreferences.edit().putString(TransactionPresetStore.PREFS_KEY, "{not-json").commit();
        assertThat(mStore.loadAll()).isEmpty();
    }

    @Test
    public void store_skipsUnparseableEntries_keepsValid() throws Exception {
        TransactionPreset valid = new TransactionPreset();
        valid.setLabel("Good");
        valid.setFromAccountUID("a");
        valid.setToAccountUID("b");

        JSONArray array = new JSONArray();
        array.put(valid.toJson());
        array.put("not-an-object");
        array.put(new JSONObject().put("id", "bad").put("directionOverride", "NOT_A_TYPE"));
        mPreferences.edit().putString(TransactionPresetStore.PREFS_KEY, array.toString()).commit();

        List<TransactionPreset> loaded = mStore.loadAll();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getLabel()).isEqualTo("Good");
    }

    @Test
    public void store_persistPreservesUnparseableEntries() throws Exception {
        TransactionPreset valid = new TransactionPreset();
        valid.setLabel("Good");
        valid.setFromAccountUID("a");
        valid.setToAccountUID("b");

        JSONArray array = new JSONArray();
        array.put(valid.toJson());
        array.put("not-an-object");
        array.put(new JSONObject().put("id", "bad").put("directionOverride", "NOT_A_TYPE"));
        mPreferences.edit().putString(TransactionPresetStore.PREFS_KEY, array.toString()).commit();

        TransactionPreset added = new TransactionPreset();
        added.setLabel("New");
        added.setFromAccountUID("x");
        added.setToAccountUID("y");
        assertThat(mStore.add(added)).isTrue();

        // corrupt entries must survive the rewrite verbatim
        String stored = mPreferences.getString(TransactionPresetStore.PREFS_KEY, null);
        JSONArray storedArray = new JSONArray(stored);
        assertThat(storedArray.length()).isEqualTo(4);
        assertThat(stored).contains("not-an-object");
        assertThat(stored).contains("NOT_A_TYPE");

        // a fresh store still parses only the valid presets
        TransactionPresetStore reloaded = new TransactionPresetStore(mPreferences);
        assertThat(reloaded.loadAll()).hasSize(2);
    }

    @Test
    public void store_findById_returnsDefensiveCopy() {
        TransactionPreset preset = new TransactionPreset();
        preset.setLabel("Original");
        preset.setFromAccountUID("a");
        preset.setToAccountUID("b");
        assertThat(mStore.add(preset)).isTrue();

        TransactionPreset found = mStore.findById(preset.getId());
        found.setLabel("Mutated without update()");

        assertThat(mStore.findById(preset.getId()).getLabel()).isEqualTo("Original");
        assertThat(mStore.loadAll().get(0).getLabel()).isEqualTo("Original");
    }

    @Test
    public void store_add_doesNotAliasCallerInstance() {
        TransactionPreset preset = new TransactionPreset();
        preset.setLabel("Original");
        preset.setFromAccountUID("a");
        preset.setToAccountUID("b");
        assertThat(mStore.add(preset)).isTrue();

        preset.setLabel("Mutated after add()");

        assertThat(mStore.findById(preset.getId()).getLabel()).isEqualTo("Original");
    }

    @Test
    public void fromJson_nullId_generatesFreshIdInsteadOfLiteralNullString() throws Exception {
        JSONObject json = new JSONObject()
                .put("id", JSONObject.NULL)
                .put("fromAccountUid", "a")
                .put("toAccountUid", "b");

        TransactionPreset first = TransactionPreset.fromJson(json);
        TransactionPreset second = TransactionPreset.fromJson(json);

        assertThat(first.getId()).isNotEqualTo("null");
        assertThat(first.getId()).isNotEmpty();
        // distinct generated IDs, so two such presets cannot collide in the store
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    public void copy_isIndependentOfOriginal() {
        TransactionPreset preset = new TransactionPreset();
        preset.setLabel("Coffee");
        preset.setFromAccountUID("cash");
        preset.setToAccountUID("expense");
        preset.setAmount("3.50");
        preset.setDirectionOverride(TransactionType.CREDIT);

        TransactionPreset copy = preset.copy();
        assertThat(copy.getId()).isEqualTo(preset.getId());
        assertThat(copy.getLabel()).isEqualTo("Coffee");
        assertThat(copy.getAmount()).isEqualTo("3.50");
        assertThat(copy.getDirectionOverride()).isEqualTo(TransactionType.CREDIT);

        copy.setLabel("Tea");
        copy.setAmount("9.99");
        assertThat(preset.getLabel()).isEqualTo("Coffee");
        assertThat(preset.getAmount()).isEqualTo("3.50");
    }

    @Test
    public void store_findById_afterAdd_keepsSiblings() {
        TransactionPreset a = new TransactionPreset();
        a.setLabel("A");
        a.setFromAccountUID("x");
        a.setToAccountUID("y");
        TransactionPreset b = new TransactionPreset();
        b.setLabel("B");
        b.setFromAccountUID("x");
        b.setToAccountUID("z");

        assertThat(mStore.add(a)).isTrue();
        assertThat(mStore.add(b)).isTrue();

        assertThat(mStore.findById(a.getId()).getLabel()).isEqualTo("A");
        assertThat(mStore.findById(b.getId()).getLabel()).isEqualTo("B");
        assertThat(mStore.loadAll()).hasSize(2);

        assertThat(mStore.delete(a.getId())).isTrue();
        assertThat(mStore.findById(a.getId())).isNull();
        assertThat(mStore.findById(b.getId())).isNotNull();
    }
}
