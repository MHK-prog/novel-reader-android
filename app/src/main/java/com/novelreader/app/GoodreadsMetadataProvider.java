package com.novelreader.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User-triggered Goodreads lookup. It reads only public search and book pages. */
final class GoodreadsMetadataProvider {
    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_HTML_BYTES = 5 * 1024 * 1024;
    private static final int MAX_COVER_BYTES = 5 * 1024 * 1024;
    private static final Pattern BOOK_LINK = Pattern.compile(
            "(?is)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]*/book/show/[0-9]+[^'\"]*)['\"][^>]*>(.*?)</a>");
    private static final Pattern LEGACY_BOOK_ROW = Pattern.compile(
            "(?is)<tr\\b[^>]*itemtype\\s*=\\s*['\"]http://schema\\.org/Book['\"][^>]*>(.*?)</tr>");
    private static final Pattern JSON_LD = Pattern.compile(
            "(?is)<script\\b[^>]*type\\s*=\\s*['\"]application/ld\\+json['\"][^>]*>(.*?)</script>");
    private static final Pattern GENRE_LINK = Pattern.compile(
            "(?is)<a\\b[^>]*href\\s*=\\s*['\"][^'\"]*/genres/[^'\"]*['\"][^>]*>(.*?)</a>");
    private static final Pattern FALLBACK_RATING = Pattern.compile(
            "(?is)(?:ratingValue|avgRating|averageRating)[\"']?\\s*[:=]\\s*[\"']?([0-5](?:\\.\\d+)?)");
    private static final Pattern FALLBACK_COUNT = Pattern.compile(
            "(?is)(?:ratingCount|ratingsCount)[\"']?\\s*[:=]\\s*[\"']?([0-9,]+)");

    static final class BookMetadata {
        String title = "";
        String author = "";
        String pageUrl = "";
        String coverUrl = "";
        double rating = -1;
        long ratingCount;
        Bitmap cover;
        final List<String> genres = new ArrayList<>();
    }

    private GoodreadsMetadataProvider() { }

    static BookMetadata lookup(String title, String author) throws IOException {
        String query = (title == null ? "" : title.trim()) + " " + (author == null ? "" : author.trim());
        if (query.trim().isEmpty()) throw new IOException("عنوان کتاب یا نویسنده وارد نشده است.");

        String searchUrl = "https://www.goodreads.com/search?q="
                + URLEncoder.encode(query.trim(), "UTF-8");
        String searchHtml = requestText(new URL(searchUrl), MAX_HTML_BYTES);
        String bookUrl = findBookUrl(searchHtml);
        if (bookUrl == null) throw new IOException("در Goodreads کتابی با این مشخصات پیدا نشد.");

        String detailHtml = requestText(new URL(bookUrl), MAX_HTML_BYTES);
        BookMetadata metadata = parseBookPage(detailHtml, bookUrl);
        if (!metadata.coverUrl.isEmpty()) {
            try { metadata.cover = downloadCover(metadata.coverUrl); }
            catch (IOException error) {
                android.util.Log.w("NovelReader", "Goodreads cover could not be downloaded", error);
            }
        }
        if (metadata.title.isEmpty()) metadata.title = title == null ? "" : title.trim();
        if (metadata.author.isEmpty()) metadata.author = author == null ? "" : author.trim();
        return metadata;
    }

    private static String findBookUrl(String html) {
        Matcher rows = LEGACY_BOOK_ROW.matcher(html);
        String candidateHtml = rows.find() ? rows.group(1) : html;
        Matcher matcher = BOOK_LINK.matcher(candidateHtml);
        if (!matcher.find()) {
            if (candidateHtml != html) {
                matcher = BOOK_LINK.matcher(html);
                if (!matcher.find()) return null;
            } else return null;
        }
        try {
            String link = matcher.group(1).replace("&amp;", "&");
            URL resolved = new URL(new URL("https://www.goodreads.com/"), link);
            if (!"https".equalsIgnoreCase(resolved.getProtocol())
                    || !isGoodreadsHost(resolved.getHost())) return null;
            return new URL(resolved.getProtocol(), resolved.getHost(), resolved.getPort(), resolved.getPath()).toString();
        } catch (Exception ignored) { return null; }
    }

