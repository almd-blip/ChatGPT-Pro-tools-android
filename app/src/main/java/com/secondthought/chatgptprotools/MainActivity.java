package com.secondthought.chatgptprotools;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText chatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = dp(20);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        scrollView.addView(layout);

        TextView title = new TextView(this);
        title.setText("Chat Export Helper");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START);
        layout.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Paste copied ChatGPT conversation text below. The helper tidies spacing and gives you a plain text version that you can copy into a note, document, or future viewer.");
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(16));
        layout.addView(intro);

        chatInput = new EditText(this);
        chatInput.setHint("Paste chat text here");
        chatInput.setMinLines(10);
        chatInput.setGravity(Gravity.TOP | Gravity.START);
        chatInput.setTextSize(16);
        chatInput.setSingleLine(false);
        layout.addView(chatInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button cleanButton = new Button(this);
        cleanButton.setText("Clean spacing");
        cleanButton.setOnClickListener(v -> cleanSpacing());
        layout.addView(cleanButton);

        Button copyButton = new Button(this);
        copyButton.setText("Copy cleaned text");
        copyButton.setOnClickListener(v -> copyText());
        layout.addView(copyButton);

        TextView note = new TextView(this);
        note.setText("This first APK is a simple offline helper. Later versions can add import, search, indexing, tags, PDF export, and a more linear chat viewer.");
        note.setTextSize(14);
        note.setPadding(0, dp(16), 0, 0);
        layout.addView(note);

        setContentView(scrollView);
    }

    private void cleanSpacing() {
        String text = chatInput.getText().toString();
        String cleaned = text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        chatInput.setText(cleaned);
        chatInput.setSelection(chatInput.getText().length());
        Toast.makeText(this, "Spacing cleaned", Toast.LENGTH_SHORT).show();
    }

    private void copyText() {
        String text = chatInput.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("cleaned-chat-export", text));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
