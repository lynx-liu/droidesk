package com.avdi.droidesk;

import static java.lang.System.getProperty;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class ForwardUtils extends Thread{
    // 默认 SSH 远端服务器
    private static final String DEFAULT_SSH_REMOTE = "root@10.86.32.34";

    // 私钥文件名（assets 根目录下同名文件）
    private static final String SSH_KEY_FILE = "id_ed25519";
    private final Context context;

    public ForwardUtils(Context context) {
        super();
        this.context = context;
    }

    @Override
    public void run() {
        super.run();

        if(isRunning("ssh"))
            return;

        if(!installSSH(context))
            return;

        int remotePort = getRemotePort(context, 5555);
        Log.d("llx", "remotePort=" + remotePort);
        if(remotePort<1) return;

        runSSHForward(context, remotePort);
    }

    private static boolean isRunning(String processName) {
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
    
    private static boolean installSSH(Context context) {
        String abi = getPrimaryAbi();

        File destDir = context.getFilesDir();
        String[] names = new String[]{"ssh", "libssh.so", "libssl.so", "libcrypto.so"};
        for (String name : names) {
            String assetPath = abi + "/" + name;
            File destFile = new File(destDir, name);
            if (!copyAssetToFile(context, assetPath, destFile))
                return false;
            destFile.setExecutable(true);
            destFile.setReadable(true);
            destFile.setWritable(true);
        }
        return copyAssetToFile(context, SSH_KEY_FILE, new File(destDir, SSH_KEY_FILE));
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

    // 优先选择 arm64-v8a（如果设备支持）
    private static String getPrimaryAbi() {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis != null) {
            for (String a : abis) {
                if ("arm64-v8a".equals(a)) return "arm64-v8a";
            }
            if (abis.length > 0) return abis[0];
        }
        // 兜底
        return "arm64-v8a";
    }

    /**
     * 启动远程端口转发：在远端暴露 remotePort，并转发到本地 127.0.0.1:localPort。
     * 使用 assets 中打包的 ssh（启动时会自动拷贝到 filesDir 并赋可执行权限）。
     */
    public static void runSSHForward(Context context, int serverport) {
        String keyfile = "id_ed25519";
        File home = context.getFilesDir();

        String localPort = getProperty("service.adb.tcp.port", "5555");
        String sshPath = new File(home, "ssh").getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(
                sshPath,
                "-T",
                "-i", keyfile,
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "StrictHostKeyChecking=no",
                "-R", serverport+":127.0.0.1:"+localPort,
                DEFAULT_SSH_REMOTE
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

    private static int getRemotePort(Context context, int startPort) {
        SshClient client = new SshClient(context, SSH_KEY_FILE);
        return client.findFreePort(DEFAULT_SSH_REMOTE, startPort);
    }
}
