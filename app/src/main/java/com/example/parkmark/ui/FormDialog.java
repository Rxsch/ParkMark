package com.example.parkmark.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

// Build accessible, scrollable forms shared by profile, parking, timer, and permit dialogs.
public final class FormDialog {
    public final LinearLayout content;
    private final ScrollView scroll;
    private final Context context;

    public FormDialog(Context context) {
        this.context = context;
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        scroll = new ScrollView(context);
        scroll.addView(content);
    }

    public EditText field(String label, String value, int inputType, int maxLength) {
        TextView title = new TextView(context);
        title.setText(label);
        EditText input = new EditText(context);
        input.setId(View.generateViewId());
        title.setLabelFor(input.getId());
        input.setInputType(inputType);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxLength)});
        content.addView(title);
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    public EditText text(String label, String value) {
        return field(label, value, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 250);
    }

    // Run validation before dismissing; an invalid form remains open with its field error.
    public AlertDialog show(String title, String action, java.util.function.BooleanSupplier submit) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle(title).setView(scroll)
                .setNegativeButton("Cancel", null).setPositiveButton(action, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> { if (submit.getAsBoolean()) dialog.dismiss(); }));
        dialog.show();
        return dialog;
    }
}