    private static BookMetadata parseBookPage(String html, String pageUrl) {
        BookMetadata result = new BookMetadata();
        result.pageUrl = pageUrl;
        JSONObject bookData = findBookJsonLd(html);
        if (bookData != null) {
            result.title = jsonText(bookData.opt("name"));
            result.author = authorName(bookData.opt("author"));
            result.coverUrl = firstJsonText(bookData.opt("image"));
            addJsonGenres(result.genres, bookData.opt("genre"));
            JSONObject rating = bookData.optJSONObject("aggregateRating");
            if (rating != null) {
                result.rating = parseDouble(rating.opt("ratingValue"), -1);
                result.ratingCount = parseLong(rating.opt("ratingCount"), 0);
            }
        }

        if (result.title.isEmpty()) result.title = metaContent(html, "property", "og:title");
        if (result.title.isEmpty()) result.title = firstElementText(html, "h1");
        if (result.author.isEmpty()) result.author = metaContent(html, "name", "author");
        if (result.coverUrl.isEmpty()) result.coverUrl = metaContent(html, "property", "og:image");

        if (result.rating < 0) {
            Matcher ratingMatcher = FALLBACK_RATING.matcher(html);
            if (ratingMatcher.find()) result.rating = parseDouble(ratingMatcher.group(1), -1);
        }
        if (result.ratingCount <= 0) {
            Matcher countMatcher = FALLBACK_COUNT.matcher(html);
            if (countMatcher.find()) result.ratingCount = parseLong(countMatcher.group(1), 0);
        }

        Set<String> uniqueGenres = new LinkedHashSet<>();
        for (String genre : result.genres) uniqueGenres.add(genre.toLowerCase(Locale.ROOT));
        Matcher genreMatcher = GENRE_LINK.matcher(html);
        while (genreMatcher.find() && uniqueGenres.size() < 12) {
            String genre = cleanText(genreMatcher.group(1));
            if (!genre.isEmpty() && uniqueGenres.add(genre.toLowerCase(Locale.ROOT))) result.genres.add(genre);
        }
        result.coverUrl = absoluteCoverUrl(result.coverUrl);
        return result;
    }

    private static void addJsonGenres(List<String> target, Object genres) {
        if (genres instanceof JSONArray) {
            JSONArray array = (JSONArray) genres;
            for (int i = 0; i < array.length() && target.size() < 12; i++) {
                String value = jsonText(array.opt(i));
                addUniqueGenre(target, value);
            }
        } else {
            addUniqueGenre(target, jsonText(genres));
        }
    }

    private static void addUniqueGenre(List<String> target, String value) {
        if (value == null || value.isEmpty() || target.size() >= 12) return;
        for (String current : target) if (current.equalsIgnoreCase(value)) return;
        target.add(value);
    }

