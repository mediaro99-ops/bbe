package ro.plesiarazvan.profitride;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OverlayService extends Service {
    public static final String ACTION_UPDATE = "ro.plesiarazvan.profitride.UPDATE_OFFER";
    public static final String ACTION_RESET = "ro.plesiarazvan.profitride.RESET_OFFER";
    public static final String ACTION_PAUSE = "ro.plesiarazvan.profitride.PAUSE_ANALYSIS";
    public static final String ACTION_HIDE = "ro.plesiarazvan.profitride.HIDE_OVERLAY";
    public static final String ACTION_SHOW_CURRENT = "ro.plesiarazvan.profitride.SHOW_CURRENT";

    public static final String MODE_WAITING = "waiting";
    public static final String MODE_OFFER = "offer";
    public static final String MODE_PAUSED = "paused";

    private WindowManager wm;
    private View overlay;
    private WindowManager.LayoutParams params;
    private TextView status, fareView, pickupView, tripView, totalView, netKmView, netHourView, profitView, verdictView, creatorView;
    private BroadcastReceiver receiver;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String lastSpokenKey = "";

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("profitride", MODE_PRIVATE);
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("ro","RO"));
                tts.setSpeechRate(1.0f);
            }
        });

        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        overlay = buildOverlay();

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                dp(326),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = prefs.getInt("overlayY", dp(72));
        params.x = prefs.getInt("overlayX", 0);
        wm.addView(overlay, params);
        enableDrag(overlay);
        registerReceiver();
        applySavedMode();
    }

    private View buildOverlay() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(9));
        root.setBackground(Shape.rounded(Color.rgb(9,18,24),22));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.profitride_logo);
        top.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(8),0,0,0);
        brand.addView(tv("ProfitRide",15,Color.WHITE,true));
        status = tv("Analiză automată",10,Color.rgb(160,180,190),false);
        brand.addView(status);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1f));

        TextView drag = tv("↕",18,Color.rgb(140,160,170),true);
        drag.setGravity(Gravity.CENTER);
        top.addView(drag,new LinearLayout.LayoutParams(dp(34),dp(34)));
        root.addView(top);

        fareView = tv("0,00 lei",38,Color.rgb(255,202,30),true);
        fareView.setGravity(Gravity.CENTER_HORIZONTAL);
        fareView.setPadding(0,dp(4),0,dp(4));
        root.addView(fareView);

        LinearLayout legs = new LinearLayout(this);
        legs.setOrientation(LinearLayout.HORIZONTAL);
        pickupView = smallBox("PÂNĂ LA CLIENT\n0 min • 0,0 km");
        tripView = smallBox("CURSĂ\n0 min • 0,0 km");
        addHalf(legs,pickupView,0);
        addHalf(legs,tripView,1);
        root.addView(legs);

        totalView = tv("Total: 0 min • 0,0 km",12,Color.WHITE,true);
        totalView.setGravity(Gravity.CENTER);
        totalView.setPadding(0,dp(6),0,dp(6));
        root.addView(totalView);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        netKmView = metricBox("NET/KM\n0,00");
        netHourView = metricBox("NET/ORĂ\n0,00");
        profitView = metricBox("PROFIT\n0,00");
        addThird(metrics,netKmView,0);
        addThird(metrics,netHourView,1);
        addThird(metrics,profitView,2);
        root.addView(metrics);

        verdictView = tv("Aștept următoarea ofertă",15,Color.WHITE,true);
        verdictView.setGravity(Gravity.CENTER);
        verdictView.setPadding(0,dp(7),0,0);
        root.addView(verdictView);

        creatorView = tv("Creat de Plesia Razvan",8,Color.rgb(118,136,146),false);
        creatorView.setGravity(Gravity.CENTER);
        creatorView.setPadding(0,dp(4),0,0);
        root.addView(creatorView);

        return root;
    }

    private void registerReceiver() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a=i.getAction();
                if(ACTION_UPDATE.equals(a)){
                    double fare=i.getDoubleExtra("fare",0);
                    double km=i.getDoubleExtra("km",0);
                    double min=i.getDoubleExtra("minutes",0);
                    double pkm=i.getDoubleExtra("pickupKm",0);
                    double pmin=i.getDoubleExtra("pickupMinutes",0);
                    double tkm=i.getDoubleExtra("tripKm",0);
                    double tmin=i.getDoubleExtra("tripMinutes",0);
                    if(fare>0 && km>0){
                        saveOffer(fare,km,min,pkm,pmin,tkm,tmin);
                        showOverlay();
                        showOffer(fare,km,min,pkm,pmin,tkm,tmin);
                        speakFareOnce(fare,km,min);
                    }
                } else if(ACTION_RESET.equals(a)){
                    prefs.edit().putString("rideMode",MODE_WAITING).apply();
                    clearOffer(); lastSpokenKey="";
                    showOverlay(); showWaiting();
                } else if(ACTION_PAUSE.equals(a)){
                    prefs.edit().putString("rideMode",MODE_PAUSED).apply();
                    showOverlay(); showPaused();
                } else if(ACTION_HIDE.equals(a)){
                    hideOverlay();
                } else if(ACTION_SHOW_CURRENT.equals(a)){
                    showOverlay(); applySavedMode();
                }
            }
        };
        IntentFilter f=new IntentFilter();
        f.addAction(ACTION_UPDATE); f.addAction(ACTION_RESET); f.addAction(ACTION_PAUSE); f.addAction(ACTION_HIDE); f.addAction(ACTION_SHOW_CURRENT);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f);
    }

    private void applySavedMode() {
        String mode=prefs.getString("rideMode",MODE_WAITING);
        if(MODE_PAUSED.equals(mode)) showPaused();
        else if(MODE_OFFER.equals(mode) && prefs.getFloat("lastFare",0)>0) showOffer(
                prefs.getFloat("lastFare",0),prefs.getFloat("lastKm",0),prefs.getFloat("lastMinutes",0),
                prefs.getFloat("lastPickupKm",0),prefs.getFloat("lastPickupMinutes",0),
                prefs.getFloat("lastTripKm",0),prefs.getFloat("lastTripMinutes",0));
        else showWaiting();
    }

    private void showWaiting() {
        status.setText("Analiză automată • mută cardul cu degetul");
        fareView.setText("0,00 lei");
        fareView.setTextColor(Color.rgb(255,202,30));
        pickupView.setText("PÂNĂ LA CLIENT\n0 min • 0,0 km");
        tripView.setText("CURSĂ\n0 min • 0,0 km");
        totalView.setText("Total: 0 min • 0,0 km");
        netKmView.setText("NET/KM\n0,00");
        netHourView.setText("NET/ORĂ\n0,00");
        profitView.setText("PROFIT\n0,00");
        verdictView.setText("Aștept următoarea ofertă");
        verdictView.setTextColor(Color.WHITE);
    }

    private void showPaused() {
        status.setText("Pauză automată • cursă în desfășurare");
        fareView.setText("PAUZĂ");
        fareView.setTextColor(Color.rgb(255,202,30));
        pickupView.setText("PÂNĂ LA CLIENT\n—");
        tripView.setText("CURSĂ\nîn desfășurare");
        totalView.setText("Repornește automat la următoarea ofertă");
        netKmView.setText("NET/KM\n—");
        netHourView.setText("NET/ORĂ\n—");
        profitView.setText("PROFIT\n—");
        verdictView.setText("Waze rămâne liber; cardul revine în Bolt");
        verdictView.setTextColor(Color.rgb(145,195,255));
    }

    private void showOffer(double fare,double km,double minutes,double pickupKm,double pickupMinutes,double tripKm,double tripMinutes) {
        double fuel = prefs.getFloat("manualCost",0f);
        if(fuel<=0) fuel=prefs.getFloat("liters100",10f)/100.0*prefs.getFloat("ronLiter",4.66f);
        double maintenance=prefs.getFloat("maintenanceKm",0.25f);
        double monthlyKm=prefs.getFloat("weeklyKm",800f)*4.33;
        double fixed=monthlyKm>0 ? prefs.getFloat("monthlyFixed",0f)/monthlyKm : 0;
        double realCostKm=fuel+maintenance+fixed;
        double cost=km*realCostKm;
        double profit=fare-cost;
        double netKm=km>0?profit/km:0;
        double netHour=minutes>0?profit/(minutes/60.0):0;

        double thKm=prefs.getFloat("minRonKm",2f);
        double thHour=prefs.getFloat("minRonHour",50f);
        double thProfit=prefs.getFloat("minProfit",5f);
        boolean ok=netKm>=thKm && (minutes<=0 || netHour>=thHour) && profit>=thProfit;
        double sKm = thKm > 0 ? Math.min(100.0, (netKm / thKm) * 100.0) : 100.0;
        double sHour = (minutes <= 0 || thHour <= 0) ? 100.0 : Math.min(100.0, (netHour / thHour) * 100.0);
        double sProfit = thProfit > 0 ? Math.min(100.0, (profit / thProfit) * 100.0) : 100.0;
        int rideScore = (int)Math.round((sKm + sHour + sProfit) / 3.0);

        status.setText("Ofertă detectată • cost real inclus");
        fareView.setText(f(fare)+" lei");
        fareView.setTextColor(ok?Color.rgb(70,224,137):Color.rgb(255,95,95));

        if(pickupKm>0 || pickupMinutes>0) pickupView.setText("PÂNĂ LA CLIENT\n"+f0(pickupMinutes)+" min • "+f1(pickupKm)+" km");
        else pickupView.setText("PÂNĂ LA CLIENT\n—");
        if(tripKm>0 || tripMinutes>0) tripView.setText("CURSĂ\n"+f0(tripMinutes)+" min • "+f1(tripKm)+" km");
        else tripView.setText("CURSĂ\n—");
        totalView.setText("Total: "+(minutes>0?f0(minutes)+" min":"—")+" • "+f1(km)+" km");

        netKmView.setText("NET/KM\n"+f(netKm));
        netHourView.setText("NET/ORĂ\n"+(minutes>0?f(netHour):"—"));
        profitView.setText("PROFIT\n"+f(profit));

        verdictView.setText((ok ? "✓ MERITĂ" : "✕ NU MERITĂ") + "  •  SCORE " + rideScore);
        verdictView.setTextColor(ok?Color.rgb(55,226,141):Color.rgb(255,95,95));
    }

    private void speakFareOnce(double fare,double km,double min) {
        if(!prefs.getBoolean("voiceEnabled",true) || !ttsReady) return;
        String key=String.format(Locale.US,"%.2f|%.1f|%.0f",fare,km,min);
        if(key.equals(lastSpokenKey)) return;
        lastSpokenKey=key;
        int lei=(int)Math.floor(fare);
        int bani=(int)Math.round((fare-lei)*100);
        String text;
        if(bani==0) text=lei+" lei";
        else text=lei+" lei și "+bani+" de bani";
        tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"profitride_fare");
    }

    private void saveOffer(double fare,double km,double min,double pkm,double pmin,double tkm,double tmin) {
        prefs.edit().putString("rideMode",MODE_OFFER)
                .putFloat("lastFare",(float)fare).putFloat("lastKm",(float)km).putFloat("lastMinutes",(float)min)
                .putFloat("lastPickupKm",(float)pkm).putFloat("lastPickupMinutes",(float)pmin)
                .putFloat("lastTripKm",(float)tkm).putFloat("lastTripMinutes",(float)tmin).apply();
    }

    private void clearOffer() {
        prefs.edit().remove("lastFare").remove("lastKm").remove("lastMinutes").remove("lastPickupKm")
                .remove("lastPickupMinutes").remove("lastTripKm").remove("lastTripMinutes").apply();
    }

    private TextView smallBox(String s){
        TextView t=tv(s,11,Color.WHITE,true); t.setGravity(Gravity.CENTER); t.setPadding(dp(4),dp(7),dp(4),dp(7));
        t.setBackground(Shape.rounded(Color.rgb(16,49,66),13)); return t;
    }
    private TextView metricBox(String s){
        TextView t=tv(s,11,Color.WHITE,true); t.setGravity(Gravity.CENTER); t.setPadding(dp(3),dp(7),dp(3),dp(7));
        t.setBackground(Shape.rounded(Color.rgb(25,38,46),13)); return t;
    }
    private void addHalf(LinearLayout r,TextView v,int i){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1f);
        p.setMargins(i==0?0:dp(4),0,i==0?dp(4):0,0); r.addView(v,p);
    }
    private void addThird(LinearLayout r,TextView v,int i){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1f);
        p.setMargins(i==0?0:dp(3),0,i==2?0:dp(3),0); r.addView(v,p);
    }
    private TextView tv(String s,int sp,int c,boolean b){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(c); if(b)t.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD)); return t; }

    private void hideOverlay(){ if(overlay!=null) overlay.setVisibility(View.GONE); }
    private void showOverlay(){ if(overlay!=null) overlay.setVisibility(View.VISIBLE); }

    private void enableDrag(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX,downY; int startX,startY; boolean moved;
            @Override public boolean onTouch(View view,MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){ downX=e.getRawX(); downY=e.getRawY(); startX=params.x; startY=params.y; moved=false; return true; }
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-downX), dy=(int)(e.getRawY()-downY);
                    if(Math.abs(dx)>3||Math.abs(dy)>3)moved=true;
                    params.x=startX+dx; params.y=startY+dy;
                    try{ wm.updateViewLayout(overlay,params);}catch(Exception ignored){}
                    return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP){
                    prefs.edit().putInt("overlayX",params.x).putInt("overlayY",params.y).apply();
                    return true;
                }
                return false;
            }
        });
    }

    private String f(double v){ if(!Double.isFinite(v))v=0; return String.format(Locale.US,"%.2f",v).replace('.',','); }
    private String f1(double v){ if(!Double.isFinite(v))v=0; return String.format(Locale.US,"%.1f",v).replace('.',','); }
    private String f0(double v){ if(!Double.isFinite(v))v=0; return String.format(Locale.US,"%.0f",v).replace('.',','); }

    @Override public void onDestroy() {
        if(receiver!=null) try{unregisterReceiver(receiver);}catch(Exception ignored){}
        if(tts!=null){ try{tts.stop();tts.shutdown();}catch(Exception ignored){} }
        if(wm!=null&&overlay!=null) try{wm.removeView(overlay);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){ return null; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
