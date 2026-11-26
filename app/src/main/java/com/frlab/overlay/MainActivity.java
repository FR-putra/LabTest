package com.frlab.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.util.Log;
import android.util.TypedValue;
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
    private TextView gapValueText;
    private Switch masterSwitch;
    private Switch lockSwitch;
    private CheckBox cap80Checkbox;

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

        // Header
        TextView title = new TextView(this);
        title.setText("OverlayLab16 - Uji Android 16");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Uji Metode 1, 3, 4 + Opacity 0-200% + Double Floating Lyrics");
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        // Status Card
        LinearLayout statusCard = makeCard();
        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#E2E8F0"));
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        statusCard.addView(statusText);
        root.addView(statusCard);

        // Permission Card
        LinearLayout permCard = makeCard();
        permCard.addView(makeSectionTitle("1. Izin Sistem"));

        Button btnOverlayPerm = makeButton("Buka Izin Overlay (Metode 3 & 4)", "#1E293B");
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
                        "Jika terkunci: Info Aplikasi -> titik 3 -> Izinkan setelan terbatas",
                        Toast.LENGTH_LONG
                ).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        permCard.addView(btnAccPerm);
        root.addView(permCard);

        // Master Controls Card
        LinearLayout controlCard = makeCard();
        controlCard.addView(makeSectionTitle("2. Kontrol Utama"));

        masterSwitch = makeSwitch("Aktifkan Floating Lyrics", LabState.overlayEnabled);
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
                    Toast.makeText(MainActivity.this, "Aktifkan Layanan Aksesibilitas dulu!", Toast.LENGTH_SHORT).show();
                    return;
                }
                LabState.overlayEnabled = isChecked;
                startService(new Intent(MainActivity.this, OverlayLabService.class));
                LabState.notifyChanged();
            }
        });
        controlCard.addView(masterSwitch);

        lockSwitch = makeSwitch("Mode Lock (Tembus Klik ke Layar Bawah)", LabState.lockClickThrough);
        lockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LabState.lockClickThrough = isChecked;
                LabState.notifyChanged();
            }
        });
        controlCard.addView(lockSwitch);
        root.addView(controlCard);

        // Methods Card
        LinearLayout methodCard = makeCard();
        methodCard.addView(makeSectionTitle("3. Pilih Metode Tembus Klik"));

        RadioGroup methodGroup = new RadioGroup(this);
        RadioButton rb1 = makeRadio("Metode 1: TYPE_ACCESSIBILITY_OVERLAY (100% Solid)", 1);
        RadioButton rb3 = makeRadio("Metode 3: Dual-Stroke + Zero BG (Aman Play Store)", 3);
        RadioButton rb4 = makeRadio("Metode 4: TouchableRegion Reflection (Alpha 1.0)", 4);
        methodGroup.addView(rb1);
        methodGroup.addView(rb3);
        methodGroup.addView(rb4);
        methodGroup.check(LabState.activeMethod);

        methodGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                LabState.activeMethod = checkedId;
                if (LabState.overlayEnabled) {
                    startService(new Intent(MainActivity.this, OverlayLabService.class));
                }
                LabState.notifyChanged();
            }
        });
        methodCard.addView(methodGroup);

        cap80Checkbox = new CheckBox(this);
        cap80Checkbox.setText("Metode 3: Kunci Window Alpha max 0.80 saat Lock (Wajib agar tidak diblokir Android 16)");
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

        // Opacity 0-200% Card
        LinearLayout opacityCard = makeCard();
        opacityCard.addView(makeSectionTitle("4. Eksperimen Opacity (0% - 200% Overdrive)"));

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

        // Double Floating Lyrics Card
        LinearLayout doubleCard = makeCard();
        doubleCard.addView(makeSectionTitle("5. Eksperimen Double Floating Lyrics"));

        Switch doubleSwitch = makeSwitch("Aktifkan Double Floating Lyrics (2 Baris)", LabState.doubleLyrics);
        doubleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LabState.doubleLyrics = isChecked;
                LabState.notifyChanged();
            }
        });
        doubleCard.addView(doubleSwitch);

        gapValueText = new TextView(this);
        gapValueText.setTextColor(Color.parseColor("#C084FC"));
        gapValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        doubleCard.addView(gapValueText);

        SeekBar gapSeek = new SeekBar(this);
        gapSeek.setMax(36);
        gapSeek.setProgress(LabState.doubleGapDp);
        gapSeek.setPadding(0, dp(6), 0, dp(8));
        gapSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                LabState.doubleGapDp = progress;
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
        doubleCard.addView(gapSeek);

        Switch pillSwitch = makeSwitch("Tampilkan Badge Diagnostik di Overlay", LabState.showDiagnosticPill);
        pillSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LabState.showDiagnosticPill = isChecked;
                LabState.notifyChanged();
            }
        });
        doubleCard.addView(pillSwitch);
        root.addView(doubleCard);

        setContentView(scrollView);
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

    private void updateLabels() {
        boolean canOverlay = Settings.canDrawOverlays(this);
        String status = "Izin Overlay: " + (canOverlay ? "AKTIF" : "BELUM")
                + " | Aksesibilitas: " + (LabState.accessibilityConnected ? "TERHUBUNG" : "MATI")
                + "\nWindow Alpha dikirim ke OS: " + String.format("%.2f", LabState.lastAppliedWindowAlpha)
                + "\nStatus Metode 4: " + LabState.method4Diagnostic;
        statusText.setText(status);

        int op = LabState.opacityPercent;
        String modeDesc = op <= 100 ? "Standard Alpha" : "OVERDRIVE (Multi-Pass Glow + Luminance)";
        opacityValueText.setText("Opacity: " + op + "% (" + modeDesc + ")");

        gapValueText.setText("Jarak Vertikal Baris 1 & 2: " + LabState.doubleGapDp + " dp");
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
