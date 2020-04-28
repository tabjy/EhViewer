/*
 * Copyright 2019, 2020 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

import android.content.Context;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.util.TextUrl;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

public class EhTagDatabase {
    public static final String NAMESPACE_ROW = "row";
    public static final String NAMESPACE_ARTIST = "artist";
    public static final String NAMESPACE_CHARACTER = "character";
    public static final String NAMESPACE_FEMALE = "female";
    public static final String NAMESPACE_GROUP = "group";
    public static final String NAMESPACE_LANGUAGE = "language";
    public static final String NAMESPACE_MALE = "male";
    public static final String NAMESPACE_MISC = "misc";
    public static final String NAMESPACE_PARODY = "parody";
    public static final String NAMESPACE_RECLASS = "reclass";

    private static final String APP_CONFIG_DIR = "tag-translations";
    private static final String TMP_FILE_POSTFIX = ".tmp";

    private static Lock lock = new ReentrantLock();
    private static EhTagDatabase instance;

    private Map<String, Map<String, String>> translationDb = new HashMap<>();
    private String version = "Unknown";

    public EhTagDatabase(String name, JSONObject top) throws JSONException {
        JSONArray data = top.getJSONArray("data");

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            String namespace = item.getString("namespace");

            Map<String, String> translations = translationDb.get(namespace);
            if (translations == null) {
                translations = new HashMap<>();
                translationDb.put(namespace, translations);
            }

            JSONObject tags = item.getJSONObject("data");
            for (Iterator<String> it = tags.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = tags.getJSONObject(key).getString("name")
                        .replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""); // remove emojis
                translations.put(key, value);
            }
        }

        version = top.getJSONObject("head").getJSONObject("committer").getString("when");
    }

    public String getTranslation(String namespace, String tag) {
        Map<String, String> translations = translationDb.get(namespace);
        if (translations == null) {
            return tag;
        }

        String translation = translations.get(tag);
        if (translation == null) {
            return tag;
        }

        return translation;
    }

    public String getVersion() {
        return version;
    }

    @Nullable
    public static EhTagDatabase getInstance(Context context) {
        if (isPossible(context)) {
            return instance;
        }

        instance = null;
        return null;
    }

    private static String[] getMetadata(Context context) {
        String[] metadata = context.getResources().getStringArray(R.array.tag_translation_metadata);
        if (metadata.length == 2) {
            return metadata;
        } else {
            return null;
        }
    }

    public static boolean isPossible(Context context) {
        return getMetadata(context) != null;
    }

    private static boolean save(OkHttpClient client, String url, File file) {
        Request request = new Request.Builder().url(url).build();
        Call call = client.newCall(request);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return false;
            }

            try (InputStream is = body.byteStream(); OutputStream os = new FileOutputStream(file)) {
                IOUtils.copy(is, os);
            }

            return true;
        } catch (Throwable t) {
            ExceptionUtils.throwIfFatal(t);
            return false;
        }
    }

    public static void update(Context context) {
        String[] urls = getMetadata(context);
        if (urls == null) {
            instance = null;
            return;
        }

        String dataName = urls[0];
        String dataUrl = urls[1];

        if (!lock.tryLock()) {
            return;
        }

        try {
            File dir = AppConfig.getFilesDir(APP_CONFIG_DIR);
            if (dir == null) {
                return;
            }
            File dataFile = new File(dir, dataName);

            // Read current EhTagDatabase
            if (instance == null && dataFile.exists()) {
                try (BufferedSource source = Okio.buffer(Okio.source(dataFile))) {
                    String json = source.readString(TextUrl.UTF_8);
                    JSONObject top = new JSONObject(json);
                    instance = new EhTagDatabase(dataName, top);
                } catch (FileNotFoundException | JSONException e) {
                    FileUtils.delete(dataFile);
                    instance = null;
                }
            }

            OkHttpClient client = EhApplication.getOkHttpClient(EhApplication.getInstance());

            // Save new data
            File tempDataFile = new File(dir, dataName + TMP_FILE_POSTFIX);
            FileUtils.delete(tempDataFile);
            if (!save(client, dataUrl, tempDataFile)) {
                FileUtils.delete(tempDataFile);
                return;
            }

            try (BufferedSource source = Okio.buffer(Okio.source(tempDataFile))) {
                String json = source.readString(TextUrl.UTF_8);
                JSONObject top = new JSONObject(json);
                EhTagDatabase newInstance = new EhTagDatabase(dataName, top);

                if (instance == null || newInstance.getVersion().compareTo(instance.getVersion()) > 0) {
                    instance = newInstance;
                    tempDataFile.renameTo(dataFile);
                }

                FileUtils.delete(tempDataFile);
            } catch (FileNotFoundException | JSONException e) {
                FileUtils.delete(dataFile);
                instance = null;
            }
        } catch (IOException e) {
            ExceptionUtils.throwIfFatal(e);
        } finally {
            lock.unlock();
        }
    }
}
