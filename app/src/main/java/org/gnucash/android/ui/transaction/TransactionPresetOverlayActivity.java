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
package org.gnucash.android.ui.transaction;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionPreset;
import org.gnucash.android.model.TransactionPresetStore;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch;
import org.gnucash.android.util.AmountParser;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;
import org.gnucash.android.util.TransactionComposer;
import org.gnucash.android.util.TransactionDirectionUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Dialog-style overlay for quick transaction entry with customizable preset buttons.
 * <p>Presets are stored in SharedPreferences — not in the database.</p>
 */
public class TransactionPresetOverlayActivity extends PasscodeLockActivity {

    private static final String ACCOUNT_CONDITIONS =
            DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0";

    @BindView(R.id.preset_buttons_container) LinearLayout mPresetButtonsContainer;
    @BindView(R.id.input_from_account) Spinner mFromAccountSpinner;
    @BindView(R.id.input_to_account) Spinner mToAccountSpinner;
    @BindView(R.id.layout_to_account) View mToAccountLayout;
    @BindView(R.id.input_preset_amount) EditText mAmountEditText;
    @BindView(R.id.input_preset_direction) TransactionTypeSwitch mDirectionSwitch;
    @BindView(R.id.label_extra_settings) TextView mExtraSettingsLabel;
    @BindView(R.id.layout_extra_settings) View mExtraSettingsLayout;
    @BindView(R.id.input_preset_label) EditText mLabelEditText;
    @BindView(R.id.input_preset_description) EditText mDescriptionEditText;
    @BindView(R.id.input_preset_notes) EditText mNotesEditText;
    @BindView(R.id.btn_save_preset) Button mSavePresetButton;
    @BindView(R.id.btn_save) Button mSaveButton;
    @BindView(R.id.btn_cancel) Button mCancelButton;

    private AccountsDbAdapter mAccountsDbAdapter;
    private TransactionsDbAdapter mTransactionsDbAdapter;
    private TransactionPresetStore mPresetStore;
    private QualifiedAccountNameCursorAdapter mFromAdapter;
    private QualifiedAccountNameCursorAdapter mToAdapter;
    private Cursor mFromCursor;
    private Cursor mToCursor;

