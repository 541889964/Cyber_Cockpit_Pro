package com.ai.cockpit;
import java.io.*;
import rikka.shizuku.Shizuku;

public class Engine {
    public static boolean isDangerous(String cmd) {
        String c = cmd.toLowerCase();
        return c.contains("rm -rf") || c.contains("reboot") || c.contains("mkfs") || c.contains("dd if=");
    }

    public static String run(int mode, String cmd) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = null;
            if (mode == 1) {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            } else if (mode == 2 && Shizuku.pingBinder()) {
                // 使用反射调用 Shizuku.newProcess，解决 13.1.5 版本编译报错
                try {
                    Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                    java.lang.reflect.Method method = shizukuClass.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                    method.setAccessible(true);
                    p = (Process) method.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
                } catch (Exception e) {
                    return "Shizuku 反射调用失败: " + e.getMessage();
                }
            } else {
                p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            }

            if (p != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String line;
                while ((line = r.readLine()) != null) out.append(line).append("\n");
                while ((line = e.readLine()) != null) out.append("[ERR] ").append(line).append("\n");
                p.waitFor();
            }
        } catch (Exception ex) { 
            out.append("执行异常: ").append(ex.getMessage()); 
        }
        return out.toString().trim();
    }
}
