package br.jota.jotapoke;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class JotaApp extends net.kdt.pojavlaunch.PojavApplication {
    static final String MANIFEST_URL = "https://raw.githubusercontent.com/DevJotaVx/jotapoke/main/manifest.json";
    static final String CDN = "https://mediafilez.forgecdn.net/files/";
    // Usado só se o manifesto não puder ser lido (sem internet ou GitHub fora do ar).
    static final String MR = "https://cdn.modrinth.com/data/";
    static final String[][] MODS = {
        {"Pixelmon", CDN + "9285/683/Pixelmon-1.21.1-9.4.2-universal.jar", "Pixelmon-1.21.1-9.4.2-universal.jar", "401077926"},
        {"MineColonies", CDN + "9394/366/minecolonies-1.1.1410-1.21.1-snapshot.jar", "minecolonies-1.1.1410-1.21.1-snapshot.jar", "78351358"},
        {"Structurize", CDN + "9333/948/structurize-1.0.837-1.21.1-snapshot.jar", "structurize-1.0.837-1.21.1-snapshot.jar", "1063621"},
        {"BlockUI", CDN + "9137/5/blockui-1.0.212-1.21.1-snapshot.jar", "blockui-1.0.212-1.21.1-snapshot.jar", "468141"},
        {"Domum Ornamentum", CDN + "9394/376/domum-ornamentum-1.0.237-snapshot-main.jar", "domum-ornamentum-1.0.237-snapshot-main.jar", "1151617"},
        {"Multi-Piston", CDN + "7097/877/multipiston-1.2.58-1.21.1.jar", "multipiston-1.2.58-1.21.1.jar", "34139"},
    };
    static volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 28) {
            String p = Application.getProcessName();
            if (p != null && p.endsWith(":game")) return;
        }
        try { setup(this); } catch (Throwable t) { toast(this, "JotaPoke: erro ao preparar - " + t.getMessage()); }
        try { installControls(this); } catch (Throwable ignored) {}
        registerActivityLifecycleCallbacks(new ControlChooser());
    }

    // Layouts de controle do JotaPoke, copiados para files/controlmap.
    static final String[] CONTROL_FILES = {"jotapoke_joystick.json", "jotapoke_classico.json", "jotapoke_teclado.json"};
    static final String[] CONTROL_NAMES = {"Joystick", "Setas", "Teclado e mouse"};
    static final String DEFAULT_CTRL = "default.json";

    static void installControls(Context ctx) throws IOException {
        File dir = new File(ctx.getExternalFilesDir(null), "controlmap");
        dir.mkdirs();
        for (String f : CONTROL_FILES) copyAssets(ctx.getAssets(), "jotapoke_controls/" + f, new File(dir, f));
    }

    // Pergunta o tipo de controle sempre que a tela inicial do launcher abre.
    static class ControlChooser implements Application.ActivityLifecycleCallbacks {
        boolean asked = false;
        public void onActivityResumed(final android.app.Activity a) {
            if (asked || !a.getClass().getName().endsWith("LauncherActivity")) return;
            asked = true;
            showChooser(a);
        }
        public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
        public void onActivityStarted(android.app.Activity a) {}
        public void onActivityPaused(android.app.Activity a) {}
        public void onActivityStopped(android.app.Activity a) {}
        public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
        public void onActivityDestroyed(android.app.Activity a) {}
    }

    // Tela cheia "Escolha seus controles": três cartões e a opção de usar o controle padrão do Pojav.
    static void showChooser(final android.app.Activity a) {
        final android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(a);
        final File dir = new File(a.getExternalFilesDir(null), "controlmap");
        final float dp = a.getResources().getDisplayMetrics().density;
        final android.app.Dialog dlg = new android.app.Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.setCancelable(false);

        android.widget.LinearLayout root = new android.widget.LinearLayout(a);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));
        root.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14204F, 0xFF0E3B2A}));

        android.widget.TextView title = new android.widget.TextView(a);
        title.setText("Escolha seus controles");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setShadowLayer(6, 0, 3, 0xFF000000);
        root.addView(title);

        android.widget.TextView sub = new android.widget.TextView(a);
        sub.setText("Dá para trocar depois: é só abrir o JotaPoke de novo.");
        sub.setTextColor(0xFFB8C7FF);
        sub.setTextSize(13);
        sub.setGravity(android.view.Gravity.CENTER);
        sub.setPadding(0, (int) (4 * dp), 0, (int) (16 * dp));
        root.addView(sub);

        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        root.addView(row, new android.widget.LinearLayout.LayoutParams(-1, -2));

        final android.widget.CheckBox old = new android.widget.CheckBox(a);

        for (int i = 0; i < CONTROL_FILES.length; i++) {
            final int idx = i;
            android.widget.LinearLayout card = new android.widget.LinearLayout(a);
            card.setOrientation(android.widget.LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            card.setPadding((int) (8 * dp), (int) (12 * dp), (int) (8 * dp), (int) (12 * dp));
            android.graphics.drawable.GradientDrawable cbg = new android.graphics.drawable.GradientDrawable();
            cbg.setColor(0x33FFFFFF); cbg.setCornerRadius(16 * dp); cbg.setStroke((int) (1 * dp), 0x55FFFFFF);
            card.setBackground(cbg);

            card.addView(new ControlIcon(a, i), new android.widget.LinearLayout.LayoutParams((int) (72 * dp), (int) (72 * dp)));

            android.widget.TextView name = new android.widget.TextView(a);
            name.setText(CONTROL_NAMES[i]);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(15);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            name.setGravity(android.view.Gravity.CENTER);
            name.setPadding(0, (int) (6 * dp), 0, (int) (10 * dp));
            card.addView(name);

            android.widget.Button pick = new android.widget.Button(a);
            pick.setText("Escolher");
            pick.setAllCaps(false);
            pick.setTextColor(0xFFFFFFFF);
            pick.setTextSize(15);
            pick.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            android.graphics.drawable.GradientDrawable bbg = new android.graphics.drawable.GradientDrawable();
            bbg.setColor(0xFF3FA535); bbg.setCornerRadius(10 * dp); bbg.setStroke((int) (2 * dp), 0xFF2A7A22);
            pick.setBackground(bbg);
            pick.setOnClickListener(new android.view.View.OnClickListener() {
                public void onClick(android.view.View v) {
                    String file = old.isChecked() ? DEFAULT_CTRL : CONTROL_FILES[idx];
                    prefs.edit().putString("defaultCtrl", new File(dir, file).getAbsolutePath()).apply();
                    Toast.makeText(a, "Controle: " + (old.isChecked() ? "padrão do Pojav" : CONTROL_NAMES[idx]), Toast.LENGTH_SHORT).show();
                    dlg.dismiss();
                }
            });
            card.addView(pick, new android.widget.LinearLayout.LayoutParams(-1, (int) (44 * dp)));

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
            row.addView(card, lp);
        }

        old.setText("Usar o controle padrão do Pojav");
        old.setTextColor(0xFFDDDDDD);
        old.setTextSize(14);
        old.setChecked(prefs.getString("defaultCtrl", "").endsWith("/" + DEFAULT_CTRL));
        android.widget.LinearLayout.LayoutParams olp = new android.widget.LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = (int) (18 * dp);
        root.addView(old, olp);

        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.setFillViewport(true);
        scroll.addView(root, new android.view.ViewGroup.LayoutParams(-1, -1));
        dlg.setContentView(scroll);
        dlg.show();
    }

    // Ícones desenhados à mão: 0 = joystick, 1 = setas, 2 = teclado e mouse.
    static class ControlIcon extends android.view.View {
        final int kind;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ControlIcon(Context c, int kind) { super(c); this.kind = kind; }
        @Override protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, s = Math.min(w, h);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(0x3366E07A);
            c.drawCircle(cx, cy, s * 0.48f, p);
            if (kind == 0) {
                p.setColor(0xFFE8ECF5); c.drawCircle(cx, cy, s * 0.34f, p);
                p.setColor(0xFF2B2F3A); c.drawCircle(cx, cy, s * 0.20f, p);
                p.setColor(0xFF4A5060); c.drawCircle(cx - s * 0.04f, cy - s * 0.04f, s * 0.12f, p);
            } else if (kind == 1) {
                p.setColor(0xFFE8ECF5);
                float a = s * 0.13f, b = s * 0.36f;
                c.drawRoundRect(new android.graphics.RectF(cx - a, cy - b, cx + a, cy + b), a * 0.4f, a * 0.4f, p);
                c.drawRoundRect(new android.graphics.RectF(cx - b, cy - a, cx + b, cy + a), a * 0.4f, a * 0.4f, p);
                p.setColor(0xFF9AA3B5);
                android.graphics.Path t = new android.graphics.Path();
                float k = s * 0.06f, o = s * 0.25f;
                t.moveTo(cx, cy - o - k); t.lineTo(cx - k, cy - o + k * 0.6f); t.lineTo(cx + k, cy - o + k * 0.6f); t.close();
                t.moveTo(cx, cy + o + k); t.lineTo(cx - k, cy + o - k * 0.6f); t.lineTo(cx + k, cy + o - k * 0.6f); t.close();
                t.moveTo(cx - o - k, cy); t.lineTo(cx - o + k * 0.6f, cy - k); t.lineTo(cx - o + k * 0.6f, cy + k); t.close();
                t.moveTo(cx + o + k, cy); t.lineTo(cx + o - k * 0.6f, cy - k); t.lineTo(cx + o - k * 0.6f, cy + k); t.close();
                c.drawPath(t, p);
            } else {
                p.setColor(0xFFE8ECF5);
                android.graphics.RectF kb = new android.graphics.RectF(cx - s * 0.40f, cy - s * 0.16f, cx + s * 0.16f, cy + s * 0.20f);
                c.drawRoundRect(kb, s * 0.04f, s * 0.04f, p);
                p.setColor(0xFF9AA3B5);
                float kw = (kb.width() - s * 0.08f) / 5, kh = (kb.height() - s * 0.08f) / 3;
                for (int r = 0; r < 3; r++) for (int q = 0; q < 5; q++) {
                    float x = kb.left + s * 0.04f + q * kw, y = kb.top + s * 0.04f + r * kh;
                    c.drawRoundRect(new android.graphics.RectF(x + 1, y + 1, x + kw - 2, y + kh - 2), 3, 3, p);
                }
                p.setColor(0xFFE8ECF5);
                c.drawRoundRect(new android.graphics.RectF(cx + s * 0.22f, cy - s * 0.06f, cx + s * 0.40f, cy + s * 0.24f), s * 0.09f, s * 0.09f, p);
                p.setColor(0xFF9AA3B5);
                c.drawRect(cx + s * 0.305f, cy - s * 0.05f, cx + s * 0.315f, cy + s * 0.06f, p);
            }
        }
    }

    static void setup(final Context ctx) throws IOException {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) return;
        final File mc = new File(base, ".minecraft");
        mc.mkdirs();
        File marker = new File(mc, ".jotapoke_v2");
        if (!marker.exists()) {
            copyAssets(ctx.getAssets(), "jotapoke", mc);
            marker.createNewFile();
        }
        if (running) return;
        running = true;
        new Thread(new Runnable() { public void run() {
            try {
                update(ctx, mc);
            } catch (Throwable t) {
                toast(ctx, "JotaPoke: falha ao atualizar. Abra o app de novo com internet.");
            } finally { running = false; }
        }}).start();
    }

    static void update(Context ctx, File mc) throws Exception {
        File modsDir = new File(mc, "mods");
        modsDir.mkdirs();
        JSONObject man = null;
        try { man = new JSONObject(readUrl(MANIFEST_URL)); } catch (Throwable ignored) {}

        String[][] mods = MODS;
        if (man != null && man.has("mods")) {
            JSONArray a = man.getJSONArray("mods");
            mods = new String[a.length()][];
            for (int i = 0; i < a.length(); i++) {
                JSONObject m = a.getJSONObject(i);
                mods[i] = new String[]{m.getString("name"), m.getString("url"), m.getString("file"), String.valueOf(m.getLong("size"))};
            }
        }

        // Remove mods instalados pelo JotaPoke que saíram da lista.
        File managed = new File(mc, ".jotapoke_mods");
        Set<String> wanted = new HashSet<String>();
        for (String[] m : mods) wanted.add(m[2]);
        Set<String> old = readLines(managed);
        for (String[] m : MODS) old.add(m[2]);
        for (String f : old) {
            if (!wanted.contains(f) && new File(modsDir, f).delete()) toast(ctx, "JotaPoke: mod removido - " + f);
        }

        boolean changed = false;
        for (String[] m : mods) {
            File jar = new File(modsDir, m[2]);
            long size = Long.parseLong(m[3]);
            if (jar.length() != size) { download(ctx, m[0], m[1], jar, size); changed = true; }
        }
        writeLines(managed, wanted);

        if (man != null && man.has("files")) {
            JSONArray a = man.getJSONArray("files");
            for (int i = 0; i < a.length(); i++) {
                JSONObject f = a.getJSONObject(i);
                File dest = new File(mc, f.getString("path"));
                long size = f.getLong("size");
                if (dest.length() != size) { download(ctx, f.getString("path"), f.getString("url"), dest, size); changed = true; }
            }
        }
        if (changed) toast(ctx, "JotaPoke: tudo atualizado! Pode jogar.");

        if (man != null && man.has("apkVersion")) {
            long current = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
            if (man.getLong("apkVersion") > current) {
                File apk = new File(ctx.getExternalFilesDir("update"), "JotaPoke-" + man.getLong("apkVersion") + ".apk");
                long size = man.getLong("apkSize");
                if (apk.length() != size) download(ctx, "nova versão do app", man.getString("apkUrl"), apk, size);
                installApk(ctx, apk);
            }
        }
    }

    static void installApk(final Context ctx, final File apk) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
            try {
                Uri uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".apkprovider", apk);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                Toast.makeText(ctx, "JotaPoke: tem versão nova! Toque em Atualizar.", Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(ctx, "JotaPoke: não consegui abrir a atualização - " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }});
    }

    static String readUrl(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "JotaPoke");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l).append('\n');
        r.close();
        return sb.toString();
    }

    static Set<String> readLines(File f) {
        Set<String> s = new HashSet<String>();
        try {
            BufferedReader r = new BufferedReader(new FileReader(f)); String l;
            while ((l = r.readLine()) != null) if (l.trim().length() > 0) s.add(l.trim());
            r.close();
        } catch (IOException ignored) {}
        return s;
    }

    static void writeLines(File f, Set<String> lines) throws IOException {
        PrintWriter w = new PrintWriter(new FileWriter(f));
        for (String l : lines) w.println(l);
        w.close();
    }

    static void copyAssets(AssetManager am, String path, File dest) throws IOException {
        String[] list = am.list(path);
        if (list == null || list.length == 0) {
            dest.getParentFile().mkdirs();
            InputStream in = am.open(path);
            OutputStream out = new FileOutputStream(dest);
            byte[] b = new byte[65536]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close(); out.close();
            return;
        }
        dest.mkdirs();
        for (String s : list) copyAssets(am, path + "/" + s, new File(dest, s));
    }

    static void download(Context ctx, String name, String url, File dest, long size) throws IOException {
        dest.getParentFile().mkdirs();
        File part = new File(dest.getPath() + ".part");
        long have = part.exists() ? part.length() : 0;
        if (have > size) { part.delete(); have = 0; }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "JotaPoke");
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        int code = c.getResponseCode();
        if (code == 200) have = 0;
        else if (code != 206) throw new IOException("HTTP " + code);
        toast(ctx, "JotaPoke: baixando " + name + " (" + Math.max(1, size >> 20) + " MB)... espere antes de jogar.");
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(part, have > 0);
        byte[] b = new byte[131072]; int n; long done = have; int lastPct = -1;
        while ((n = in.read(b)) > 0) {
            out.write(b, 0, n); done += n;
            int pct = (int) (done * 100 / size);
            if (size > (8 << 20) && pct / 10 != lastPct / 10) { lastPct = pct; toast(ctx, "JotaPoke: " + name + " " + pct + "%"); }
        }
        out.close(); in.close();
        if (part.length() != size) throw new IOException("tamanho errado");
        dest.delete();
        if (!part.renameTo(dest)) throw new IOException("rename");
    }

    static void toast(final Context ctx, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { public void run() {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
        }});
    }
}
