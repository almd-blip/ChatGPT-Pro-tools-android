package com.secondthought.chatgptprotools;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Html;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private EditText linkInput;
    private EditText chatInput;
    private TextView statusText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        intro.setText("Import a public ChatGPT share link, or paste copied chat text below. The helper tries to turn the conversation into clean plain text that you can copy into notes, documents, or a future searchable archive.");
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(16));
        layout.addView(intro);

        linkInput = new EditText(this);
        linkInput.setHint("Paste ChatGPT share link here");
        linkInput.setSingleLine(true);
        linkInput.setTextSize(16);
        layout.addView(linkInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button importButton = new Button(this);
        importButton.setText("Import from share link");
        importButton.setOnClickListener(v -> importFromShareLink());
        layout.addView(importButton);

        statusText = new TextView(this);
        statusText.setText("Ready.");
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(8), 0, dp(12));
        layout.addView(statusText);

        chatInput = new EditText(this);
        chatInput.setHint("Imported or pasted chat text will appear here");
        chatInput.setMinLines(14);
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
        note.setText("Tip: use a ChatGPT share link for long conversations. Selecting and copying a long page can miss hidden or unloaded parts of the chat.");
        note.setTextSize(14);
        note.setPadding(0, dp(16), 0, 0);
        layout.addView(note);

        setContentView(scrollView);
    }

    private void importFromShareLink() {
        String url = linkInput.getText().toString().trim();
        if (url.length() == 0) {
            showToast("Paste a share link first");
            return;
        }
        if (!url.startsWith("https://")) {
            showToast("Use the full https share link");
            return;
        }

        setStatus("Importing share link...");

        new Thread(() -> {
            try {
                String html = fetchUrl(url);
                String extracted = extractConversationText(html);
                if (extracted.trim().length() == 0) {
                    extracted = "I imported the page, but could not confidently extract the conversation text.\n\n" +
                            "This may happen if the share page requires sign-in, has changed format, or loads the conversation in a way this early version cannot read yet.";
                }
                String finalText = cleanText(extracted);
                mainHandler.post(() -> {
                    chatInput.setText(finalText);
                    chatInput.setSelection(chatInput.getText().length());
                    setStatus("Imported " + finalText.length() + " characters.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> setStatus("Import failed: " + e.getMessage()));
            }
        }).start();
    }

    private String fetchUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android Chat Export Helper");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            throw new Exception("No response from server");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        reader.close();

        if (code >= 400) {
            throw new Exception("Server returned HTTP " + code);
        }
        return builder.toString();
    }

    private String extractConversationText(String html) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();

        addRegexMatches(parts, html, "\\\"parts\\\"\\s*:\\s*\\[(\\\"(?:\\\\.|[^\\\"\\\\])*\\\")\\]");
        addRegexMatches(parts, html, "\"parts\"\\s*:\\s*\\[(\"(?:\\\\.|[^\"\\\\])*\")\\]");
        addRegexMatches(parts, html, "\\\"text\\\"\\s*:\\s*(\\\"(?:\\\\.|[^\\\"\\\\])*\\\")");
        addRegexMatches(parts, html, "\"text\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");

        ArrayList<String> cleanedParts = new ArrayList<>();
        for (String part : parts) {
            String decoded = jsonUnquote(part);
            decoded = cleanText(decoded);
            if (looksLikeConversationText(decoded)) {
                cleanedParts.add(decoded);
            }
        }

        if (!cleanedParts.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (String part : cleanedParts) {
                if (builder.length() > 0) {
                    builder.append("\n\n---\n\n");
                }
                builder.append(part);
            }
            return builder.toString();
        }

        String visible = Html.fromHtml(html).toString();
        visible = cleanText(visible);
        return removeLikelyPageChrome(visible);
    }

    private void addRegexMatches(LinkedHashSet<String> parts, String html, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            parts.add(matcher.group(1));
        }
    }

    private String jsonUnquote(String value) {
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (Exception ignored) {
            return value
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\u003c", "<")
                    .replace("\\u003e", ">")
                    .replace("\\u0026", "&")
                    .replaceAll("^\"|\"$", "");
        }
    }

    private boolean looksLikeConversationText(String text) {
        if (text.length() < 20) return false;
        String lower = text.toLowerCase();
        if (lower.contains("webpack") || lower.contains("__next") || lower.contains("manifest")) return false;
        if (lower.startsWith("http") && text.length() < 120) return false;
        return true;
    }

    private String removeLikelyPageChrome(String text) {
        return text
                .replace("ChatGPT", "ChatGPT")
                .replaceAll("(?i)log in|sign up|new chat|share|upgrade", "")
                .trim();
    }

    private void cleanSpacing() {
        String cleaned = cleanText(chatInput.getText().toString());
        chatInput.setText(cleaned);
        chatInput.setSelection(chatInput.getText().length());
        showToast("Spacing cleaned");
    }

    private String cleanText(String text) {
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private void copyText() {
        String text = chatInput.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("chat-export", text));
        showToast("Copied");
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
