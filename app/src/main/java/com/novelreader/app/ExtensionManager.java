package com.novelreader.app;

import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic manifest-based extension host. Extensions provide data and declare supported host actions. */
final class ExtensionManager {
    static final int API_VERSION = 1;
    static final String TEXT_REPLACEMENT = "chapter.text.transform.pairs.v1";
    private static final String MANIFEST_FILE = "extension.json";
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_FILES = 24;
    private static final int MAX_ACTIONS = 32;

    private final NovelStorage storage;
    private final SharedPreferences preferences;

    ExtensionManager(NovelStorage storage, SharedPreferences preferences) {
        this.storage = storage;
        this.preferences = preferences;
    }

    List<Plugin> installed() {
        List<Plugin> plugins = new ArrayList<>();
        for (String id : storage.listPluginIds()) {
            try {
                Plugin plugin = load(id);
                if (plugin != null) plugins.add(plugin);
            } catch (Exception error) {
                android.util.Log.w("NovelReader", "Skipping invalid extension " + id, error);
            }
        }
        Collections.sort(plugins, (left, right) -> left.name.compareToIgnoreCase(right.name));
        return plugins;
    }

    Plugin load(String id) throws Exception {
        String manifestText = storage.readPluginFile(id, MANIFEST_FILE);
        if (manifestText == null) return null;
        JSONObject manifest = new JSONObject(manifestText);
        if (migratePreviousDataManifest(id, manifest)) {
            manifestText = manifest.toString();
            if (!storage.writePluginFile(id, MANIFEST_FILE, manifestText)) throw new IOException(storage.lastError());
        }
        validateManifest(manifest);
        return new Plugin(manifest, manifestText);
    }

    private boolean migratePreviousDataManifest(String id, JSONObject manifest) throws JSONException {
        JSONObject files = manifest.optJSONObject("files");
        if (manifest.has("apiVersion") || manifest.optInt("engineApi", 0) != 2
                || manifest.optInt("rulesSchema", 0) != 3 || files == null
                || files.optJSONObject("rules.json") == null) return false;
        JSONArray actions = new JSONArray();
        JSONObject action = new JSONObject();
        action.put("id", "convert-text");
        action.put("label", manifest.optString("name", "Text conversion"));
        action.put("icon", "scan_text");
        action.put("placement", "chapter_bottom");
        action.put("capability", TEXT_REPLACEMENT);
        action.put("dataFile", "rules.json");
        action.put("confirmTitle", manifest.optString("name", "Text conversion"));
        action.put("confirmMessage", "این افزونه متن فعلی Chapter را تغییر می‌دهد.");
        actions.put(action);
        manifest.put("apiVersion", API_VERSION);
        manifest.put("description", "افزونهٔ تبدیل متن مبتنی بر جفت‌رشته‌ها.");
        manifest.put("actions", actions);
        manifest.remove("engineApi");
        manifest.remove("rulesSchema");
        if (!preferences.contains(enabledKey(id)) && preferences.contains(id + "-enabled")) {
            preferences.edit().putBoolean(enabledKey(id), preferences.getBoolean(id + "-enabled", false)).apply();
        }
        return true;
    }

    boolean isEnabled(String id) {
        return preferences.getBoolean(enabledKey(id), false);
    }

    void setEnabled(String id, boolean enabled) {
        preferences.edit().putBoolean(enabledKey(id), enabled).apply();
    }

    String sourceUrl(String id) {
        return preferences.getString(sourceKey(id), "");
    }

