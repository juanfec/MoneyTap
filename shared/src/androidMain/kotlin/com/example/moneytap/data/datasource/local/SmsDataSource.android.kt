package com.example.moneytap.data.datasource.local

import android.content.ContentResolver
import android.content.Context
import android.provider.Telephony
import com.example.moneytap.data.dto.SmsMessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SmsDataSource(
    private val context: Context,
) {
    private val contentResolver: ContentResolver
        get() = context.contentResolver

    actual suspend fun readInbox(limit: Int): Result<List<SmsMessageDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val messages = mutableListOf<SmsMessageDto>()
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ,
                )

                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        messages.add(
                            SmsMessageDto(
                                id = cursor.getLong(idIndex),
                                address = cursor.getString(addressIndex),
                                body = cursor.getString(bodyIndex),
                                date = cursor.getLong(dateIndex),
                                read = cursor.getInt(readIndex),
                            ),
                        )
                        count++
                    }
                }
                messages
            }
        }

    actual suspend fun readMessageById(id: Long): Result<SmsMessageDto?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ,
                )

                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(id.toString()),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        SmsMessageDto(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                            address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                            body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                            date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                            read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)),
                        )
                    } else {
                        null
                    }
                }
            }
        }

    actual fun isSupported(): Boolean = true
}
