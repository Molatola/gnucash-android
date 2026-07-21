package org.gnucash.android.test.unit.ui;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.gnucash.android.R;
import org.gnucash.android.model.TransactionPreset;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.ui.transaction.TransactionPresetAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link TransactionPresetAdapter} row rendering and edit/delete callbacks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, packageName = "org.gnucash.android", shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class TransactionPresetAdapterTest {

    private FrameLayout mParent;
    private RecordingListener mListener;

    @Before
    public void setUp() {
        mParent = new FrameLayout(RuntimeEnvironment.application);
        mListener = new RecordingListener();
    }

    @Test
    public void getView_showsEditAndDeleteForPreset() {
        TransactionPreset preset = coffeePreset();
        TransactionPresetAdapter adapter = new TransactionPresetAdapter(
                RuntimeEnvironment.application, Collections.singletonList(preset), mListener);

        View row = adapter.getView(0, null, mParent);

        assertThat(row.findViewById(R.id.btn_edit_preset).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(row.findViewById(R.id.btn_delete_preset).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(((TextView) row.findViewById(R.id.preset_text)).getText().toString())
                .isEqualTo("Coffee");
    }

    @Test
    public void getView_usesDisplayLabelFallback() {
        TransactionPreset preset = new TransactionPreset();
        preset.setFromAccountUID("cash-uid");
        preset.setToAccountUID("expense-uid");
        TransactionPresetAdapter adapter = new TransactionPresetAdapter(
                RuntimeEnvironment.application, Collections.singletonList(preset), mListener);

        View row = adapter.getView(0, null, mParent);

        assertThat(((TextView) row.findViewById(R.id.preset_text)).getText().toString())
                .isEqualTo("Cash → Expense");
    }

    @Test
    public void editButton_invokesListener() {
        TransactionPreset preset = coffeePreset();
        TransactionPresetAdapter adapter = new TransactionPresetAdapter(
                RuntimeEnvironment.application, Collections.singletonList(preset), mListener);

        View row = adapter.getView(0, null, mParent);
        row.findViewById(R.id.btn_edit_preset).performClick();

        assertThat(mListener.edited.get()).isSameAs(preset);
        assertThat(mListener.deleted.get()).isNull();
    }

    @Test
    public void deleteButton_invokesListener() {
        TransactionPreset preset = coffeePreset();
        TransactionPresetAdapter adapter = new TransactionPresetAdapter(
                RuntimeEnvironment.application, Collections.singletonList(preset), mListener);

        View row = adapter.getView(0, null, mParent);
        row.findViewById(R.id.btn_delete_preset).performClick();

        assertThat(mListener.deleted.get()).isSameAs(preset);
        assertThat(mListener.edited.get()).isNull();
    }

    @Test
    public void getView_recyclesWithoutStaleListeners() {
        TransactionPreset first = coffeePreset();
        TransactionPreset second = new TransactionPreset();
        second.setLabel("Lunch");
        second.setFromAccountUID("cash-uid");
        second.setToAccountUID("expense-uid");

        List<TransactionPreset> presets = new ArrayList<>();
        presets.add(first);
        presets.add(second);
        TransactionPresetAdapter adapter = new TransactionPresetAdapter(
                RuntimeEnvironment.application, presets, mListener);

        View row = adapter.getView(0, null, mParent);
        row = adapter.getView(1, row, mParent);
        row.findViewById(R.id.btn_edit_preset).performClick();

        assertThat(mListener.edited.get()).isSameAs(second);
        assertThat(((TextView) row.findViewById(R.id.preset_text)).getText().toString())
                .isEqualTo("Lunch");
    }

    @NonNull
    private static TransactionPreset coffeePreset() {
        TransactionPreset preset = new TransactionPreset();
        preset.setLabel("Coffee");
        preset.setFromAccountUID("cash-uid");
        preset.setToAccountUID("expense-uid");
        return preset;
    }

    private static final class RecordingListener implements TransactionPresetAdapter.Listener {
        final AtomicReference<TransactionPreset> edited = new AtomicReference<>();
        final AtomicReference<TransactionPreset> deleted = new AtomicReference<>();

        @Override
        @NonNull
        public String getAccountDisplayName(@Nullable String accountUID) {
            if ("cash-uid".equals(accountUID)) {
                return "Cash";
            }
            if ("expense-uid".equals(accountUID)) {
                return "Expense";
            }
            return "?";
        }

        @Override
        public void onEditPreset(@NonNull TransactionPreset preset) {
            edited.set(preset);
        }

        @Override
        public void onDeletePreset(@NonNull TransactionPreset preset) {
            deleted.set(preset);
        }
    }
}
