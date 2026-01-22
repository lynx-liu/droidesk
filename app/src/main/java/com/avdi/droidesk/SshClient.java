package com.avdi.droidesk;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SshClient {
    private static final String TAG = "SshClient";

    private final File workDir;
    private final String sshPath;
    private final String keyFileName;

    public static class Result {
        public final int exitCode;
        public final String stdout;

        public Result(int exitCode, String stdout) {
            this.exitCode = exitCode;
            this.stdout = stdout;
        }
    }

    public SshClient(Context ctx, String keyFileName) {
        this.workDir = ctx.getFilesDir();
        this.sshPath = new File(workDir, "ssh").getAbsolutePath();
        this.keyFileName = keyFileName;
    }

    private Result execLocal(List<String> args, long timeoutMs) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(workDir);

            // 关键：把 HOME 指向应用私有目录，避免 ssh 默认尝试使用 /data/.ssh
            pb.environment().put("HOME", workDir.getAbsolutePath());
            pb.environment().put("USER", "root");
            pb.environment().put("LD_LIBRARY_PATH", workDir.getAbsolutePath());

            // 不合并 stderr，分开读取，避免警告信息污染 stdout
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            // 分别读取 stdout 和 stderr
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            String line;
            while ((line = stdoutReader.readLine()) != null) {
                out.append(line).append('\n');
            }
            while ((line = stderrReader.readLine()) != null) {
                // stderr 只打日志，不混入返回值
                Log.w(TAG, line);
                err.append(line).append('\n');
            }

            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                try { p.destroy(); } catch (Throwable ignored) {}
                try { p.waitFor(200, TimeUnit.MILLISECONDS); } catch (Throwable ignored) {}
                try { p.destroyForcibly(); } catch (Throwable ignored) {}
            }

            int code = finished ? p.exitValue() : -1;
            String stdout = out.toString().trim();
            return new Result(code, stdout);
        } catch (Exception e) {
            return new Result(-1, e.toString());
        }
    }

    public Result runRemoteCommand(String userAtHost, String remoteCmd, long timeoutMs) {
        List<String> args = new ArrayList<>();
        args.add(sshPath);
        args.add("-T");
        args.add("-i");
        args.add(keyFileName);

        // 与 runSSHForward 保持一致：使用 /dev/null 避免 ssh 尝试创建 /data/.ssh
        args.add("-o"); args.add("UserKnownHostsFile=/dev/null");
        args.add("-o"); args.add("StrictHostKeyChecking=no");

        args.add(userAtHost);
        args.add(remoteCmd);

        Result r = execLocal(args, timeoutMs);
        if(r.exitCode!=0) {
            Log.d(TAG, r.stdout+", exit: " + r.exitCode);
        }
        return r;
    }

    public int findFreePort(String userAtHost, int startPort) {
         int endPort = 65535;

        String remoteCmd = "sh -c '" +
                "for p in $(seq " + startPort + " " + endPort + "); do " +
                "(ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | grep -Eq \"[:.]${p}\\b\" " +
                "&& continue || { echo ${p}; exit 0; }; " +
                "done; exit 1'";

        Result r = runRemoteCommand(userAtHost, remoteCmd, 15000);
        if (r.exitCode == 0) {
            try {
                // Be defensive: extract first integer anywhere in stdout.
                String v = r.stdout == null ? "" : r.stdout;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(v);
                if (m.find()) {
                    int p = Integer.parseInt(m.group(1));
                    if (p >= startPort && p <= endPort) return p;
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }
}
