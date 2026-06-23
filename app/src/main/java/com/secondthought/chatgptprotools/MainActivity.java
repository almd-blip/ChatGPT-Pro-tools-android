package com.secondthought.chatgptprotools;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
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
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int CREATE_DOCUMENT_REQUEST_CODE = 1001;

    private EditText linkInput;
    private EditText titleInput;
    private EditText tagsInput;
    private EditText searchInput;
    private EditText chatInput;
    private TextView statusText;
    private TextView libraryText;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<ChatRecord> lastSearchResults = new ArrayList<>();
    private String pendingExportText = "";
    private String pendingExportFileName = "chat-export.txt";

    private static class ChatRecord {
        String id;
        String title;
        String tags;
        String date;
        String sourceLink;
        String fileName;

        static ChatRecord fromJson(JSONObject object) {
            ChatRecord record = new ChatRecord();
            record.id = object.optString("id");
            record.title = object.optString("title");
            record.tags = object.optString("tags");
            record.date = object.optString("date");
            record.sourceLink = object.optString("sourceLink");
            record.fileName = object.optString("fileName");
            return record;
        }

        JSONObject toJson() throws Exception {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("title", title);
            object.put("tags", tags);
            object.put("date", date);
            object.put("sourceLink", sourceLink);
            object.put("fileName", fileName);
            return object;
        }
    }

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
        intro.setText("Import ChatGPT share links, save chats locally, search your archive, and export text files. Paste still works as a fallback when a share page cannot be read.");
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(16));
        layout.addView(intro);

        titleInput = new EditText(this);
        titleInput.setHint("Title for this chat");
        titleInput.setSingleLine(true);
        titleInput.setTextSize(16);
        layout.addView(titleInput, matchWrap());

        tagsInput = new EditText(this);
        tagsInput.setHint("Tags, e.g. Still Becoming, KDP, AI training");
        tagsInput.setSingleLine(true);
        tagsInput.setTextSize(16);
        layout.addView(tagsInput, matchWrap());

        linkInput = new EditText(this);
        linkInput.setHint("Paste one or more ChatGPT share links, one per line");
        linkInput.setMinLines(2);
        linkInput.setGravity(Gravity.TOP | Gravity.START);
        linkInput.setTextSize(16);
        linkInput.setSingleLine(false);
        layout.addView(linkInput, matchWrap());

        Button importButton = new Button(this);
        importButton.setText("Import and save link(s)");
        importButton.setOnClickListener(v -> importFromShareLinks());
        layout.addView(importButton);

        searchInput = new EditText(this);
        searchInput.setHint("Search saved chats");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16);
        layout.addView(searchInput, matchWrap());

        Button searchButton = new Button(this);
        searchButton.setText("Search archive");
        searchButton.setOnClickListener(v -> searchSavedChats());
        layout.addView(searchButton);

        Button loadLatestButton = new Button(this);
        loadLatestButton.setText("Load latest saved chat");
        loadLatestButton.setOnClickListener(v -> loadLatestSavedChat());
        layout.addView(loadLatestButton);

        Button loadFirstSearchButton = new Button(this);
        loadFirstSearchButton.setText("Load first search result");
        loadFirstSearchButton.setOnClickListener(v -> loadFirstSearchResult());
        layout.addView(loadFirstSearchButton);

        statusText = new TextView(this);
        statusText.setText("Ready.");
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(8), 0, dp(12));
        layout.addView(statusText);

        chatInput = new EditText(this);
        chatInput.setHint("Imported, loaded, or pasted chat text will appear here");
        chatInput.setMinLines(16);
        chatInput.setGravity(Gravity.TOP | Gravity.START);
        chatInput.setTextSize(16);
        chatInput.setSingleLine(false);
        layout.addView(chatInput, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("Save current chat locally");
        saveButton.setOnClickListener(v -> saveCurrentChatFromUi());
        layout.addView(saveButton);

        Button exportButton = new Button(this);
        exportButton.setText("Export current chat as .txt");
        exportButton.setOnClickListener(v -> exportCurrentAsTxt());
        layout.addView(exportButton);

        Button shareButton = new Button(this);
        shareButton.setText("Share current chat text");
        shareButton.setOnClickListener(v -> shareCurrentText());
        layout.addView(shareButton);

        Button cleanButton = new Button(this);
        cleanButton.setText("Clean spacing");
        cleanButton.setOnClickListener(v -> cleanSpacing());
        layout.addView(cleanButton);

        Button copyButton = new Button(this);
        copyButton.setText("Copy cleaned text");
        copyButton.setOnClickListener(v -> copyText());
        layout.addView(copyButton);

        libraryText = new TextView(this);
        libraryText.setTextSize(14);
        libraryText.setPadding(0, dp(16), 0, 0);
        layout.addView(libraryText);

        setContentView(scrollView);
        searchSavedChats();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void importFromShareLinks() {
        String input = linkInput.getText().toString().trim();
        if (input.length() == 0) {
            showToast("Paste at least one share link first");
            return;
        }

        ArrayList<String> links = new ArrayList<>();
        for (String line : input.split("\\n")) {
            String value = line.trim();
            if (value.startsWith("https://")) {
                links.add(value);
            }
        }

        if (links.isEmpty()) {
            showToast("Use full https share links, one per line");
            return;
        }

        String manualTitle = titleInput.getText().toString().trim();
        String tags = tagsInput.getText().toString().trim();
        setStatus("Importing " + links.size() + " link(s)...");

        new Thread(() -> {
            int savedCount = 0;
            String firstText = "";
            String firstTitle = "";
            StringBuilder errors = new StringBuilder();

            for (String link : links) {
                try {
                    String html = fetchUrl(link);
                    String extracted = extractConversationText(html);
                    if (extracted.trim().length() == 0) {
                        extracted = "Imported page, but conversation text could not be confidently extracted.\n\nSource: " + link;
                    }

                    String derivedTitle = extractHtmlTitle(html);
                    String title = manualTitle.length() > 0 && links.size() == 1
                            ? manualTitle
                            : derivedTitle.length() > 0 ? derivedTitle : "Imported chat";

                    String finalText = cleanText(extracted);
                    saveChatRecord(title, tags, link, finalText);
                    savedCount++;
                    if (firstText.length() == 0) {
                        firstText = finalText;
                        firstTitle = title;
                    }
                } catch (Exception e) {
                    errors.append("Import failed for ").append(link).append(": ").append(e.getMessage()).append("\n");
                }
            }

            int finalSavedCount = savedCount;
            String finalFirstText = firstText;
            String finalFirstTitle = firstTitle;
            String finalErrors = errors.toString().trim();

            mainHandler.post(() -> {
                if (finalFirstText.length() > 0) {
                    chatInput.setText(finalFirstText);
                    titleInput.setText(finalFirstTitle);
                    chatInput.setSelection(chatInput.getText().length());
                }
                setStatus("Imported and saved " + finalSavedCount + " chat(s)." + (finalErrors.length() > 0 ? " Some links failed." : ""));
                if (finalErrors.length() > 0) {
                    libraryText.setText(finalErrors);
                }
                searchSavedChats();
            });
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

    private String extractHtmlTitle(String html) {
        Matcher matcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (matcher.find()) {
            String title = Html.fromHtml(matcher.group(1)).toString();
            title = title.replace(" - ChatGPT", "").replace(" | ChatGPT", "");
            return cleanText(title);
        }
        return "";
    }

    private boolean looksLikeConversationText(String text) {
        if (text.length() < 20) return false;
        String lower = text.toLowerCase(Locale.UK);
        if (lower.contains("webpack") || lower.contains("__next") || lower.contains("manifest")) return false;
        if (lower.startsWith("http") && text.length() < 120) return false;
        return true;
    }

    private String removeLikelyPageChrome(String text) {
        return text
                .replaceAll("(?i)log in|sign up|new chat|upgrade", "")
                .trim();
    }

    private void saveCurrentChatFromUi() {
        String text = cleanText(chatInput.getText().toString());
        if (text.length() == 0) {
            showToast("There is no chat text to save yet");
            return;
        }
        String title = titleInput.getText().toString().trim();
        if (title.length() == 0) {
            title = "Saved chat";
        }
        String tags = tagsInput.getText().toString().trim();
        String source = linkInput.getText().toString().trim();
        try {
            saveChatRecord(title, tags, source, text);
            setStatus("Saved current chat locally.");
            searchSavedChats();
        } catch (Exception e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private void saveChatRecord(String title, String tags, String sourceLink, String text) throws Exception {
        File chatDir = getChatDir();
        String id = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.UK).format(new Date());
        String fileName = id + ".txt";
        writeTextFile(new File(chatDir, fileName), text);

        ChatRecord record = new ChatRecord();
        record.id = id;
        record.title = title;
        record.tags = tags;
        record.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(new Date());
        record.sourceLink = sourceLink;
        record.fileName = fileName;

        JSONArray index = readIndex();
        index.put(record.toJson());
        writeIndex(index);
    }

    private void searchSavedChats() {
        try {
            String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.UK);
            JSONArray index = readIndex();
            lastSearchResults.clear();
            StringBuilder display = new StringBuilder();
            display.append("Saved chats: ").append(index.length()).append("\n");

            for (int i = index.length() - 1; i >= 0; i--) {
                ChatRecord record = ChatRecord.fromJson(index.getJSONObject(i));
                String text = readChatText(record);
                String haystack = (record.title + " " + record.tags + " " + record.date + " " + text).toLowerCase(Locale.UK);
                if (query.length() == 0 || haystack.contains(query)) {
                    lastSearchResults.add(record);
                }
            }

            display.append("Matching results: ").append(lastSearchResults.size()).append("\n\n");
            int limit = Math.min(lastSearchResults.size(), 10);
            for (int i = 0; i < limit; i++) {
                ChatRecord record = lastSearchResults.get(i);
                display.append(i + 1).append(". ").append(record.title).append("\n")
                        .append("   ").append(record.date).append("\n");
                if (record.tags.length() > 0) {
                    display.append("   Tags: ").append(record.tags).append("\n");
                }
                display.append("\n");
            }
            if (lastSearchResults.size() > 10) {
                display.append("Showing first 10 results. Refine your search to narrow it down.");
            }
            libraryText.setText(display.toString());
        } catch (Exception e) {
            if (libraryText != null) {
                libraryText.setText("Could not read saved chats: " + e.getMessage());
            }
        }
    }

    private void loadLatestSavedChat() {
        try {
            JSONArray index = readIndex();
            if (index.length() == 0) {
                showToast("No saved chats yet");
                return;
            }
            ChatRecord record = ChatRecord.fromJson(index.getJSONObject(index.length() - 1));
            loadRecord(record);
        } catch (Exception e) {
            setStatus("Load failed: " + e.getMessage());
        }
    }

    private void loadFirstSearchResult() {
        if (lastSearchResults.isEmpty()) {
            searchSavedChats();
        }
        if (lastSearchResults.isEmpty()) {
            showToast("No search result to load");
            return;
        }
        try {
            loadRecord(lastSearchResults.get(0));
        } catch (Exception e) {
            setStatus("Load failed: " + e.getMessage());
        }
    }

    private void loadRecord(ChatRecord record) throws Exception {
        titleInput.setText(record.title);
        tagsInput.setText(record.tags);
        linkInput.setText(record.sourceLink);
        chatInput.setText(readChatText(record));
        chatInput.setSelection(chatInput.getText().length());
        setStatus("Loaded: " + record.title);
    }

    private JSONArray readIndex() throws Exception {
        File indexFile = getIndexFile();
        if (!indexFile.exists()) {
            return new JSONArray();
        }
        String content = readTextFile(indexFile);
        if (content.trim().length() == 0) {
            return new JSONArray();
        }
        return new JSONArray(content);
    }

    private void writeIndex(JSONArray index) throws Exception {
        writeTextFile(getIndexFile(), index.toString(2));
    }

    private String readChatText(ChatRecord record) throws Exception {
        return readTextFile(new File(getChatDir(), record.fileName));
    }

    private File getChatDir() {
        File dir = new File(getFilesDir(), "saved_chats");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getIndexFile() {
        return new File(getChatDir(), "index.json");
    }

    private String readTextFile(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        reader.close();
        return builder.toString();
    }

    private void writeTextFile(File file, String text) throws Exception {
        FileOutputStream stream = new FileOutputStream(file);
        stream.write(text.getBytes("UTF-8"));
        stream.flush();
        stream.close();
    }

    private void exportCurrentAsTxt() {
        String text = cleanText(chatInput.getText().toString());
        if (text.length() == 0) {
            showToast("There is no chat text to export yet");
            return;
        }
        pendingExportText = text;
        String title = titleInput.getText().toString().trim();
        if (title.length() == 0) {
            title = "chat-export";
        }
        pendingExportFileName = safeFileName(title) + ".txt";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, pendingExportFileName);
        startActivityForResult(intent, CREATE_DOCUMENT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOCUMENT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                setStatus("Export failed: no file selected.");
                return;
            }
            try {
                OutputStream stream = getContentResolver().openOutputStream(uri);
                if (stream == null) {
                    throw new Exception("Could not open selected file");
                }
                stream.write(pendingExportText.getBytes("UTF-8"));
                stream.flush();
                stream.close();
                setStatus("Exported: " + pendingExportFileName);
            } catch (Exception e) {
                setStatus("Export failed: " + e.getMessage());
            }
        }
    }

    private void shareCurrentText() {
        String text = cleanText(chatInput.getText().toString());
        if (text.length() == 0) {
            showToast("There is no chat text to share yet");
            return;
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(sendIntent, "Share chat text"));
    }

    private String safeFileName(String value) {
        String cleaned = value.replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("-+", "-");
        cleaned = cleaned.replaceAll("^-|-$", "");
        if (cleaned.length() == 0) {
            cleaned = "chat-export";
        }
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        return cleaned;
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
        String text = cleanText(chatInput.getText().toString());
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