    private static JSONObject findBookJsonLd(String html) {
        Matcher matcher = JSON_LD.matcher(html);
        while (matcher.find()) {
            try {
                String json = matcher.group(1).trim();
                Object parsed = json.startsWith("[") ? new JSONArray(json) : new JSONObject(json);
                JSONObject book = findBookObject(parsed);
                if (book != null) return book;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static JSONObject findBookObject(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String type = jsonText(object.opt("@type"));
            if (type.toLowerCase(Locale.ROOT).contains("book")) return object;
            Object graph = object.opt("@graph");
            if (graph != null) return findBookObject(graph);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findBookObject(array.opt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String authorName(Object value) {
        if (value instanceof JSONArray) {
            JSONArray authors = (JSONArray) value;
            for (int i = 0; i < authors.length(); i++) {
                String name = authorName(authors.opt(i));
                if (!name.isEmpty()) return name;
            }
            return "";
        }
        if (value instanceof JSONObject) return jsonText(((JSONObject) value).opt("name"));
        return jsonText(value);
    }

    private static String firstJsonText(Object value) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            return array.length() == 0 ? "" : jsonText(array.opt(0));
        }
        return jsonText(value);
    }

    private static String jsonText(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONObject) return "";
        return cleanText(String.valueOf(value));
    }

    private static String metaContent(String html, String key, String value) {
        Matcher tags = Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            if (value.equalsIgnoreCase(attribute(tag, key))) return cleanText(attribute(tag, "content"));
        }
        return "";
    }

    private static String attribute(String tag, String name) {
        Matcher matcher = Pattern.compile("(?is)\\b" + Pattern.quote(name)
                + "\\s*=\\s*(['\"])(.*?)\\1").matcher(tag);
        return matcher.find() ? matcher.group(2) : "";
    }

    private static String firstElementText(String html, String tagName) {
        Matcher matcher = Pattern.compile("(?is)<" + Pattern.quote(tagName)
                + "\\b[^>]*>(.*?)</" + Pattern.quote(tagName) + ">").matcher(html);
        return matcher.find() ? cleanText(matcher.group(1)) : "";
    }

    private static String cleanText(String value) {
        if (value == null) return "";
        String plain = value.replaceAll("(?is)<[^>]*>", " ");
        plain = Html.fromHtml(plain).toString();
        return plain.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String absoluteCoverUrl(String coverUrl) {
        if (coverUrl == null || coverUrl.trim().isEmpty()) return "";
        try {
            URL url = new URL(new URL("https://www.goodreads.com/"), coverUrl.trim());
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !(host.endsWith(".gr-assets.com") || host.endsWith(".goodreads.com")
                    || host.equals("gr-assets.com") || host.equals("goodreads.com"))) return "";
            return url.toString();
        } catch (Exception ignored) { return ""; }
    }

    private static Bitmap downloadCover(String coverUrl) throws IOException {
        HttpURLConnection connection = openFollowingRedirects(new URL(coverUrl));
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Cover request failed: " + status);
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IOException("Goodreads did not return an image.");
            }
            byte[] bytes = readLimited(connection.getInputStream(), MAX_COVER_BYTES);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("The cover image is invalid.");
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) throw new IOException("The cover image could not be decoded.");
            return bitmap;
        } finally {
            connection.disconnect();
        }
    }

    private static String requestText(URL url, int maxBytes) throws IOException {
        HttpURLConnection connection = openFollowingRedirects(url);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Goodreads request failed: " + status);
            return new String(readLimited(connection.getInputStream(), maxBytes), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(URL url) throws IOException {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !isGoodreadsHost(host)
                && !host.equals("gr-assets.com") && !host.endsWith(".gr-assets.com")) {
            throw new IOException("Unexpected Goodreads address.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,image/avif,image/webp,image/apng,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        return connection;
    }

    private static HttpURLConnection openFollowingRedirects(URL initialUrl) throws IOException {
        URL current = initialUrl;
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpURLConnection connection = openConnection(current);
            int status = connection.getResponseCode();
            if (status < 300 || status >= 400) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.trim().isEmpty()) {
                throw new IOException("Goodreads returned an invalid redirect.");
            }
            current = new URL(current, location);
        }
        throw new IOException("Goodreads redirected too many times.");
    }

    private static boolean isGoodreadsHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return normalized.equals("goodreads.com") || normalized.endsWith(".goodreads.com");
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (out.size() + count > maxBytes) throw new IOException("Goodreads response is too large.");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static double parseDouble(Object value, double fallback) {
        try { return Double.parseDouble(String.valueOf(value).replace(",", "").trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private static long parseLong(Object value, long fallback) {
        try { return Long.parseLong(String.valueOf(value).replace(",", "").trim()); }
        catch (Exception ignored) { return fallback; }
    }
}
