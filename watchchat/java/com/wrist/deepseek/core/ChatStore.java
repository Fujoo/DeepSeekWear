package com.wrist.deepseek.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ChatStore extends SQLiteOpenHelper {
    private static final String DB = "chats.db";
    private static final int VERSION = 3;

    public ChatStore(Context context) {
        super(context, DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE conversations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "is_private INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "conversation_id INTEGER NOT NULL," +
                "role TEXT NOT NULL," +
                "content TEXT NOT NULL DEFAULT ''," +
                "reasoning TEXT NOT NULL DEFAULT ''," +
                "sources TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "status INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_messages_cid ON messages(conversation_id, id)");
        db.execSQL("CREATE INDEX idx_conversations_updated ON conversations(updated_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sources TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0");
        }
    }

    public synchronized long createConversation(String title) {
        return createConversation(title, false);
    }

    public synchronized long createConversation(String title, boolean privateSpace) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", title == null || title.trim().isEmpty() ? "新对话" : title.trim());
        v.put("is_private", privateSpace ? 1 : 0);
        v.put("created_at", now);
        v.put("updated_at", now);
        return getWritableDatabase().insert("conversations", null, v);
    }

    public synchronized boolean conversationExists(long id) {
        if (id <= 0) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM conversations WHERE id=? LIMIT 1", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public synchronized boolean isPrivateConversation(long id) {
        if (id <= 0) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT is_private FROM conversations WHERE id=? LIMIT 1",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() && c.getInt(0) == 1;
        } finally {
            c.close();
        }
    }

    public synchronized long addMessage(long conversationId, String role, String content,
                                        String reasoning, int status) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("conversation_id", conversationId);
        v.put("role", role);
        v.put("content", content == null ? "" : content);
        v.put("reasoning", reasoning == null ? "" : reasoning);
        v.put("created_at", now);
        v.put("status", status);
        long id = getWritableDatabase().insert("messages", null, v);
        touch(conversationId);
        return id;
    }

    public synchronized void updateMessage(long id, String content, String reasoning, int status) {
        ContentValues v = new ContentValues();
        v.put("content", content == null ? "" : content);
        v.put("reasoning", reasoning == null ? "" : reasoning);
        v.put("status", status);
        getWritableDatabase().update("messages", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateSources(long id, String sources) {
        ContentValues v = new ContentValues();
        v.put("sources", sources == null ? "" : sources);
        getWritableDatabase().update("messages", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void touch(long conversationId) {
        ContentValues v = new ContentValues();
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("conversations", v, "id=?", new String[]{String.valueOf(conversationId)});
    }

    public synchronized void renameConversation(long id, String title) {
        ContentValues v = new ContentValues();
        v.put("title", title == null || title.trim().isEmpty() ? "新对话" : title.trim());
        getWritableDatabase().update("conversations", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void deleteConversation(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("messages", "conversation_id=?", new String[]{String.valueOf(id)});
            db.delete("conversations", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void deleteAllConversations() {
        deleteAllConversations(false);
    }

    public synchronized void deleteAllConversations(boolean privateSpace) {
        SQLiteDatabase db = getWritableDatabase();
        String flag = privateSpace ? "1" : "0";
        db.beginTransaction();
        try {
            db.delete("messages", "conversation_id IN (SELECT id FROM conversations WHERE is_private=?)",
                    new String[]{flag});
            db.delete("conversations", "is_private=?", new String[]{flag});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<Conversation> listConversations() {
        return listConversations(false);
    }

    public synchronized List<Conversation> listConversations(boolean privateSpace) {
        List<Conversation> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT c.id,c.title,c.created_at,c.updated_at," +
                        "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id=c.id)," +
                        "(SELECT content FROM messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) " +
                        "FROM conversations c WHERE c.is_private=? ORDER BY c.updated_at DESC",
                new String[]{privateSpace ? "1" : "0"});
        try {
            while (c.moveToNext()) {
                Conversation cv = new Conversation();
                cv.id = c.getLong(0);
                cv.title = c.getString(1);
                cv.createdAt = c.getLong(2);
                cv.updatedAt = c.getLong(3);
                cv.messageCount = c.getInt(4);
                cv.preview = c.isNull(5) ? "" : c.getString(5);
                cv.isPrivate = privateSpace;
                out.add(cv);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized Conversation getConversation(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,is_private,created_at,updated_at FROM conversations WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) {
                Conversation cv = new Conversation();
                cv.id = c.getLong(0);
                cv.title = c.getString(1);
                cv.isPrivate = c.getInt(2) == 1;
                cv.createdAt = c.getLong(3);
                cv.updatedAt = c.getLong(4);
                return cv;
            }
        } finally {
            c.close();
        }
        return null;
    }

    public synchronized List<Message> messages(long conversationId) {
        List<Message> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,conversation_id,role,content,reasoning,sources,created_at,status " +
                        "FROM messages WHERE conversation_id=? ORDER BY id ASC",
                new String[]{String.valueOf(conversationId)});
        try {
            while (c.moveToNext()) {
                Message m = new Message();
                m.id = c.getLong(0);
                m.conversationId = c.getLong(1);
                m.role = c.getString(2);
                m.content = c.getString(3);
                m.reasoning = c.getString(4);
                m.sources = c.getString(5);
                m.createdAt = c.getLong(6);
                m.status = c.getInt(7);
                out.add(m);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized Message getMessage(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,conversation_id,role,content,reasoning,sources,created_at,status FROM messages WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            if (c.moveToFirst()) {
                Message m = new Message();
                m.id = c.getLong(0);
                m.conversationId = c.getLong(1);
                m.role = c.getString(2);
                m.content = c.getString(3);
                m.reasoning = c.getString(4);
                m.sources = c.getString(5);
                m.createdAt = c.getLong(6);
                m.status = c.getInt(7);
                return m;
            }
        } finally {
            c.close();
        }
        return null;
    }

    public synchronized String lastUserText(long conversationId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT content FROM messages WHERE conversation_id=? AND role='user' ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(conversationId)});
        try {
            return c.moveToFirst() ? c.getString(0) : "";
        } finally {
            c.close();
        }
    }

    public synchronized long latestConversationId() {
        return latestConversationId(false);
    }

    public synchronized long latestConversationId(boolean privateSpace) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM conversations WHERE is_private=? ORDER BY updated_at DESC LIMIT 1",
                new String[]{privateSpace ? "1" : "0"});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Messages left in "streaming" state after a crash should not spin forever. */
    public synchronized void repairInterrupted() {
        ContentValues v = new ContentValues();
        v.put("status", 2);
        getWritableDatabase().update("messages", v, "status=1", null);
    }

    public synchronized void deleteMessageAndAfter(long conversationId, long messageId) {
        getWritableDatabase().delete("messages", "conversation_id=? AND id>=?",
                new String[]{String.valueOf(conversationId), String.valueOf(messageId)});
        touch(conversationId);
    }

    public synchronized void deleteMessage(long messageId) {
        getWritableDatabase().delete("messages", "id=?", new String[]{String.valueOf(messageId)});
    }

    public synchronized int countMessages(long conversationId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM messages WHERE conversation_id=?",
                new String[]{String.valueOf(conversationId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }
}
