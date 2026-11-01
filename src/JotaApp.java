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
    static final String[] CONTROL_FILES = {"jotapoke_classico.json", "jotapoke_grandes.json", "jotapoke_canhoto.json"};
    static final String[] CONTROL_NAMES = {"Clássico (setas à esquerda)", "Botões grandes", "Canhoto (setas à direita)"};

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
            final android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(a);
            final File dir = new File(a.getExternalFilesDir(null), "controlmap");
            String current = prefs.getString("defaultCtrl", "");
            int sel = 0;
            for (int i = 0; i < CONTROL_FILES.length; i++) if (current.endsWith(CONTROL_FILES[i])) sel = i;
            final int[] choice = {sel};
            new android.app.AlertDialog.Builder(a)
                .setTitle("Como você quer jogar?")
                .setSingleChoiceItems(CONTROL_NAMES, sel, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { choice[0] = w; }
                })
                .setPositiveButton("Continuar", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String path = new File(dir, CONTROL_FILES[choice[0]]).getAbsolutePath();
                        prefs.edit().putString("defaultCtrl", path).apply();
                        Toast.makeText(a, "Controle: " + CONTROL_NAMES[choice[0]], Toast.LENGTH_SHORT).show();
                    }
                })
                .setCancelable(false)
                .show();
        }
        public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {}
        public void onActivityStarted(android.app.Activity a) {}
        public void onActivityPaused(android.app.Activity a) {}
        public void onActivityStopped(android.app.Activity a) {}
        public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
        public void onActivityDestroyed(android.app.Activity a) {}
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