    Plugin install(String manifestUrl) throws Exception {
        URL source = checkedManifestUrl(manifestUrl);
        String manifestText = new String(download(source.toString(), MAX_MANIFEST_BYTES, MANIFEST_FILE), StandardCharsets.UTF_8);
        JSONObject manifest = new JSONObject(manifestText);
        validateManifest(manifest);
        String id = manifest.getString("id");
        JSONObject files = manifest.getJSONObject("files");
        Map<String, String> downloadedFiles = new LinkedHashMap<>();

        for (int i = 0; i < files.length(); i++) {
            String fileName = files.names().getString(i);
            JSONObject descriptor = files.getJSONObject(fileName);
            int maxBytes = Math.max(1, Math.min(MAX_FILE_BYTES, descriptor.optInt("maxBytes", 256 * 1024)));
            URL fileUrl = new URL(source, fileName);
            if (!source.getHost().equalsIgnoreCase(fileUrl.getHost())
                    || !"https".equalsIgnoreCase(fileUrl.getProtocol())) {
                throw new IOException("Extension files must use the manifest's HTTPS host.");
            }
            byte[] fileBytes = download(fileUrl.toString(), maxBytes, fileName);
            if (!descriptor.getString("sha256").equalsIgnoreCase(sha256(fileBytes))) {
                throw new IOException("Checksum mismatch for " + fileName + ".");
            }
            String fileContents = new String(fileBytes, StandardCharsets.UTF_8);
            validateActionData(manifest, fileName, fileContents);
            downloadedFiles.put(fileName, fileContents);
        }

        for (Map.Entry<String, String> file : downloadedFiles.entrySet()) {
            if (!storage.writePluginFile(id, file.getKey(), file.getValue())) {
                throw new IOException(storage.lastError());
            }
        }
        if (!storage.writePluginFile(id, MANIFEST_FILE, manifestText)) throw new IOException(storage.lastError());
        preferences.edit().putString(sourceKey(id), source.toString()).apply();
        return new Plugin(manifest, manifestText);
    }

    boolean colloquializerInstalled() {
        try { return load("colloquializer") != null; }
        catch (Exception ignored) { return false; }
    }

    boolean colloquializerEnabled() { return isEnabled("colloquializer"); }

    void setColloquializerEnabled(boolean enabled) { setEnabled("colloquializer", enabled); }

    String colloquializerRules() throws Exception {
        String content = storage.readPluginFile("colloquializer", "rules.json");
        if (content == null) throw new IOException("Colloquializer rules are missing.");
        validatePairs(content);
        return content;
    }

    String transformColloquializer(String input) throws Exception {
        Plugin plugin = load("colloquializer");
        if (plugin == null || plugin.actions.isEmpty()) throw new IOException("محاوره‌ای‌ساز نصب نشده است.");
        return runTextReplacement(plugin, plugin.actions.get(0), input);
    }

    void saveColloquializerRules(String rulesJson) throws Exception {
        validatePairs(rulesJson);
        byte[] bytes = rulesJson.getBytes(StandardCharsets.UTF_8);
        JSONObject descriptor = new JSONObject();
        descriptor.put("sha256", sha256(bytes));
        descriptor.put("maxBytes", MAX_FILE_BYTES);
        JSONObject files = new JSONObject().put("rules.json", descriptor);
        JSONObject action = new JSONObject();
        action.put("id", "colloquialize");
        action.put("label", "محاوره‌ای‌سازی");
        action.put("icon", "scan_text");
        action.put("placement", "chapter_bottom");
        action.put("capability", TEXT_REPLACEMENT);
        action.put("dataFile", "rules.json");
        action.put("confirmTitle", "محاوره‌ای‌سازی متن");
        action.put("confirmMessage", "متن فعلی Chapter با قوانین محاوره‌ای‌ساز تغییر می‌کند.");
        JSONArray actions = new JSONArray().put(action);
        JSONObject manifest = new JSONObject();
        manifest.put("schema", 1);
        manifest.put("apiVersion", API_VERSION);
        manifest.put("id", "colloquializer");
        manifest.put("name", "محاوره‌ای‌ساز");
        manifest.put("version", "1.0.0");
        manifest.put("description", "قوانین جایگزینی متن فارسی.");
        manifest.put("files", files);
        manifest.put("actions", actions);
        validateManifest(manifest);
        if (!storage.writePluginFile("colloquializer", "rules.json", rulesJson)) {
            throw new IOException(storage.lastError());
        }
        if (!storage.writePluginFile("colloquializer", MANIFEST_FILE, manifest.toString())) {
            throw new IOException(storage.lastError());
        }
        setEnabled("colloquializer", true);
        preferences.edit().remove(sourceKey("colloquializer")).apply();
    }

    boolean removeColloquializer() { return remove("colloquializer"); }

    String runTextReplacement(Plugin plugin, Action action, String input) throws Exception {
        return runTextReplacement(plugin, action, input, false);
    }

