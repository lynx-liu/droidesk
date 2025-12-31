package com.avdi.droidesk;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Utils {
    public static String getProperty(final String key, final String defaultValue) {
        String value = defaultValue;
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            value = (String)(get.invoke(c, key, defaultValue ));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public static void setProperty(final String key, final String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String toJson(Bundle extras) {
        JSONObject jsonObject = new JSONObject();
        if (extras != null) {
            Set<String> keys = extras.keySet();
            for (String key : keys) {
                try {
                    jsonObject.put(key, JSONObject.wrap(extras.get(key)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return jsonObject.toString();
    }

    public static Bundle toBundle(JSONObject json){
        Bundle bundle = new Bundle();
        Iterator<String> iterator = json.keys();
        while(iterator.hasNext()){
            String key = iterator.next();
            bundle.putString(key, json.optString(key));
        }
        return bundle;
    }

    public static void runCmd(String cmd) {
        Log.d("llx", "runCmd: " + cmd);
        String[] args = {"sh", "-c", cmd};
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            // 先读标准输出
            while ((line = reader.readLine()) != null) {
                Log.d("llx", line);
            }
            // 再读错误输出
            while ((line = errorReader.readLine()) != null) {
                Log.e("llx", line);
            }

            process.waitFor();
        } catch (Exception e) {
            Log.e("llx", e.toString());
        }
    }

    public static boolean isRunning(String processName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "ps -ef | grep " + processName + " | grep -v grep");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            boolean isRunning = (line != null);
            process.waitFor();

            return isRunning;
        } catch (Exception e) {
            Log.d("llx", e.toString());
            return false;
        }
    }

    private static boolean installSSH(Context context, File destDir, String keyfile) {
        String abi = getPrimaryAbi();

        String[] names = new String[]{"ssh", "libssh.so", "libssl.so", "libcrypto.so"};
        for (String name : names) {
            String assetPath = abi + "/" + name;
            File destFile = new File(destDir, name);
            if(!copyAssetToFile(context, assetPath, destFile))
                return false;
            destFile.setExecutable(true);
            destFile.setReadable(true);
            destFile.setWritable(true);
        }
        return copyAssetToFile(context, keyfile, new File(destDir, keyfile));
    }

    public static void runSSH(Context context, int serverport) {
        String keyfile = "id_ed25519";
        File home = context.getFilesDir();
        if (!installSSH(context, home, keyfile)) {
            return;
        }

        String clientport = getProperty("service.adb.tcp.port", "5555");
        String sshPath = new File(home, "ssh").getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(
                sshPath,
                "-T",
                "-i", keyfile,
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "StrictHostKeyChecking=no",
                "-R", serverport+":127.0.0.1:"+clientport,
                "root@10.86.32.34"
        );
        pb.directory(home);

        Map<String, String> env = pb.environment();
        env.put("HOME", home.getAbsolutePath());
        env.put("USER", "root");
        env.put("LD_LIBRARY_PATH", home.getAbsolutePath());

        try {
            Process process = pb.start();

            BufferedReader stdout =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderr =
                    new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = stdout.readLine()) != null) {
                Log.d("llx", line);
            }
            while ((line = stderr.readLine()) != null) {
                Log.e("llx", line);
            }

            int code = process.waitFor();
            Log.d("llx", "ssh exit code=" + code);

        } catch (Exception e) {
            Log.e("llx", "ssh failed", e);
        }
    }

    private static boolean copyAssetToFile(Context context, String assetPath, File outFile) {
        InputStream is = null;
        FileOutputStream fos = null;
        AssetManager am = context.getAssets();
        try {
            try {
                is = am.open(assetPath);
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                fos = new FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
                fos.getFD().sync();
            } finally {
                try {
                    if (is != null) is.close();
                } catch (IOException ignored) {
                }
                try {
                    if (fos != null) fos.close();
                } catch (IOException ignored) {
                }
            }
            return true;
        } catch (Exception e) {
            Log.e("llx", e.toString());
        }
        return false;
    }

    public static String getPrimaryAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null && abis.length > 0) return abis[0];
        return Build.CPU_ABI != null ? Build.CPU_ABI : "arm64-v8a";
    }
}