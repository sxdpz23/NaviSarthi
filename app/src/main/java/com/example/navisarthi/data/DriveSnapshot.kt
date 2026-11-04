package com.example.navisarthi.data

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Versioned local snapshot. Call on an I/O dispatcher. */
internal object DriveSnapshot {
    private val tables = listOf(
        "ride", "service_record", "scheduled_service", "document", "bike_identity",
        "fuel_fillup", "maintenance_item", "bike_state", "saved_location",
    )
    private val attachmentColumns = mapOf("service_record" to "invoice_path", "document" to "file_path")
    private const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024
    private const val MAX_TOTAL_ATTACHMENT_BYTES = 40L * 1024 * 1024

    fun capture(context: Context): JSONObject {
        val db = NaviSarthiDb.get(context).readableDatabase
        val records = JSONObject()
        val attachments = JSONObject()
        var attachmentBytes = 0L
        val fileIds = mutableMapOf<File, String>()
        db.beginTransaction()
        try {
            for (table in tables) {
                val rows = JSONArray()
                db.query(table, null, null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        for ((index, column) in cursor.columnNames.withIndex()) {
                            val value = when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                                else -> error("Unsupported backup column: $table.$column")
                            }
                            if (column == attachmentColumns[table] && value is String && value.isNotBlank()) {
                                val file = File(value).canonicalFile
                                require(file.toPath().startsWith(context.filesDir.canonicalFile.toPath()))
                                val id = fileIds.getOrPut(file) {
                                    val size = file.length()
                                    check(file.isFile && size <= MAX_ATTACHMENT_BYTES &&
                                        attachmentBytes + size <= MAX_TOTAL_ATTACHMENT_BYTES) {
                                        "An attachment is missing or exceeds the backup size limit"
                                    }
                                    val bytes = file.inputStream().use { input ->
                                        val data = input.readNBytes((MAX_ATTACHMENT_BYTES + 1).toInt())
                                        check(data.size <= MAX_ATTACHMENT_BYTES &&
                                            attachmentBytes + data.size <= MAX_TOTAL_ATTACHMENT_BYTES) {
                                            "Attachments exceed the backup size limit"
                                        }
                                        data
                                    }
                                    attachmentBytes += bytes.size
                                    val attachmentId = "file-${attachments.length()}"
                                    attachments.put(attachmentId, JSONObject()
                                        .put("name", file.name)
                                        .put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP)))
                                    attachmentId
                                }
                                row.put(column, id)
                            } else {
                                row.put(column, value)
                            }
                        }
                        rows.put(row)
                    }
                }
                records.put(table, rows)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return JSONObject()
            .put("format", "navisarthi-backup")
            .put("schemaVersion", 1)
            .put("createdAtMs", System.currentTimeMillis())
            .put("records", records)
            .put("attachments", attachments)
    }

    fun restore(context: Context, snapshot: JSONObject) {
        require(snapshot.getString("format") == "navisarthi-backup" && snapshot.getInt("schemaVersion") == 1)
        val records = snapshot.getJSONObject("records")
        val attachments = snapshot.getJSONObject("attachments")
        require(records.keys().asSequence().toSet() == tables.toSet())
        val directory = File(context.filesDir, "restored-backup")
        check(directory.isDirectory || directory.mkdirs())
        val staged = mutableMapOf<String, File>()
        var committed = false
        try {
            var total = 0L
            for (id in attachments.keys()) {
                val attachment = attachments.getJSONObject(id)
                val encoded = attachment.getString("base64")
                require(encoded.length <= ((MAX_ATTACHMENT_BYTES + 2) / 3) * 4)
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                total += bytes.size
                require(bytes.size <= MAX_ATTACHMENT_BYTES && total <= MAX_TOTAL_ATTACHMENT_BYTES)
                val extension = attachment.getString("name").substringAfterLast('.', "")
                    .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
                val file = File.createTempFile("attachment-", ".$extension", directory)
                staged[id] = file
                file.writeBytes(bytes)
            }

            val db = NaviSarthiDb.get(context).writableDatabase
            db.beginTransaction()
            try {
                for (table in tables) {
                    val columns = db.query(table, null, "0", null, null, null, null)
                        .use { it.columnNames.toSet() }
                    val rows = records.getJSONArray(table)
                    db.delete(table, null, null)
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        require(row.keys().asSequence().toSet() == columns)
                        val values = ContentValues()
                        for (column in columns) {
                            when (val value = row.get(column)) {
                                JSONObject.NULL -> values.putNull(column)
                                is Int -> values.put(column, value)
                                is Long -> values.put(column, value)
                                is Double -> values.put(column, value)
                                is String -> values.put(column, if (column == attachmentColumns[table] && value.isNotBlank())
                                    requireNotNull(staged[value]).absolutePath else value)
                                else -> error("Unsupported backup value")
                            }
                        }
                        db.insertOrThrow(table, null, values)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            committed = true
            SyncRepository.get(context).notifyLocalRestore()
        } finally {
            if (!committed) staged.values.forEach { it.delete() }
        }
    }

}
