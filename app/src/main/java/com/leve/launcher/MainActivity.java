package com.leve.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {

    static class App {
        String label;
        ComponentName cn; // null = atalho interno (Jogos de hoje)
        Drawable icon;
    }

    private static final long INTERVALO = 10 * 60 * 1000; // atualiza jogos a cada 10 min

    private final ArrayList<App> apps = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private GridView grid;
    private BaseAdapter adapter;
    private TextView jogosView;
    private float d;
    private boolean loading, buscando;
    private long ultimaBusca;
    private List<Jogos.Jogo> jogos = new ArrayList<>();
    private App tileJogos;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { load(); }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            buscarJogos();
            ui.postDelayed(this, INTERVALO);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101418); // cor sólida: sem wallpaper, sem gastar RAM

        // Jogos de hoje (resumo, canto superior esquerdo)
        jogosView = new TextView(this);
        jogosView.setTextColor(0xFFB8F5C8);
        jogosView.setTextSize(13);
        jogosView.setMaxLines(3);
        jogosView.setEllipsize(TextUtils.TruncateAt.END);
        jogosView.setMaxWidth(getResources().getDisplayMetrics().widthPixels * 7 / 10);
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        jp.setMargins((int) (32 * d), (int) (16 * d), 0, 0);
        root.addView(jogosView, jp);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(28);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clp.setMargins(0, (int) (20 * d), (int) (40 * d), 0);
        root.addView(clock, clp);

        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth((int) (140 * d));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing((int) (8 * d));
        grid.setPadding((int) (32 * d), (int) (88 * d), (int) (32 * d), (int) (24 * d));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(0x44FFFFFF)); // destaque do foco (controle remoto)
        grid.setScrollingCacheEnabled(false);
        grid.setAnimationCacheEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Atalho interno "Jogos de hoje"
        GradientDrawable verde = new GradientDrawable();
        verde.setColor(0xFF1B8A3E);
        verde.setCornerRadius(14 * d);
        tileJogos = new App();
        tileJogos.label = "Jogos de hoje";
        tileJogos.icon = verde;

        adapter = new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int p) { return apps.get(p); }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View v, ViewGroup parent) {
                ImageView icon;
                TextView label;
                if (v == null) {
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setGravity(Gravity.CENTER_HORIZONTAL);
                    row.setPadding(0, (int) (12 * d), 0, (int) (8 * d));
                    row.setLayoutParams(new AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) (120 * d)));
                    icon = new ImageView(MainActivity.this);
                    row.addView(icon, new LinearLayout.LayoutParams((int) (64 * d), (int) (64 * d)));
                    label = new TextView(MainActivity.this);
                    label.setTextColor(0xFFFFFFFF);
                    label.setTextSize(13);
                    label.setSingleLine(true);
                    label.setEllipsize(TextUtils.TruncateAt.END);
                    label.setGravity(Gravity.CENTER);
                    label.setPadding((int) (4 * d), (int) (6 * d), (int) (4 * d), 0);
                    row.addView(label, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    row.setTag(new View[]{icon, label});
                    v = row;
                } else {
                    View[] h = (View[]) v.getTag();
                    icon = (ImageView) h[0];
                    label = (TextView) h[1];
                }
                App a = apps.get(p);
                icon.setImageDrawable(a.icon);
                label.setText(a.label);
                return v;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                App a = apps.get(pos);
                if (a.cn == null) { mostrarJogos(); return; }
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN)
                            .setComponent(a.cn)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                } catch (Exception ignored) { }
            }
        });
        root.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        load();
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                ArrayList<App> out = new ArrayList<>();
                HashSet<String> seen = new HashSet<>();
                String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
                for (String cat : cats) {
                    List<ResolveInfo> list = pm.queryIntentActivities(
                            new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
                    for (ResolveInfo ri : list) {
                        String pkg = ri.activityInfo.packageName;
                        if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                        App a = new App();
                        a.label = String.valueOf(ri.loadLabel(pm));
                        a.cn = new ComponentName(pkg, ri.activityInfo.name);
                        a.icon = ri.loadIcon(pm);
                        out.add(a);
                    }
                }
                Collections.sort(out, new Comparator<App>() {
                    @Override public int compare(App x, App y) {
                        return x.label.compareToIgnoreCase(y.label);
                    }
                });
                final ArrayList<App> result = out;
                ui.post(new Runnable() {
                    @Override public void run() {
                        apps.clear();
                        apps.add(tileJogos); // sempre o primeiro da grade
                        apps.addAll(result);
                        adapter.notifyDataSetChanged();
                        loading = false;
                    }
                });
            }
        }).start();
    }

    private void buscarJogos() {
        if (buscando) return;
        buscando = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Jogos.Jogo> r = Jogos.buscar();
                ui.post(new Runnable() {
                    @Override public void run() {
                        buscando = false;
                        if (r == null) return; // sem rede: mantém o que já estava na tela
                        ultimaBusca = System.currentTimeMillis();
                        jogos = r;
                        atualizarResumo();
                    }
                });
            }
        }).start();
    }

    private void atualizarResumo() {
        if (jogos.isEmpty()) {
            jogosView.setText("⚽ Sem jogos hoje nas ligas selecionadas");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < jogos.size() && i < 3; i++) {
            if (i > 0) sb.append('\n');
            sb.append(Jogos.linha(jogos.get(i)));
        }
        jogosView.setText(sb.toString());
    }

    private void mostrarJogos() {
        String[] itens;
        if (jogos.isEmpty()) {
            itens = new String[]{"Nenhum jogo hoje (ou sem conexão com a internet)."};
        } else {
            itens = new String[jogos.size()];
            for (int i = 0; i < itens.length; i++) itens[i] = Jogos.linha(jogos.get(i));
        }
        new AlertDialog.Builder(this).setTitle("Jogos de hoje").setItems(itens, null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(tick);
        long espera = Math.max(0, ultimaBusca + INTERVALO - System.currentTimeMillis());
        ui.postDelayed(tick, espera);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent i) { // botão HOME: volta ao topo
        super.onNewIntent(i);
        if (grid != null) grid.setSelection(0);
    }

    @Override public void onBackPressed() { /* launcher não fecha */ }

    @Override
    protected void onDestroy() {
        unregisterReceiver(pkgReceiver);
        super.onDestroy();
    }
}
