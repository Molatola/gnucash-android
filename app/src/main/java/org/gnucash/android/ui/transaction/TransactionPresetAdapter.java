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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.gnucash.android.R;
import org.gnucash.android.model.TransactionPreset;

import java.util.List;

/**
 * List adapter for transaction presets with inline edit/delete actions.
 */
public class TransactionPresetAdapter extends ArrayAdapter<TransactionPreset> {

    /**
     * Callbacks for row actions and account-name resolution.
     */
    public interface Listener {
        /**
         * @param accountUID Account UID, or {@code null}
         * @return Display name for the account
         */
        @NonNull
        String getAccountDisplayName(@Nullable String accountUID);

        /**
         * Called when the edit affordance is tapped.
         * @param preset Preset to edit
         */
        void onEditPreset(@NonNull TransactionPreset preset);

        /**
         * Called when the delete affordance is tapped.
         * @param preset Preset to delete
         */
        void onDeletePreset(@NonNull TransactionPreset preset);
    }

    @NonNull
    private final Listener mListener;

    public TransactionPresetAdapter(@NonNull Context context,
                                    @NonNull List<TransactionPreset> presets,
                                    @NonNull Listener listener) {
        super(context, 0, presets);
        mListener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_preset_dropdown, parent, false);
        }

        TextView presetText = convertView.findViewById(R.id.preset_text);
        View editBtn = convertView.findViewById(R.id.btn_edit_preset);
        View deleteBtn = convertView.findViewById(R.id.btn_delete_preset);

        // Clear recycled listeners before rebinding
        editBtn.setOnClickListener(null);
        deleteBtn.setOnClickListener(null);

        final TransactionPreset preset = getItem(position);
        if (preset == null) {
            presetText.setText("");
            editBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);
            return convertView;
        }

        String fromName = mListener.getAccountDisplayName(preset.getFromAccountUID());
        String toName = mListener.getAccountDisplayName(preset.getToAccountUID());
        presetText.setText(preset.getDisplayLabel(fromName, toName));

        editBtn.setVisibility(View.VISIBLE);
        deleteBtn.setVisibility(View.VISIBLE);
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onEditPreset(preset);
            }
        });
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onDeletePreset(preset);
            }
        });

        return convertView;
    }
}
