// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'cache_metadata.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class CacheMetadataAdapter extends TypeAdapter<CacheMetadata> {
  @override
  final int typeId = 1;

  @override
  CacheMetadata read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return CacheMetadata(
      originalPath: fields[0] as String,
      cachedPath: fields[1] as String,
      cachedAt: fields[2] as DateTime,
      expiresAt: fields[3] as DateTime,
      isVideo: fields[4] as bool,
      fileSize: fields[5] as int,
      fileName: fields[6] as String,
      thumbnailPath: fields[7] as String?,
    );
  }

  @override
  void write(BinaryWriter writer, CacheMetadata obj) {
    writer
      ..writeByte(8)
      ..writeByte(0)
      ..write(obj.originalPath)
      ..writeByte(1)
      ..write(obj.cachedPath)
      ..writeByte(2)
      ..write(obj.cachedAt)
      ..writeByte(3)
      ..write(obj.expiresAt)
      ..writeByte(4)
      ..write(obj.isVideo)
      ..writeByte(5)
      ..write(obj.fileSize)
      ..writeByte(6)
      ..write(obj.fileName)
      ..writeByte(7)
      ..write(obj.thumbnailPath);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is CacheMetadataAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}