    private boolean mUseDoubleEntry;
    private boolean mDirectionUserOverridden;
    private boolean mUpdatingSpinners;
    private boolean mExtraSettingsExpanded;
    private String mEditingPresetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_preset_overlay);
        setFinishOnTouchOutside(true);
        ButterKnife.bind(this);

        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
        mPresetStore = TransactionPresetStore.forActiveBook();
        mUseDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();

        if (!mUseDoubleEntry) {
            mToAccountLayout.setVisibility(View.GONE);
        }

        bindAccountSpinners();
        if (mFromAdapter.getCount() <= 0) {
            Toast.makeText(this, R.string.error_no_accounts, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindListeners();
        refreshPresetButtons();
        applyDirectionDefault();
    }

    private void bindAccountSpinners() {
        mFromCursor = mAccountsDbAdapter.fetchAccountsOrderedByFavoriteAndFullName(
                ACCOUNT_CONDITIONS, new String[]{AccountType.ROOT.name()});
        mFromAdapter = new QualifiedAccountNameCursorAdapter(this, mFromCursor);
        mFromAccountSpinner.setAdapter(mFromAdapter);

        if (mUseDoubleEntry) {
            refreshToAccountSpinner(null);
        }
    }

    private void refreshToAccountSpinner(String excludeAccountUID) {
        if (mToCursor != null) {
            mToCursor.close();
        }
        String conditions = ACCOUNT_CONDITIONS;
        String[] args;
        if (excludeAccountUID != null) {
            conditions = ACCOUNT_CONDITIONS + " AND " + DatabaseSchema.AccountEntry.COLUMN_UID + " != ?";
            args = new String[]{AccountType.ROOT.name(), excludeAccountUID};
        } else {
            args = new String[]{AccountType.ROOT.name()};
        }
        mToCursor = mAccountsDbAdapter.fetchAccountsOrderedByFavoriteAndFullName(conditions, args);
        mToAdapter = new QualifiedAccountNameCursorAdapter(this, mToCursor);
        mUpdatingSpinners = true;
        mToAccountSpinner.setAdapter(mToAdapter);
        mUpdatingSpinners = false;
    }

    private void bindListeners() {
        AdapterView.OnItemSelectedListener accountSelectedListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mUpdatingSpinners) {
                    return;
                }
                if (parent.getId() == R.id.input_from_account && mUseDoubleEntry) {
                    String fromUID = mAccountsDbAdapter.getUID(id);
                    String previouslySelectedTo = getSelectedAccountUID(mToAccountSpinner);
                    refreshToAccountSpinner(fromUID);
                    if (previouslySelectedTo != null && !previouslySelectedTo.equals(fromUID)) {
                        selectAccount(mToAccountSpinner, mToAdapter, previouslySelectedTo);
                    }
                }
                mDirectionUserOverridden = false;
                applyDirectionDefault();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        };
        mFromAccountSpinner.setOnItemSelectedListener(accountSelectedListener);
        mToAccountSpinner.setOnItemSelectedListener(accountSelectedListener);

        mDirectionSwitch.addOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!mUpdatingSpinners) {
                    mDirectionUserOverridden = true;
                }
            }
        });

        mExtraSettingsLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mExtraSettingsExpanded = !mExtraSettingsExpanded;
                mExtraSettingsLayout.setVisibility(mExtraSettingsExpanded ? View.VISIBLE : View.GONE);
                mExtraSettingsLabel.setText(mExtraSettingsExpanded
                        ? R.string.label_extra_settings_expanded
                        : R.string.label_extra_settings_collapsed);
            }
        });

        mSavePresetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentAsPreset();
            }
        });

        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTransaction();
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void refreshPresetButtons() {
        mPresetButtonsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        List<TransactionPreset> presets = mPresetStore.loadAll();
        for (final TransactionPreset preset : presets) {
            Button button = (Button) inflater.inflate(R.layout.item_preset_button, mPresetButtonsContainer, false);
            String fromName = safeAccountName(preset.getFromAccountUID());
            String toName = safeAccountName(preset.getToAccountUID());
            button.setText(preset.getDisplayLabel(fromName, toName));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPreset(preset);
                }
            });
            button.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showPresetActions(preset);
                    return true;
                }
            });
            mPresetButtonsContainer.addView(button);
        }

        if (presets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.label_no_presets);
            empty.setPadding(8, 8, 8, 8);
            mPresetButtonsContainer.addView(empty);
        }
    }

    private String safeAccountName(String accountUID) {
        if (accountUID == null) {
            return "?";
        }
        try {
            return mAccountsDbAdapter.getAccountName(accountUID);
        } catch (IllegalArgumentException e) {
            return getString(R.string.label_account_deleted_short);
        }
    }

    private void applyPreset(TransactionPreset preset) {
        mEditingPresetId = preset.getId();
        mUpdatingSpinners = true;
        selectAccount(mFromAccountSpinner, mFromAdapter, preset.getFromAccountUID());
        if (mUseDoubleEntry) {
            refreshToAccountSpinner(preset.getFromAccountUID());
            selectAccount(mToAccountSpinner, mToAdapter, preset.getToAccountUID());
        }
        mUpdatingSpinners = false;

        if (preset.getAmount() != null) {
            mAmountEditText.setText(preset.getAmount());
        } else {
            mAmountEditText.setText("");
        }
        mLabelEditText.setText(preset.getLabel());
        mDescriptionEditText.setText(preset.getDescription());
        mNotesEditText.setText(preset.getNotes());

        if (preset.getDirectionOverride() != null) {
            AccountType fromType = getSelectedAccountType(mFromAccountSpinner);
            mDirectionSwitch.setAccountType(fromType);
            mDirectionSwitch.setChecked(preset.getDirectionOverride());
            mDirectionUserOverridden = true;
        } else {
            mDirectionUserOverridden = false;
            applyDirectionDefault();
        }

        if (!mExtraSettingsExpanded
                && (!preset.getLabel().isEmpty()
                || !preset.getDescription().isEmpty()
                || !preset.getNotes().isEmpty())) {
            mExtraSettingsLabel.performClick();
        }
    }

    private void showPresetActions(final TransactionPreset preset) {
        new AlertDialog.Builder(this)
                .setTitle(preset.getDisplayLabel(
                        safeAccountName(preset.getFromAccountUID()),
                        safeAccountName(preset.getToAccountUID())))
                .setItems(new CharSequence[]{
                        getString(R.string.btn_edit_preset),
                        getString(R.string.btn_delete_preset)
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            applyPreset(preset);
                        } else if (which == 1) {
                            mPresetStore.delete(preset.getId());
                            if (preset.getId().equals(mEditingPresetId)) {
                                mEditingPresetId = null;
                            }
                            refreshPresetButtons();
                            Toast.makeText(TransactionPresetOverlayActivity.this,
                                    R.string.toast_preset_deleted, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void applyDirectionDefault() {
        String fromUID = getSelectedAccountUID(mFromAccountSpinner);
        if (fromUID == null) {
            return;
        }
        AccountType fromType = mAccountsDbAdapter.getAccountType(fromUID);
        mDirectionSwitch.setAccountType(fromType);

        if (mDirectionUserOverridden) {
            return;
        }

        AccountType toType = null;
        if (mUseDoubleEntry) {
            String toUID = getSelectedAccountUID(mToAccountSpinner);
            if (toUID != null) {
                toType = mAccountsDbAdapter.getAccountType(toUID);
            }
        }

        TransactionType fallback = TransactionType.DEBIT;
        String typePref = PreferenceActivity.getActiveBookSharedPreferences()
                .getString(getString(R.string.key_default_transaction_type), "DEBIT");
        try {
            fallback = TransactionType.valueOf(typePref);
        } catch (IllegalArgumentException ignored) {
            // keep DEBIT
        }

        TransactionType type = TransactionDirectionUtils.defaultTypeForAccounts(fromType, toType, fallback);
        mUpdatingSpinners = true;
        mDirectionSwitch.setChecked(type);
        mUpdatingSpinners = false;
    }

    private void saveCurrentAsPreset() {
        String fromUID = getSelectedAccountUID(mFromAccountSpinner);
        String toUID = mUseDoubleEntry ? getSelectedAccountUID(mToAccountSpinner) : null;
        if (fromUID == null) {
            Toast.makeText(this, R.string.toast_select_from_account, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mUseDoubleEntry && toUID == null) {
            Toast.makeText(this, R.string.toast_select_to_account, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mUseDoubleEntry && fromUID.equals(toUID)) {
            Toast.makeText(this, R.string.toast_from_to_must_differ, Toast.LENGTH_SHORT).show();
            return;
        }

        TransactionPreset preset = mEditingPresetId != null
                ? mPresetStore.findById(mEditingPresetId)
                : null;
        if (preset == null) {
            preset = new TransactionPreset();
        }

        preset.setFromAccountUID(fromUID);
        preset.setToAccountUID(toUID);
        preset.setLabel(mLabelEditText.getText().toString().trim());
        preset.setDescription(mDescriptionEditText.getText().toString().trim());
        preset.setNotes(mNotesEditText.getText().toString().trim());

        String amountText = mAmountEditText.getText().toString().trim();
        if (amountText.isEmpty()) {
            preset.setAmount(null);
        } else {
            try {
                BigDecimal amount = AmountParser.parse(amountText);
                preset.setAmount(amount.abs().toPlainString());
            } catch (ParseException e) {
                Toast.makeText(this, R.string.toast_invalid_amount, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (mDirectionUserOverridden) {
            preset.setDirectionOverride(mDirectionSwitch.getTransactionType());
        } else {
            preset.setDirectionOverride(null);
        }

        if (mEditingPresetId != null && mPresetStore.findById(mEditingPresetId) != null) {
            mPresetStore.update(preset);
        } else {
            mPresetStore.add(preset);
            mEditingPresetId = preset.getId();
        }
        refreshPresetButtons();
        Toast.makeText(this, R.string.toast_preset_saved, Toast.LENGTH_SHORT).show();
    }

    private void saveTransaction() {
        String fromUID = getSelectedAccountUID(mFromAccountSpinner);
        if (fromUID == null) {
            Toast.makeText(this, R.string.toast_select_from_account, Toast.LENGTH_SHORT).show();
            return;
        }

        String amountText = mAmountEditText.getText().toString().trim();
        if (amountText.isEmpty()) {
            Toast.makeText(this, R.string.toast_enter_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        BigDecimal amount;
        try {
            amount = AmountParser.parse(amountText);
        } catch (ParseException e) {
            Toast.makeText(this, R.string.toast_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        String toUID;
        if (mUseDoubleEntry) {
            toUID = getSelectedAccountUID(mToAccountSpinner);
            if (toUID == null) {
                Toast.makeText(this, R.string.toast_select_to_account, Toast.LENGTH_SHORT).show();
                return;
            }
            if (fromUID.equals(toUID)) {
                Toast.makeText(this, R.string.toast_from_to_must_differ, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            Commodity commodity = mAccountsDbAdapter.getRecord(fromUID).getCommodity();
            toUID = mAccountsDbAdapter.getOrCreateImbalanceAccountUID(commodity);
        }

        Commodity commodity = mAccountsDbAdapter.getRecord(fromUID).getCommodity();
        String description = mDescriptionEditText.getText().toString().trim();
        if (description.isEmpty()) {
            description = mLabelEditText.getText().toString().trim();
        }
        String notes = mNotesEditText.getText().toString().trim();

        Transaction transaction = TransactionComposer.compose(
                description,
                notes,
                amount,
                commodity,
                fromUID,
                toUID,
                mDirectionSwitch.getTransactionType());

        mTransactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert);
        WidgetConfigurationActivity.updateAllWidgets(getApplicationContext());
        Toast.makeText(this, R.string.toast_transaction_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String getSelectedAccountUID(Spinner spinner) {
        long id = spinner.getSelectedItemId();
        if (id <= 0) {
            return null;
        }
        try {
            return mAccountsDbAdapter.getUID(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AccountType getSelectedAccountType(Spinner spinner) {
        String uid = getSelectedAccountUID(spinner);
        if (uid == null) {
            return AccountType.EXPENSE;
        }
        return mAccountsDbAdapter.getAccountType(uid);
    }

    private void selectAccount(Spinner spinner, QualifiedAccountNameCursorAdapter adapter, String accountUID) {
        if (accountUID == null || adapter == null) {
            return;
        }
        int position = adapter.getPosition(accountUID);
        if (position >= 0) {
            spinner.setSelection(position);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFromCursor != null) {
            mFromCursor.close();
        }
        if (mToCursor != null) {
            mToCursor.close();
        }
    }
}
