package com.wrist.deepseek;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.wrist.deepseek.core.ChatStore;
import com.wrist.deepseek.core.Settings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {
    private static App instance;

    public Settings settings;
    public ChatStore store;

    public static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static final ExecutorService IO = Executors.newFixedThreadPool(4);
    public static final ExecutorService CHAT = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new Settings(this);
        store = new ChatStore(this);
        store.repairInterrupted();
    }

    public static App get(Context context) {
        return (App) context.getApplicationContext();
    }

    public static App get() {
        return instance;
    }
}
