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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.ListPopupWindow;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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
 * Dialog-style overlay for quick transaction entry with customizable presets.
 * <p>Presets are stored in SharedPreferences — not in the database.</p>
 */
public class TransactionPresetOverlayActivity extends PasscodeLockActivity
        implements TransactionPresetAdapter.Listener {

    private static final String ACCOUNT_CONDITIONS =
            DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0";

    private static final String STATE_EDITING_PRESET_ID = "editing_preset_id";
    private static final String STATE_DIRECTION_OVERRIDDEN = "direction_overridden";
    private static final String STATE_DIRECTION_TYPE = "direction_type";
    private static final String STATE_EXTRA_EXPANDED = "extra_expanded";
    private static final String STATE_AMOUNT = "amount";
    private static final String STATE_LABEL = "label";
    private static final String STATE_DESCRIPTION = "description";
    private static final String STATE_NOTES = "notes";
    private static final String STATE_FROM_UID = "from_uid";
    private static final String STATE_TO_UID = "to_uid";

    @BindView(R.id.preset_selector) TextView mPresetSelector;
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
    private boolean mSaving;
    private String mEditingPresetId;
    private TransactionPresetAdapter mPresetAdapter;
    private ListPopupWindow mPresetPopup;

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
        refreshPresets();
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            applyDefaultTransferAccount(getSelectedAccountUID(mFromAccountSpinner));
            applyDirectionDefault();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_EDITING_PRESET_ID, mEditingPresetId);
        outState.putBoolean(STATE_DIRECTION_OVERRIDDEN, mDirectionUserOverridden);
        outState.putString(STATE_DIRECTION_TYPE, mDirectionSwitch.getTransactionType().name());
        outState.putBoolean(STATE_EXTRA_EXPANDED, mExtraSettingsExpanded);
        outState.putString(STATE_AMOUNT, mAmountEditText.getText().toString());
        outState.putString(STATE_LABEL, mLabelEditText.getText().toString());
        outState.putString(STATE_DESCRIPTION, mDescriptionEditText.getText().toString());
        outState.putString(STATE_NOTES, mNotesEditText.getText().toString());
        outState.putString(STATE_FROM_UID, getSelectedAccountUID(mFromAccountSpinner));
        if (mUseDoubleEntry) {
            outState.putString(STATE_TO_UID, getSelectedAccountUID(mToAccountSpinner));
        }
    }

    private void restoreState(Bundle state) {
        String fromUID = state.getString(STATE_FROM_UID);
        mUpdatingSpinners = true;
        selectAccount(mFromAccountSpinner, mFromAdapter, fromUID);
        if (mUseDoubleEntry) {
            refreshToAccountSpinner(fromUID);
            selectAccount(mToAccountSpinner, mToAdapter, state.getString(STATE_TO_UID));
        }
        mUpdatingSpinners = false;

        mAmountEditText.setText(state.getString(STATE_AMOUNT, ""));
        mLabelEditText.setText(state.getString(STATE_LABEL, ""));
        mDescriptionEditText.setText(state.getString(STATE_DESCRIPTION, ""));
        mNotesEditText.setText(state.getString(STATE_NOTES, ""));
        mEditingPresetId = state.getString(STATE_EDITING_PRESET_ID);

        mExtraSettingsExpanded = state.getBoolean(STATE_EXTRA_EXPANDED, false);
        mExtraSettingsLayout.setVisibility(mExtraSettingsExpanded ? View.VISIBLE : View.GONE);
        mExtraSettingsLabel.setText(mExtraSettingsExpanded
                ? R.string.label_extra_settings_expanded
                : R.string.label_extra_settings_collapsed);

        mDirectionUserOverridden = state.getBoolean(STATE_DIRECTION_OVERRIDDEN, false);
        mDirectionSwitch.setAccountType(getSelectedAccountType(mFromAccountSpinner));
        if (mDirectionUserOverridden) {
            String typeName = state.getString(STATE_DIRECTION_TYPE);
            if (typeName != null) {
                try {
                    mUpdatingSpinners = true;
                    mDirectionSwitch.setChecked(TransactionType.valueOf(typeName));
                } catch (IllegalArgumentException ignored) {
                    // unknown stored type; leave switch as-is
                } finally {
                    mUpdatingSpinners = false;
                }
            }
        } else {
            applyDirectionDefault();
        }
        updateDirectionColor(mDirectionSwitch.isChecked());
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
                    } else {
                        applyDefaultTransferAccount(fromUID);
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
                updateDirectionColor(isChecked);
            }
        });

        mPresetSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPresetPopup();
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

    private void updateDirectionColor(boolean isChecked) {
        int color = android.support.v4.content.ContextCompat.getColor(
                this, isChecked ? R.color.debit_red : R.color.credit_green);
        mDirectionSwitch.setTextColor(color);
        mAmountEditText.setTextColor(color);
    }

    private void refreshPresets() {
        List<TransactionPreset> presets = mPresetStore.loadAll();
        mLabelEditText.setHint(generateDefaultPresetLabel(presets));
        mPresetAdapter = new TransactionPresetAdapter(this, presets, this);
        if (presets.isEmpty()) {
            mPresetSelector.setText(R.string.label_no_presets);
            mPresetSelector.setEnabled(false);
        } else {
            mPresetSelector.setText(R.string.label_select_preset);
            mPresetSelector.setEnabled(true);
        }
        if (mPresetPopup != null && mPresetPopup.isShowing()) {
            mPresetPopup.setAdapter(mPresetAdapter);
        }
    }

    private void showPresetPopup() {
        if (mPresetAdapter == null || mPresetAdapter.getCount() == 0) {
            return;
        }
        if (mPresetPopup == null) {
            mPresetPopup = new ListPopupWindow(this);
            mPresetPopup.setAnchorView(mPresetSelector);
            mPresetPopup.setModal(true);
            mPresetPopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    TransactionPreset preset = mPresetAdapter.getItem(position);
                    dismissPresetPopup();
                    if (preset != null) {
                        fillFromPreset(preset);
                    }
                }
            });
        }
        mPresetPopup.setAdapter(mPresetAdapter);
        mPresetPopup.setWidth(Math.max(mPresetSelector.getWidth(), mPresetSelector.getMeasuredWidth()));
        mPresetPopup.show();
    }

    private void dismissPresetPopup() {
        if (mPresetPopup != null && mPresetPopup.isShowing()) {
            mPresetPopup.dismiss();
        }
    }

    @Override
    @NonNull
    public String getAccountDisplayName(@Nullable String accountUID) {
        return safeAccountName(accountUID);
    }

    @Override
    public void onEditPreset(@NonNull TransactionPreset preset) {
        dismissPresetPopup();
        editPreset(preset);
    }

    @Override
    public void onDeletePreset(@NonNull TransactionPreset preset) {
        confirmDeletePreset(preset);
    }

    private void confirmDeletePreset(final TransactionPreset preset) {
        dismissPresetPopup();
        String label = preset.getDisplayLabel(
                safeAccountName(preset.getFromAccountUID()),
                safeAccountName(preset.getToAccountUID()));
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_confirm_delete)
                .setMessage(getString(R.string.msg_delete_preset_confirmation, label))
                .setPositiveButton(R.string.alert_dialog_ok_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mPresetStore.delete(preset.getId())) {
                            if (preset.getId().equals(mEditingPresetId)) {
                                mEditingPresetId = null;
                            }
                            Toast.makeText(TransactionPresetOverlayActivity.this,
                                    R.string.toast_preset_deleted, Toast.LENGTH_SHORT).show();
                            refreshPresets();
                        } else {
                            Toast.makeText(TransactionPresetOverlayActivity.this,
                                    R.string.toast_preset_delete_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.alert_dialog_cancel, null)
                .show();
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

    private int amountFractionDigits(String accountUID) {
        if (accountUID != null) {
            try {
                return mAccountsDbAdapter.getRecord(accountUID).getCommodity().getSmallestFractionDigits();
            } catch (IllegalArgumentException ignored) {
                // account missing; fall back to a sensible default
            }
        }
        return 2;
    }

    /**
     * Fills the form from a preset without arming edit mode: a later
     * "Save as preset" creates a new preset instead of overwriting this one.
     */
    private void fillFromPreset(TransactionPreset preset) {
        mEditingPresetId = null;
        populateFromPreset(preset);
    }

    /**
     * Fills the form from a preset and arms edit mode so "Save as preset"
     * updates this preset in place.
     */
    private void editPreset(TransactionPreset preset) {
        mEditingPresetId = preset.getId();
        populateFromPreset(preset);
    }

    private void populateFromPreset(TransactionPreset preset) {
        mUpdatingSpinners = true;
        boolean fromFound = selectAccount(mFromAccountSpinner, mFromAdapter, preset.getFromAccountUID());
        boolean toFound = true;
        if (mUseDoubleEntry) {
            refreshToAccountSpinner(preset.getFromAccountUID());
            toFound = selectAccount(mToAccountSpinner, mToAdapter, preset.getToAccountUID());
        }
        mUpdatingSpinners = false;

        boolean fromMissing = preset.getFromAccountUID() != null && !fromFound;
        boolean toMissing = mUseDoubleEntry && preset.getToAccountUID() != null && !toFound;
        if (fromMissing || toMissing) {
            Toast.makeText(this, R.string.toast_preset_account_missing, Toast.LENGTH_LONG).show();
        }

        if (preset.getAmount() != null) {
            try {
                BigDecimal stored = new BigDecimal(preset.getAmount());
                mAmountEditText.setText(
                        AmountParser.format(stored, amountFractionDigits(preset.getFromAccountUID())));
            } catch (NumberFormatException e) {
                mAmountEditText.setText("");
            }
        } else {
            mAmountEditText.setText("");
        }
        mLabelEditText.setText(preset.getLabel());
        mDescriptionEditText.setText(preset.getDescription());
        mNotesEditText.setText(preset.getNotes());

        if (fromMissing || toMissing) {
            // don't apply the preset's direction against a stale spinner selection;
            // the toast above asks the user to re-choose accounts
        } else if (preset.getDirectionOverride() != null) {
            AccountType fromType = getSelectedAccountType(mFromAccountSpinner);
            mDirectionSwitch.setAccountType(fromType);
            mDirectionSwitch.setChecked(preset.getDirectionOverride());
            updateDirectionColor(mDirectionSwitch.isChecked());
            mDirectionUserOverridden = true;
        } else {
            mDirectionUserOverridden = false;
            applyDirectionDefault();
        }

        // Label lives outside Extra settings; expand only when those fields have content.
        if (!mExtraSettingsExpanded
                && (!preset.getDescription().isEmpty()
                || !preset.getNotes().isEmpty())) {
            mExtraSettingsLabel.performClick();
        }

        mPresetSelector.setText(preset.getDisplayLabel(
                safeAccountName(preset.getFromAccountUID()),
                safeAccountName(preset.getToAccountUID())));
    }

    /**
     * Preselects the "to" account from the from-account's default transfer account,
     * walking up the parent chain like the full transaction form does.
     */
    private void applyDefaultTransferAccount(String fromUID) {
        if (!mUseDoubleEntry || fromUID == null) {
            return;
        }
        try {
            long defaultTransferId = mAccountsDbAdapter.findInheritedDefaultTransferAccountId(fromUID);
            if (defaultTransferId > 0) {
                mUpdatingSpinners = true;
                selectAccount(mToAccountSpinner, mToAdapter, mAccountsDbAdapter.getUID(defaultTransferId));
                mUpdatingSpinners = false;
            }
        } catch (IllegalArgumentException ignored) {
            // account chain changed underfoot; keep the current selection
        }
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
        updateDirectionColor(mDirectionSwitch.isChecked());
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
        boolean isEditingExisting = preset != null;
        if (preset == null) {
            preset = new TransactionPreset();
        }

        preset.setFromAccountUID(fromUID);
        preset.setToAccountUID(toUID);
        String labelText = mLabelEditText.getText().toString().trim();
        if (labelText.isEmpty()) {
            labelText = generateDefaultPresetLabel();
        }
        preset.setLabel(labelText);
        preset.setDescription(mDescriptionEditText.getText().toString().trim());
        preset.setNotes(mNotesEditText.getText().toString().trim());

        String amountText = mAmountEditText.getText().toString().trim();
        if (amountText.isEmpty()) {
            preset.setAmount(null);
        } else {
            try {
                BigDecimal amount = AmountParser.parseStrict(amountText);
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

        boolean saved = isEditingExisting ? mPresetStore.update(preset) : mPresetStore.add(preset);
        if (!saved) {
            Toast.makeText(this, R.string.toast_preset_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        refreshPresets();
        mPresetSelector.setText(preset.getDisplayLabel(
                safeAccountName(preset.getFromAccountUID()),
                safeAccountName(preset.getToAccountUID())));
        Toast.makeText(this, R.string.toast_preset_saved, Toast.LENGTH_SHORT).show();
    }

    private String generateDefaultPresetLabel() {
        return generateDefaultPresetLabel(mPresetStore.loadAll());
    }

    private String generateDefaultPresetLabel(List<TransactionPreset> presets) {
        String prefix = getString(R.string.title_quick_transaction) + " ";
        int maxNumber = 0;
        for (TransactionPreset p : presets) {
            String label = p.getLabel();
            if (label != null && label.startsWith(prefix)) {
                try {
                    int num = Integer.parseInt(label.substring(prefix.length()).trim());
                    if (num > maxNumber) {
                        maxNumber = num;
                    }
                } catch (NumberFormatException e) {
                    // ignore non-numeric suffixes
                }
            }
        }
        return prefix + (maxNumber + 1);
    }

    private void saveTransaction() {
        if (mSaving) {
            return;
        }
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
            amount = AmountParser.parseStrict(amountText);
        } catch (ParseException e) {
            Toast.makeText(this, R.string.toast_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            Toast.makeText(this, R.string.toast_enter_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        Commodity commodity;
        try {
            commodity = mAccountsDbAdapter.getRecord(fromUID).getCommodity();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.toast_account_no_longer_exists, Toast.LENGTH_LONG).show();
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
            Commodity toCommodity;
            try {
                toCommodity = mAccountsDbAdapter.getRecord(toUID).getCommodity();
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, R.string.toast_account_no_longer_exists, Toast.LENGTH_LONG).show();
                return;
            }
            if (!commodity.equals(toCommodity)) {
                Toast.makeText(this, R.string.toast_quick_tx_multi_currency, Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            toUID = mAccountsDbAdapter.getOrCreateImbalanceAccountUID(commodity);
        }

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

        mSaving = true;
        mSaveButton.setEnabled(false);
        mTransactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert);
        WidgetConfigurationActivity.updateAllWidgets(getApplicationContext());
        Toast.makeText(this, R.string.toast_transaction_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String getSelectedAccountUID(Spinner spinner) {
        long id = spinner.getSelectedItemId();
        if (id == AdapterView.INVALID_ROW_ID) {
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

    private boolean selectAccount(Spinner spinner, QualifiedAccountNameCursorAdapter adapter, String accountUID) {
        if (accountUID == null || adapter == null) {
            return false;
        }
        int position = adapter.getPosition(accountUID);
        if (position >= 0) {
            spinner.setSelection(position);
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        dismissPresetPopup();
        super.onDestroy();
        if (mFromCursor != null) {
            mFromCursor.close();
        }
        if (mToCursor != null) {
            mToCursor.close();
        }
    }
}
