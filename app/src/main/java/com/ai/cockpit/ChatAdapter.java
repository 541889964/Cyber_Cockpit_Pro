package com.ai.cockpit;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<String[]> messages;

    public ChatAdapter(List<String[]> messages) { this.messages = messages; }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String[] msg = messages.get(position);
        boolean isUser = msg[0].startsWith("Me");
        boolean isSystem = msg[0].startsWith("System");

        holder.tvRole.setText(isUser ? "OPERATOR" : isSystem ? "TERMINAL" : "AI_CORE");
        holder.tvRole.setTextColor(isUser ? 0xFF00F0FF : isSystem ? 0xFFFFD54F : 0xFF10B981);
        
        holder.tvRole.setGravity(isUser ? Gravity.END : Gravity.START);
        holder.tvContent.setGravity(isUser ? Gravity.END : Gravity.START);
        
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.tvContent.getLayoutParams();
        params.gravity = isUser ? Gravity.END : Gravity.START;
        holder.tvContent.setLayoutParams(params);

        holder.tvContent.setBackgroundResource(isUser ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_ai);
        holder.tvContent.setTextColor(isUser ? Color.BLACK : 0xFFE2E8F0);

        // 极简 Markdown 代码高亮渲染
        holder.tvContent.setText(formatText(msg[1]));

        holder.itemView.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("AI", msg[1]));
            Toast.makeText(v.getContext(), "已复制代码/日志", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private SpannableString formatText(String text) {
        SpannableString spannable = new SpannableString(text);
        // 简单的行内代码高亮逻辑
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                if (start == -1) start = i;
                else {
                    spannable.setSpan(new ForegroundColorSpan(0xFF00F0FF), start, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), start, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = -1;
                }
            }
        }
        return spannable;
    }

    @Override public int getItemCount() { return messages.size(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRole, tvContent;
        ViewHolder(View v) { super(v); tvRole = v.findViewById(R.id.tvRole); tvContent = v.findViewById(R.id.tvContent); }
    }
}
