package com.frlab.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements LabState.Listener {

    private TextView statusText;
    private TextView opacityValueText;
    private TextView layersValueText;
    private TextView offsetValueText;
    private SeekBar layersSeek;
    private final Button[] quickLayerButtons = new Button[10];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, final Throwable e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showCrashScreen(e);
                    }
                });
            }
        });

        try {
            buildMainUi();
            requestNotificationPermissionIfNeeded();
            updateLabels();
        } catch (Throwable t) {
            showCrashScreen(t);
        }
    }

    private void buildMainUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#090D16"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(24), pad, dp(36));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("OverlayLab16 - 1xWIN Multi-Pass");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("1 Jendela Overlay + Penggandaan Cahaya 1–10 Pass (Tembus Beranda)");
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        LinearLayout statusCard = makeCard();
        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#E2E8F0"));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        statusCard.addView(statusText);
        root.addView(statusCard);

        LinearLayout permCard = makeCard();
        permCard.addView(makeSectionTitle("1. Izin Sistem"));

        Button btnOverlayPerm = makeButton("Buka Izin Overlay (Metode 3)", "#1E293B");
        btnOverlayPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            }
        });
        permCard.addView(btnOverlayPerm);

        Button btnAccPerm = makeButton("Buka Izin Aksesibilitas (Metode 1)", "#1E293B");
        btnAccPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(
                        MainActivity.this,
                        "Aktifkan OverlayLab16 Accessibility Overlay",
                        Toast.LENGTH_LONG
                ).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        permCard.addView(btnAccPerm);
        root.addView(permCard);

        LinearLayout controlCard = makeCard();
        controlCard.addView(makeSectionTitle("2. Kontrol Utama"));

        Switch masterSwitch = makeSwitch("Aktifkan Floating Lyrics", LabState.overlayEnabled);
        masterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && LabState.activeMethod != LabState.METHOD_1_ACCESSIBILITY && !Settings.canDrawOverlays(MainActivity.this)) {
                    buttonView.setChecked(false);
                    Toast.makeText(MainActivity.this, "Aktifkan izin Overlay dulu!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isChecked && LabState.activeMethod == LabState.METHOD_1_ACCESSIBILITY && !LabState.accessibilityConnected) {
                    buttonView.setChecked(false);
                    Toast.makeText(MainActivity.this, "Matikan lalu nyalakan ulang layanan Aksesibilitas di Settings!", Toast.LENGTH_LONG).show();
                    return;
                }
                LabState.overlayEnabled = isChecked;
                if (isChecked) {
                    LabState.syncEpochMs = System.currentTimeMillis();
                }
                if (LabState.activeMethod != LabState.METHOD_1_ACCESSIBILITY) {
                    startService(new Intent(MainActivity.this, OverlayLabService.class));
                }
                LabState.notifyChanged();
            }
        });
        controlCard.addView(masterSwitch);

        Switch lockSwitch = makeSwitch("Mode Lock (Tembus Klik ke Beranda & App Lain)", LabState.lockClickThrough);
        lockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LabState.lockClickThrough = isChecked;
                LabState.notifyChanged();
            }
        });
        controlCard.addView(lockSwitch);
        root.addView(controlCard);

        LinearLayout stackCard = makeCard();
        stackCard.addView(makeSectionTitle("3. Penggandaan Cahaya & Ketebalan (1 – 10 Pass)"));

        layersValueText = new TextView(this);
        layersValueText.setTextColor(Color.parseColor("#34D399"));
        layersValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        layersValueText.setTypeface(null, Typeface.BOLD);
        stackCard.addView(layersValueText);

        LinearLayout sliderRow = new LinearLayout(this);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        sliderRow.setPadding(0, dp(8), 0, dp(8));

        Button btnMinus = makeCompactButton("-", "#1E293B");
        btnMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setStackLayers(LabState.stackLayers - 1);
            }
        });
        sliderRow.addView(btnMinus);

        layersSeek = new SeekBar(this);
        layersSeek.setMax(9);
        layersSeek.setProgress(LabState.stackLayers - 1);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.leftMargin = dp(8);
        seekLp.rightMargin = dp(8);
        layersSeek.setLayoutParams(seekLp);
        layersSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setStackLayers(progress + 1);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sliderRow.addView(layersSeek);

        Button btnPlus = makeCompactButton("+", "#1E293B");
        btnPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setStackLayers(LabState.stackLayers + 1);
            }
        });
        sliderRow.addView(btnPlus);
        stackCard.addView(sliderRow);

        LinearLayout row1to5 = new LinearLayout(this);
        row1to5.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= 5; i++) {
            row1to5.addView(createQuickLayerBtn(i));
        }
        stackCard.addView(row1to5);

        LinearLayout row6to10 = new LinearLayout(this);
        row6to10.setOrientation(LinearLayout.HORIZONTAL);
        row6to10.setPadding(0, dp(6), 0, dp(8));
        for (int i = 6; i <= 10; i++) {
            row6to10.addView(createQuickLayerBtn(i));
        }
        stackCard.addView(row6to10);

        offsetValueText = new TextView(this);
        offsetValueText.setTextColor(Color.parseColor("#C084FC"));
        offsetValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        stackCard.addView(offsetValueText);

        SeekBar offsetSeek = new SeekBar(this);
        offsetSeek.setMax(16);
        offsetSeek.setProgress(LabState.doubleOffsetDp);
        offsetSeek.setPadding(0, dp(6), 0, dp(8));
        offsetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                LabState.doubleOffsetDp = progress;
                updateLabels();
                LabState.notifyChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        stackCard.addView(offsetSeek);

        Switch pillSwitch = makeSwitch("Tampilkan Badge Diagnostik di Overlay", LabState.showDiagnosticPill);
        pillSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LabState.showDiagnosticPill = isChecked;
                LabState.notifyChanged();
            }
        });
        stackCard.addView(pillSwitch);
        root.addView(stackCard);

        LinearLayout methodCard = makeCard();
        methodCard.addView(makeSectionTitle("4. Pilih Metode Tembus Klik"));

        RadioGroup methodGroup = new RadioGroup(this);
        RadioButton rb1 = makeRadio("Metode 1: TYPE_ACCESSIBILITY_OVERLAY (100% Solid)", 1);
        RadioButton rb3 = makeRadio("Metode 3: 1xWIN Dual-Stroke + Multi-Pass (Aman Play Store)", 3);
        methodGroup.addView(rb1);
        methodGroup.addView(rb3);
        methodGroup.check(LabState.activeMethod);

        methodGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                LabState.activeMethod = checkedId;
                if (LabState.overlayEnabled && checkedId != LabState.METHOD_1_ACCESSIBILITY) {
                    startService(new Intent(MainActivity.this, OverlayLabService.class));
                }
                LabState.notifyChanged();
            }
        });
        methodCard.addView(methodGroup);

        CheckBox cap80Checkbox = new CheckBox(this);
        cap80Checkbox.setText("Metode 3: Kunci Window Alpha max 0.80 saat Lock (Wajib agar tembus beranda)");
        cap80Checkbox.setTextColor(Color.parseColor("#CBD5E1"));
        cap80Checkbox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        cap80Checkbox.setChecked(LabState.capAlpha80InMethod3);
        cap80Checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LabState.capAlpha80InMethod3 = isChecked;
                LabState.notifyChanged();
            }
        });
        methodCard.addView(cap80Checkbox);
        root.addView(methodCard);

        LinearLayout opacityCard = makeCard();
        opacityCard.addView(makeSectionTitle("5. Eksperimen Cahaya & Opacity (0% – 200%)"));

        opacityValueText = new TextView(this);
        opacityValueText.setTextColor(Color.parseColor("#38BDF8"));
        opacityValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        opacityValueText.setTypeface(null, Typeface.BOLD);
        opacityCard.addView(opacityValueText);

        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(200);
        opacitySeek.setProgress(LabState.opacityPercent);
        opacitySeek.setPadding(0, dp(8), 0, dp(4));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                LabState.opacityPercent = progress;
                updateLabels();
                LabState.notifyChanged();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        opacityCard.addView(opacitySeek);
        root.addView(opacityCard);

        setContentView(scrollView);
    }

    private void setStackLayers(int target) {
        int clamped = Math.min(10, Math.max(1, target));
        LabState.stackLayers = clamped;
        if (layersSeek != null && layersSeek.getProgress() != clamped - 1) {
            layersSeek.setProgress(clamped - 1);
        }
        updateLabels();
        LabState.notifyChanged();
    }

    private Button createQuickLayerBtn(final int layerNum) {
        Button btn = new Button(this);
        btn.setText(String.valueOf(layerNum));
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setPadding(0, dp(6), 0, dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        int margin = dp(3);
        lp.setMargins(margin, 0, margin, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setStackLayers(layerNum);
            }
        });

        quickLayerButtons[layerNum - 1] = btn;
        return btn;
    }

    private Button makeCompactButton(String label, String hexColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        btn.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dp(8));
        btn.setBackground(bg);
        btn.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(38)));
        return btn;
    }

    private void showCrashScreen(final Throwable t) {
        final String stack = Log.getStackTraceString(t);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#1A0505"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, dp(28), p, dp(28));

        TextView header = new TextView(this);
        header.setText("CRASH TERDETEKSI (Copy & Kirim Error Ini)");
        header.setTextColor(Color.parseColor("#FCA5A5"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setTypeface(null, Typeface.BOLD);
        box.addView(header);

        Button copyBtn = makeButton("Copy Pesan Error", "#DC2626");
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("CrashLog", stack));
                    Toast.makeText(MainActivity.this, "Error disalin ke clipboard!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(copyBtn);

        TextView body = new TextView(this);
        body.setText(stack);
        body.setTextColor(Color.WHITE);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        body.setTextIsSelectable(true);
        box.addView(body);

        sv.addView(box);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LabState.addListener(this);
        if (statusText != null) {
            updateLabels();
        }
    }

    @Override
    protected void onPause() {
        LabState.removeListener(this);
        super.onPause();
    }

    @Override
    public void onStateChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusText != null) {
                    updateLabels();
                }
            }
        });
    }

    private boolean isAccessibilityEnabledInSettings() {
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled != 1) {
                return false;
            }
            String services = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(services)) {
                return false;
            }
            ComponentName expected = new ComponentName(this, LabAccessibilityService.class);
            String full = expected.flattenToString();
            String shortName = expected.flattenToShortString();
            return services.contains(full) || services.contains(shortName);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateLabels() {
        boolean canOverlay = Settings.canDrawOverlays(this);
        String accStatus;
        if (LabState.accessibilityConnected) {
            accStatus = "TERHUBUNG";
        } else if (isAccessibilityEnabledInSettings()) {
            accStatus = "TERDAFTAR (Toggle ulang di Settings)";
        } else {
            accStatus = "MATI";
        }

        int passes = LabState.stackLayers;
        String status = "Izin Overlay: " + (canOverlay ? "AKTIF" : "BELUM")
                + " | Aksesibilitas: " + accStatus
                + "\nJendela OS: " + LabState.activeWindowCount + "xWIN | Internal Pass: " + passes + "x | WinAlpha: " + String.format("%.2f", LabState.lastAppliedWindowAlpha);
        statusText.setText(status);

        layersValueText.setText("Penggandaan Dalam 1 Canvas: " + passes + "x Pass");

        for (int i = 0; i < quickLayerButtons.length; i++) {
            Button btn = quickLayerButtons[i];
            if (btn != null) {
                GradientDrawable bg = new GradientDrawable();
                boolean selected = (i + 1) == passes;
                bg.setColor(Color.parseColor(selected ? "#059669" : "#1E293B"));
                bg.setCornerRadius(dp(8));
                if (selected) {
                    bg.setStroke(dp(1), Color.parseColor("#6EE7B7"));
                }
                btn.setBackground(bg);
            }
        }

        String offsetDesc = LabState.doubleOffsetDp == 0 ? "0 dp (Tepat Ditimpa 100%)" : LabState.doubleOffsetDp + " dp per pass (Efek 3D/Bayangan)";
        offsetValueText.setText("Geser Jarak Antar Pass: " + offsetDesc);

        int op = LabState.opacityPercent;
        String modeDesc = op <= 100 ? "Standard Alpha" : "SUPER BRIGHT OVERDRIVE";
        opacityValueText.setText("Cahaya & Opacity: " + op + "% (" + modeDesc + ")");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#111827"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#1F2937"));
        card.setBackground(bg);
        int p = dp(14);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#F8FAFC"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private Button makeButton(String text, String hexColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private Switch makeSwitch(String label, boolean initial) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.parseColor("#E2E8F0"));
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        sw.setChecked(initial);
        sw.setPadding(0, dp(6), 0, dp(6));
        return sw;
    }

    private RadioButton makeRadio(String label, int id) {
        RadioButton rb = new RadioButton(this);
        rb.setId(id);
        rb.setText(label);
        rb.setTextColor(Color.parseColor("#E2E8F0"));
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        rb.setPadding(dp(4), dp(6), 0, dp(6));
        return rb;
    }

    private int dp(int val) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics()
        ));
    }
}
