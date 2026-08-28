package com.mobile.novabox.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.mobile.novabox.R;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        View btnClose = findViewById(R.id.btnAboutClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }
}