package com.statussaver.app.ui

import android.os.Parcel
import android.os.Parcelable
import com.statussaver.app.data.database.FileType
import com.statussaver.app.data.database.StatusSource

/**
 * Parcelable data class representing a status item for fullscreen viewing.
 * Used to pass list of items between fragments and FullScreenViewActivity.
 */
data class MediaItem(
    val id: Long,
    val filename: String,
    val path: String,
    val uri: String,
    val fileType: FileType,
    val source: StatusSource,
    var isDownloaded: Boolean = false,
    val cachedAt: Long = 0L,
    val expiresAt: Long = 0L
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        filename = parcel.readString() ?: "",
        path = parcel.readString() ?: "",
        uri = parcel.readString() ?: "",
        fileType = FileType.valueOf(parcel.readString() ?: FileType.IMAGE.name),
        source = StatusSource.valueOf(parcel.readString() ?: StatusSource.LIVE.name),
        isDownloaded = parcel.readByte() != 0.toByte(),
        cachedAt = parcel.readLong(),
        expiresAt = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(filename)
        parcel.writeString(path)
        parcel.writeString(uri)
        parcel.writeString(fileType.name)
        parcel.writeString(source.name)
        parcel.writeByte(if (isDownloaded) 1 else 0)
        parcel.writeLong(cachedAt)
        parcel.writeLong(expiresAt)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<MediaItem> {
        override fun createFromParcel(parcel: Parcel): MediaItem = MediaItem(parcel)
        override fun newArray(size: Int): Array<MediaItem?> = arrayOfNulls(size)
        
        /**
         * Convert from StatusAdapter.StatusItem to MediaItem
         */
        fun fromStatusItem(item: StatusAdapter.StatusItem): MediaItem {
            return MediaItem(
                id = item.id,
                filename = item.filename,
                path = item.path,
                uri = item.uri,
                fileType = item.fileType,
                source = item.source,
                isDownloaded = item.isDownloaded,
                cachedAt = item.cachedAt,
                expiresAt = item.expiresAt
            )
        }
    }
}