    String runTextReplacement(Plugin plugin, Action action, String input,
                              boolean tagVariableChanges) throws Exception {
        if (plugin == null || action == null || !TEXT_REPLACEMENT.equals(action.capability)) {
            throw new IOException("Unsupported extension action.");
        }
        JSONObject file = plugin.files.getJSONObject(action.dataFile);
        String content = storage.readPluginFile(plugin.id, action.dataFile);
        if (content == null) throw new IOException("Extension data is missing: " + action.dataFile);
        if (!file.getString("sha256").equalsIgnoreCase(sha256(content.getBytes(StandardCharsets.UTF_8)))) {
            throw new IOException("Extension data checksum does not match.");
        }
        validatePairs(content);
        return TextReplacementEngine.transform(input, content, tagVariableChanges);
    }

    boolean remove(String id) {
        boolean removed = storage.deletePlugin(id);
        if (removed) preferences.edit().remove(enabledKey(id)).remove(sourceKey(id)).apply();
        return removed;
    }

    private void validateManifest(JSONObject manifest) throws Exception {
        String id = manifest.optString("id");
        if (manifest.optInt("schema", 0) != 1 || manifest.optInt("apiVersion", 0) != API_VERSION
                || !id.matches("[a-z0-9][a-z0-9._-]{0,63}") || id.contains("..")
                || clean(manifest.optString("name"), 80).isEmpty()
                || clean(manifest.optString("version"), 32).isEmpty()) {
            throw new IOException("Unsupported extension manifest or host API version.");
        }
        JSONObject files = manifest.optJSONObject("files");
        JSONArray actions = manifest.optJSONArray("actions");
        if (files == null || files.length() == 0 || files.length() > MAX_FILES
                || actions == null || actions.length() == 0 || actions.length() > MAX_ACTIONS) {
            throw new IOException("The extension manifest must declare files and actions.");
        }
        for (int i = 0; i < files.length(); i++) {
            String name = files.names().getString(i);
            JSONObject descriptor = files.optJSONObject(name);
            if (!safeFileName(name) || descriptor == null
                    || !descriptor.optString("sha256").matches("(?i)[0-9a-f]{64}")) {
                throw new IOException("Invalid extension file descriptor: " + name);
            }
        }
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null || !action.optString("id").matches("[a-z0-9][a-z0-9._-]{0,47}")
                    || clean(action.optString("label"), 50).isEmpty()
                    || !"chapter_bottom".equals(action.optString("placement"))
                    || !TEXT_REPLACEMENT.equals(action.optString("capability"))
                    || !safeFileName(action.optString("dataFile"))
                    || files.optJSONObject(action.optString("dataFile")) == null) {
                throw new IOException("Unsupported extension action at index " + i + ".");
            }
            String icon = action.optString("icon", "scan_text");
            if (!icon.matches("[a-z][a-z0-9_]{0,31}")) throw new IOException("Invalid extension action icon.");
        }
    }

    private void validateActionData(JSONObject manifest, String fileName, String content) throws Exception {
        JSONArray actions = manifest.getJSONArray("actions");
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            if (fileName.equals(action.optString("dataFile"))
                    && TEXT_REPLACEMENT.equals(action.optString("capability"))) validatePairs(content);
        }
    }

    private void validatePairs(String content) throws JSONException, IOException {
        JSONArray pairs = new JSONArray(content);
        if (pairs.length() > 10000) throw new IOException("Extension rule count is too large.");
        for (int i = 0; i < pairs.length(); i++) {
            JSONArray pair = pairs.optJSONArray(i);
            if (pair == null || pair.length() < 2 || pair.length() > 3 || !(pair.opt(0) instanceof String)
                    || !(pair.opt(1) instanceof String) || pair.optString(0).isEmpty()) {
                throw new IOException("Each text rule must contain source, replacement, and optional exceptions.");
            }
            if (pair.length() == 3) {
                JSONArray exceptions = pair.optJSONArray(2);
                if (exceptions == null) throw new IOException("Text rule exceptions must be an array.");
                for (int j = 0; j < exceptions.length(); j++) {
                    Object exception = exceptions.opt(j);
                    if (!(exception instanceof String) || ((String) exception).trim().isEmpty()) {
                        throw new IOException("Text rule exceptions must be non-empty strings.");
                    }
                }
            }
        }
        TextReplacementEngine.transform("", pairs.toString());
    }

    private byte[] download(String address, int maxBytes, String fileName) throws Exception {
        URL url = checkedHttpsUrl(address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "NovelReader-Android-ExtensionHost/" + API_VERSION);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        try {
            int response = connection.getResponseCode();
            URL finalUrl = connection.getURL();
            if (!"https".equalsIgnoreCase(finalUrl.getProtocol())
                    || !url.getHost().equalsIgnoreCase(finalUrl.getHost())) {
                throw new IOException("Extension download redirected away from its HTTPS host.");
            }
            if (response < 200 || response >= 300) throw new IOException("HTTP " + response);
            if (connection.getContentLength() > maxBytes) {
                throw new IOException("Extension file is too large: " + fileName + " (limit " + maxBytes + " bytes).");
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) {
                        throw new IOException("Extension file is too large: " + fileName + " (limit " + maxBytes + " bytes).");
                    }
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private URL checkedManifestUrl(String value) throws Exception {
        URL url = checkedHttpsUrl(value);
        if (!"github.com".equalsIgnoreCase(url.getHost())) return url;
        String[] parts = url.getPath().replaceAll("^/+|/+$", "").split("/");
        if (parts.length < 2) return url;
        String owner = parts[0];
        String repository = parts[1];
        String branch;
        String filePath;
        if (parts.length >= 5 && "blob".equals(parts[2])) {
            branch = parts[3];
            filePath = String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length));
        } else if (parts.length >= 4 && "tree".equals(parts[2])) {
            branch = parts[3];
            String directory = parts.length > 4
                    ? String.join("/", java.util.Arrays.copyOfRange(parts, 4, parts.length)) : "";
            filePath = directory.isEmpty() ? MANIFEST_FILE : directory + "/" + MANIFEST_FILE;
        } else if (parts.length == 2) {
            branch = "main";
            filePath = MANIFEST_FILE;
        } else {
            return url;
        }
        return new URL("https://raw.githubusercontent.com/" + owner + "/" + repository
                + "/" + branch + "/" + filePath);
    }

    private URL checkedHttpsUrl(String value) throws Exception {
        URL url = new URL(value.trim());
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()) {
            throw new IOException("Enter an HTTPS extension manifest address.");
        }
        return url;
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        char[] hex = "0123456789abcdef".toCharArray();
        char[] output = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int value = digest[i] & 0xff;
            output[i * 2] = hex[value >>> 4];
            output[i * 2 + 1] = hex[value & 0x0f];
        }
        return new String(output);
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    private static boolean safeFileName(String name) {
        return name != null && name.length() <= 100 && !name.contains("..")
                && name.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private static String enabledKey(String id) { return "enabled." + id; }
    private static String sourceKey(String id) { return "source." + id; }

    static final class Plugin {
        final String id;
        final String name;
        final String version;
        final String description;
        final String manifestText;
        final JSONObject manifest;
        final JSONObject files;
        final List<Action> actions = new ArrayList<>();

        Plugin(JSONObject source, String sourceText) throws JSONException {
            manifest = source;
            manifestText = sourceText;
            id = source.getString("id");
            name = source.optString("name", id);
            version = source.optString("version", "?");
            description = source.optString("description", "");
            files = source.getJSONObject("files");
            JSONArray actionArray = source.getJSONArray("actions");
            for (int i = 0; i < actionArray.length(); i++) actions.add(new Action(id, name, actionArray.getJSONObject(i)));
        }
    }

    static final class Action {
        final String pluginId;
        final String pluginName;
        final String id;
        final String label;
        final String icon;
        final String capability;
        final String dataFile;
        final String confirmTitle;
        final String confirmMessage;

        Action(String pluginId, String pluginName, JSONObject action) {
            this.pluginId = pluginId;
            this.pluginName = pluginName;
            id = action.optString("id");
            label = action.optString("label");
            icon = action.optString("icon", "scan_text");
            capability = action.optString("capability");
            dataFile = action.optString("dataFile");
            confirmTitle = action.optString("confirmTitle", pluginName);
            confirmMessage = action.optString("confirmMessage", "این افزونه متن فعلی Chapter را تغییر می‌دهد.");
        }
    }
}
