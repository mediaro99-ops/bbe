package ro.plesiarazvan.profitride;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 2001;
    private static final int REQ_NOTIFY = 2002;
    private static final int PENDING_NONE = 0;
    private static final int PENDING_OVERLAY_ONLY = 1;
    private static final int PENDING_START_ANALYSIS = 2;

    private final int BG = Color.rgb(7, 12, 16);
    private final int CARD = Color.rgb(18, 28, 35);
    private final int FIELD = Color.rgb(28, 41, 50);
    private final int MUTED = Color.rgb(165, 178, 188);
    private final int GREEN = Color.rgb(40, 220, 145);
    private final int YELLOW = Color.rgb(255, 196, 22);
    private final int RED = Color.rgb(255, 83, 83);

    private EditText minRonKm, minRonHour, minProfit;
    private EditText liters100, ronLiter, manualCost, maintenanceKm;
    private EditText monthlyFixed, weeklyHours, weeklyKm, targetMonthly;
    private Switch voiceSwitch;
    private TextView costLabel;
    private SharedPreferences prefs;
    private MediaProjectionManager projectionManager;
    private int pendingOverlayAction = PENDING_NONE;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("profitride", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());
        maybeAskNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        if (pendingOverlayAction != PENDING_NONE && Settings.canDrawOverlays(this)) {
            int action = pendingOverlayAction;
            pendingOverlayAction = PENDING_NONE;
            if (action == PENDING_START_ANALYSIS) requestScreenCapture();
            else startOverlayService();
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.profitride_logo);
        header.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(text("ProfitRide", 30, Color.WHITE, true));
        brand.addView(text("asistent profit șofer", 14, MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        TextView intro = text("Configurezi o dată. După aceea, aplicația citește ofertele și calculează automat.", 15, MUTED, false);
        intro.setPadding(0, dp(12), 0, dp(16));
        root.addView(intro);

        root.addView(section("1. Costurile tale"));
        LinearLayout costs = card();
        costs.addView(label("Costuri fixe lunare"));
        monthlyFixed = field("ex. 1250", pref("monthlyFixed", 0f));
        costs.addView(fieldRow("Rate + asigurări + telefon + alte costuri", monthlyFixed, "RON/lună"));

        costs.addView(label("Combustibil / energie"));
        ronLiter = field("ex. 4,66", pref("ronLiter", 4.66f));
        costs.addView(fieldRow("Preț combustibil", ronLiter, "RON/L"));

        liters100 = field("ex. 10,0", pref("liters100", 10f));
        LinearLayout litersRow = new LinearLayout(this);
        litersRow.setOrientation(LinearLayout.HORIZONTAL);
        Button minus = button("−", FIELD, Color.WHITE);
        Button plus = button("+", FIELD, Color.WHITE);
        LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(dp(52), dp(52));
        litersRow.addView(minus, small);
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(0, dp(52), 1f);
        mid.setMargins(dp(8), 0, dp(8), 0);
        litersRow.addView(liters100, mid);
        litersRow.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        costs.addView(text("Consum mediu (L/100 km)", 12, MUTED, false));
        costs.addView(litersRow);

        manualCost = field("0", pref("manualCost", 0f));
        costs.addView(fieldRow("Cost manual / km (opțional, inclusiv EV)", manualCost, "RON/km"));

        maintenanceKm = field("ex. 0,25", pref("maintenanceKm", 0.25f));
        costs.addView(fieldRow("Mentenanță estimată / km", maintenanceKm, "RON/km"));

        costLabel = text("", 14, GREEN, true);
        costLabel.setPadding(0, dp(10), 0, dp(4));
        costs.addView(costLabel);
        root.addView(costs);

        root.addView(section("2. Programul tău"));
        LinearLayout program = card();
        weeklyHours = field("40", pref("weeklyHours", 40f));
        weeklyKm = field("800", pref("weeklyKm", 800f));
        program.addView(fieldRow("Ore pe săptămână", weeklyHours, "ore"));
        program.addView(fieldRow("Kilometri pe săptămână", weeklyKm, "km"));
        root.addView(program);

        root.addView(section("3. Ținta și pragurile tale"));
        LinearLayout goals = card();
        targetMonthly = field("5000", pref("targetMonthly", 5000f));
        goals.addView(fieldRow("Profit lunar dorit", targetMonthly, "RON"));

        minRonKm = field("2,00", pref("minRonKm", 2f));
        minRonHour = field("50", pref("minRonHour", 50f));
        minProfit = field("5", pref("minProfit", 5f));
        goals.addView(fieldRow("Prag minim NET / km", minRonKm, "RON/km"));
        goals.addView(fieldRow("Prag minim NET / oră", minRonHour, "RON/oră"));
        goals.addView(fieldRow("Profit minim / cursă", minProfit, "RON"));
        root.addView(goals);

        root.addView(section("4. Voce și automatizare"));
        LinearLayout auto = card();
        voiceSwitch = new Switch(this);
        voiceSwitch.setText("Spune cu voce doar suma ofertei");
        voiceSwitch.setTextColor(Color.WHITE);
        voiceSwitch.setTextSize(15);
        voiceSwitch.setChecked(prefs.getBoolean("voiceEnabled", true));
        auto.addView(voiceSwitch);

        TextView voiceHint = text("Exemplu: „9 lei și 82 de bani”. Nu citește restul calculelor.", 12, MUTED, false);
        voiceHint.setPadding(0, dp(6), 0, dp(14));
        auto.addView(voiceHint);

        Button accessibility = button("ACTIVEAZĂ MODUL AUTOMAT", GREEN, Color.BLACK);
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        auto.addView(accessibility, buttonParams());

        TextView autoHint = text("ProfitRide doar observă Acceptă/Refuză pentru pauză/reset și se ascunde în Waze. Nu apasă nimic în locul tău.", 12, MUTED, false);
        autoHint.setPadding(0, dp(8), 0, 0);
        auto.addView(autoHint);
        root.addView(auto);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){ updateCostPreview(); }
            @Override public void afterTextChanged(Editable e){}
        };
        for (EditText e : new EditText[]{liters100,ronLiter,manualCost,maintenanceKm,monthlyFixed,weeklyKm,weeklyHours,targetMonthly}) e.addTextChangedListener(watcher);
        minus.setOnClickListener(v -> adjustConsumption(-0.1));
        plus.setOnClickListener(v -> adjustConsumption(0.1));

        Button save = button("SALVEAZĂ TOATE SETĂRILE", YELLOW, Color.BLACK);
        LinearLayout.LayoutParams saveP = new LinearLayout.LayoutParams(-1, dp(58));
        saveP.setMargins(0, dp(4), 0, dp(12));
        root.addView(save, saveP);
        save.setOnClickListener(v -> {
            saveSettings();
            updateCostPreview();
            Toast.makeText(this, "Setările au fost salvate.", Toast.LENGTH_SHORT).show();
        });

        Button start = button("▶ PORNEȘTE ANALIZA AUTOMATĂ", GREEN, Color.BLACK);
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(62)));
        start.setOnClickListener(v -> ensureOverlayThen(PENDING_START_ANALYSIS));

        Button display = button("Arată doar afișajul flotant", FIELD, Color.WHITE);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(-1, dp(52));
        dp2.setMargins(0, dp(10), 0, 0);
        root.addView(display, dp2);
        display.setOnClickListener(v -> ensureOverlayThen(PENDING_OVERLAY_ONLY));

        Button stop = button("Oprește ProfitRide", RED, Color.WHITE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.setMargins(0, dp(10), 0, 0);
        root.addView(stop, sp);
        stop.setOnClickListener(v -> stopEverything());

        TextView footer = text("Creat de Plesia Razvan • ProfitRide 2.0", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(18), 0, 0);
        root.addView(footer);

        updateCostPreview();
        return scroll;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Activează ProfitRide și revino în aplicație.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Nu pot deschide Accesibilitate.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String wanted = new ComponentName(this, DriverStateAccessibilityService.class).flattenToString();
            for (String item : enabled.split(":")) if (wanted.equalsIgnoreCase(item)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private void ensureOverlayThen(int action) {
        saveSettings();
        if (Settings.canDrawOverlays(this)) {
            if (action == PENDING_START_ANALYSIS) requestScreenCapture();
            else startOverlayService();
            return;
        }
        pendingOverlayAction = action;
        new AlertDialog.Builder(this)
                .setTitle("Permisiune afișaj")
                .setMessage("Permite ProfitRide să se afișeze peste Bolt. Cardul este mutabil cu degetul.")
                .setPositiveButton("Deschide setările", (d,w) -> startActivity(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Anulează", (d,w) -> pendingOverlayAction = PENDING_NONE)
                .show();
    }

    private void requestScreenCapture() {
        saveSettings();
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Pentru pauză/reset automat și ascundere în Waze, activează și Accesibilitatea ProfitRide.", Toast.LENGTH_LONG).show();
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent svc = new Intent(this, ScreenCaptureService.class);
            svc.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
            svc.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc); else startService(svc);
            startOverlayService();
            Toast.makeText(this, "ProfitRide rulează. Deschide Bolt.", Toast.LENGTH_LONG).show();
        }
    }

    private void startOverlayService() {
        if (Settings.canDrawOverlays(this)) startService(new Intent(this, OverlayService.class));
    }

    private void stopEverything() {
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "ProfitRide oprit.", Toast.LENGTH_SHORT).show();
    }

    private void saveSettings() {
        prefs.edit()
                .putFloat("minRonKm",(float)num(minRonKm))
                .putFloat("minRonHour",(float)num(minRonHour))
                .putFloat("minProfit",(float)num(minProfit))
                .putFloat("liters100",(float)num(liters100))
                .putFloat("ronLiter",(float)num(ronLiter))
                .putFloat("manualCost",(float)num(manualCost))
                .putFloat("maintenanceKm",(float)num(maintenanceKm))
                .putFloat("monthlyFixed",(float)num(monthlyFixed))
                .putFloat("weeklyHours",(float)num(weeklyHours))
                .putFloat("weeklyKm",(float)num(weeklyKm))
                .putFloat("targetMonthly",(float)num(targetMonthly))
                .putBoolean("voiceEnabled", voiceSwitch != null && voiceSwitch.isChecked())
                .apply();
    }

    private void updateCostPreview() {
        if (costLabel == null) return;
        double direct = num(manualCost);
        double fuel = direct > 0 ? direct : (num(liters100)/100.0)*num(ronLiter);
        double maint = num(maintenanceKm);
        double monthlyKm = num(weeklyKm)*4.33;
        double fixed = monthlyKm > 0 ? num(monthlyFixed)/monthlyKm : 0;
        double monthlyHours = num(weeklyHours)*4.33;
        double targetPerKm = monthlyKm > 0 ? num(targetMonthly)/monthlyKm : 0;
        double targetPerHour = monthlyHours > 0 ? num(targetMonthly)/monthlyHours : 0;
        costLabel.setText("Cost real estimat: " + fmt(fuel+maint+fixed) + " RON/km\nȚinta ta: +" + fmt(targetPerKm) + " RON profit/km sau +" + fmt(targetPerHour) + " RON profit/oră");
    }

    private void adjustConsumption(double delta) {
        double v = num(liters100);
        if (v <= 0) v = 10;
        v = Math.max(0.1, Math.min(99.9, Math.round((v+delta)*10.0)/10.0));
        liters100.setText(String.format(Locale.US,"%.1f",v).replace('.',','));
        liters100.setSelection(liters100.length());
        saveSettings();
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14),dp(14),dp(14),dp(14));
        l.setBackground(Shape.rounded(CARD,22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,dp(12));
        l.setLayoutParams(p);
        return l;
    }

    private TextView section(String s) {
        TextView t = text(s,18,Color.WHITE,true);
        t.setPadding(0,dp(8),0,dp(8));
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s,16,Color.WHITE,true);
        t.setPadding(0,dp(4),0,dp(8));
        return t;
    }

    private LinearLayout fieldRow(String title, EditText e, String unit) {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(0,dp(5),0,dp(10));
        outer.addView(text(title,12,MUTED,false));
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,dp(4),0,0);
        r.addView(e,new LinearLayout.LayoutParams(0,dp(50),1f));
        TextView u = text(unit,12,MUTED,true);
        u.setPadding(dp(10),0,0,0);
        r.addView(u);
        outer.addView(r);
        return outer;
    }

    private EditText field(String hint,String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(MUTED);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(dp(14),0,dp(14),0);
        e.setBackground(Shape.rounded(FIELD,16));
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private Button button(String s,int bg,int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(fg);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        b.setAllCaps(false);
        b.setBackground(Shape.rounded(bg,18));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(54));
        p.setMargins(0,dp(4),0,0);
        return p;
    }

    private TextView text(String s,int sp,int c,boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(c);
        if (bold) t.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
        return t;
    }

    private String pref(String key,float fallback){ return trim(prefs.getFloat(key,fallback)); }
    private String trim(double v){
        if(Math.abs(v-Math.rint(v))<0.0001) return String.format(Locale.US,"%.0f",v);
        return String.format(Locale.US,"%.2f",v).replaceAll("0+$","").replaceAll("\\.$","").replace('.',',');
    }
    private double num(EditText e){
        if(e==null) return 0;
        try { double v=Double.parseDouble(e.getText().toString().trim().replace(',','.')); return Double.isFinite(v)&&v>0?v:0; }
        catch(Exception x){ return 0; }
    }
    private String fmt(double v){ return String.format(Locale.US,"%.2f",Math.max(0,v)).replace('.',','); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private void maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != getPackageManager().PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
    }
}
