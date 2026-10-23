package com.ai.cockpit;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private List<String[]> messages = new ArrayList<>();
    private EditText etMsg, etKey;
    private Spinner spinnerMode, spinnerModel, spinnerEngine;
    private TextView tvSysInfo;
    private SharedPreferences sp;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().readTimeout(300, java.util.concurrent.TimeUnit.SECONDS).build();
    
    private int currentPermMode = 1;
    private int currentEngine = 0;
    private String currentModel = "deepseek-chat";
    private int loopCount = 0;
    private static final int MAX_LOOP = 20; // 不限制重试，直到成功

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全屏沉浸式
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_main);
        
        sp = getSharedPreferences("Cockpit", Context.MODE_PRIVATE);
        rvChat = findViewById(R.id.rvChat);
        etMsg = findViewById(R.id.etMsg);
        etKey = findViewById(R.id.etKey);
        spinnerMode = findViewById(R.id.spinnerMode);
        spinnerModel = findViewById(R.id.spinnerModel);
        spinnerEngine = findViewById(R.id.spinnerEngine);
        tvSysInfo = findViewById(R.id.tvSysInfo);
        
        Button btnSend = findViewById(R.id.btnSend);
        Button btnSaveKey = findViewById(R.id.btnSaveKey);

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        etKey.setText(sp.getString("api_key", ""));
        addMessage("System", "Cockpit 矩阵启动完毕。内存与性能已解锁。");

        updateSysInfo();

        String[] modes = {"普通权限", "ROOT 直连", "SHIZUKU ADB"};
        spinnerMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        spinnerMode.setSelection(1);
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentPermMode = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] engines = {"LOCAL 内核", "CLOUD 沙盒", "GITHUB 云编译"};
        spinnerEngine.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, engines));
        spinnerEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentEngine = pos; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] models = {"V3 极速", "R1 深思"};
        spinnerModel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, models));
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { currentModel = (pos == 0) ? "deepseek-chat" : "deepseek-reasoner"; }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnSaveKey.setOnClickListener(v -> { 
            sp.edit().putString("api_key", etKey.getText().toString().trim()).apply(); 
            Toast.makeText(this, "LINK ESTABLISHED", Toast.LENGTH_SHORT).show(); 
        });

        btnSend.setOnClickListener(v -> {
            String msg = etMsg.getText().toString().trim();
            if (msg.isEmpty()) return;
            etMsg.setText("");
            addMessage("Me", msg);
            loopCount = 0; // 重置纠错循环
            callAPI(msg, null, false);
        });
    }

    private void updateSysInfo() {
        uiHandler.postDelayed(() -> {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            tvSysInfo.setText("SYSTEM ONLINE  |  BAT: " + battery + "%  |  ANDROID: " + Build.VERSION.RELEASE);
            updateSysInfo();
        }, 3000);
    }

    private void addMessage(String role, String content) {
        uiHandler.post(() -> {
            messages.add(new String[]{role, content});
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    private void updateLastMessage(String append) {
        uiHandler.post(() -> {
            if (messages.isEmpty()) return;
            String[] last = messages.get(messages.size() - 1);
            if (last[0].startsWith("Agent")) {
                last[1] += append; adapter.notifyItemChanged(messages.size() - 1);
                rvChat.scrollToPosition(messages.size() - 1);
            }
        });
    }

    private void callAPI(String question, String execResult, boolean isLoop) {
        String key = sp.getString("api_key", "");
        if (key.isEmpty()) { addMessage("System", "API KEY 缺失"); return; }
        if (!isLoop) addMessage("Agent", "正在解析指令...\n");

        try {
            JSONObject json = new JSONObject();
            json.put("model", currentModel);
            json.put("stream", true);
            JSONArray msgs = new JSONArray();
            
            String sys = "你是超级安卓AI。执行系统操作用：[EXEC] 命令 或 [COMPILE] 语言\n代码\n[/COMPILE]。";
            if (isLoop) sys += "\n仔细看反馈结果，如果没有彻底完成，请继续发指令修复！禁止放弃。";
            
            msgs.put(new JSONObject().put("role", "system").put("content", sys));
            msgs.put(new JSONObject().put("role", "user").put("content", question));
            if (isLoop && execResult != null) {
                msgs.put(new JSONObject().put("role", "assistant").put("content", "上步终端反馈：\n" + execResult + "\n继续执行或修复："));
            }
            json.put("messages", msgs);

            Request req = new Request.Builder().url("https://api.deepseek.com/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(json.toString(), MediaType.parse("application/json"))).build();

            client.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { updateLastMessage("\n[网络连接断开]"); }
                @Override public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful() || response.body() == null) { updateLastMessage("\n[API 节点拒绝]"); return; }
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        String line; StringBuilder full = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                                JSONObject chunk = new JSONObject(line.substring(6));
                                JSONObject delta = chunk.getJSONArray("choices").getJSONObject(0).optJSONObject("delta");
                                if (delta != null && delta.has("reasoning_content")) {
                                    updateLastMessage(delta.getString("reasoning_content"));
                                }
                                if (delta != null && delta.has("content") && !delta.getString("content").isEmpty()) {
                                    String c = delta.getString("content");
                                    updateLastMessage(c); full.append(c);
                                }
                            }
                        }
                        executeAction(full.toString(), question);
                    } catch (Exception e) {}
                }
            });
        } catch (Exception e) {}
    }

    private void executeAction(String reply, String originalQuestion) {
        if (reply.contains("[TASK_DONE]")) { addMessage("System", "任务完美闭环。"); return; }

        if (reply.contains("[EXEC]")) {
            int start = reply.indexOf("[EXEC]") + 6;
            int end = reply.indexOf("\n", start);
            String cmd = (end == -1 ? reply.substring(start) : reply.substring(start, end)).replace("`", "").trim();
            
            if (Engine.isDangerous(cmd)) {
                uiHandler.post(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("⚠️ 高危操作警报")
                        .setMessage("AI 试图执行核心破坏命令：\n\n" + cmd)
                        .setPositiveButton("强制放行", (d, w) -> executeTerminal(cmd, originalQuestion))
                        .setNegativeButton("拒绝", (d, w) -> callAPI(originalQuestion, "用户拦截了此操作", true))
                        .show();
                });
                return;
            }
            executeTerminal(cmd, originalQuestion);
        }

        if (reply.contains("[COMPILE]") && reply.contains("[/COMPILE]")) {
            int start = reply.indexOf("[COMPILE]") + 9;
            int end = reply.indexOf("[/COMPILE]");
            String raw = reply.substring(start, end).trim();
            String lang = raw.startsWith("python") ? "python" : raw.startsWith("c") ? "c" : "sh";
            String code = raw.substring(raw.indexOf("\n")).replace("`", "").trim();

            addMessage("System", ">>> 启动 [" + lang + "] 编译引擎...");
            new Thread(() -> {
                String res = "";
                if (currentEngine == 0) {
                    File f = new File("/sdcard/CyberCockpit/snap_" + System.currentTimeMillis() + "." + (lang.equals("python") ? "py" : lang.equals("c") ? "c" : "sh"));
                    f.getParentFile().mkdirs();
                    try (FileWriter w = new FileWriter(f)) { w.write(code); } catch (Exception e) {}
                    String cmd = lang.equals("python") ? "python " + f.getAbsolutePath() : lang.equals("c") ? "gcc " + f.getAbsolutePath() + " -o /sdcard/CyberCockpit/a.out && /sdcard/CyberCockpit/a.out" : "sh " + f.getAbsolutePath();
                    res = Engine.run(currentPermMode, cmd);
                } else {
                    res = "云端代码沙盒运行成功 (模拟返回)。";
                }
                addMessage("System", ">>> 编译/运行结果:\n" + res);
                
                if (loopCount < MAX_LOOP) {
                    loopCount++;
                    callAPI(originalQuestion, res, true);
                }
            }).start();
        }
    }

    private void executeTerminal(String cmd, String originalQuestion) {
        addMessage("System", ">>> 正在执行底层指令: " + cmd);
        new Thread(() -> {
            String res = Engine.run(currentPermMode, cmd);
            addMessage("System", ">>> 终端反馈:\n" + res);
            
            if (loopCount < MAX_LOOP) {
                loopCount++;
                callAPI(originalQuestion, res, true);
            }
        }).start();
    }
}
