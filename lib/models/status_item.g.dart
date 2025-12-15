// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'status_item.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class StatusItemAdapter extends TypeAdapter<StatusItem> {
  @override
  final int typeId = 0;

  @override
  StatusItem read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return StatusItem(
      path: fields[0] as String,
      name: fields[1] as String,
      isVideo: fields[2] as bool,
      timestamp: fields[3] as DateTime,
      size: fields[4] as int,
      thumbnailPath: fields[5] as String?,
      originalPath: fields[6] as String?,
      isCached: fields[7] as bool,
      isSaved: fields[8] as bool,
    );
  }

  @override
  void write(BinaryWriter writer, StatusItem obj) {
    writer
      ..writeByte(9)
      ..writeByte(0)
      ..write(obj.path)
      ..writeByte(1)
      ..write(obj.name)
      ..writeByte(2)
      ..write(obj.isVideo)
      ..writeByte(3)
      ..write(obj.timestamp)
      ..writeByte(4)
      ..write(obj.size)
      ..writeByte(5)
      ..write(obj.thumbnailPath)
      ..writeByte(6)
      ..write(obj.originalPath)
      ..writeByte(7)
      ..write(obj.isCached)
      ..writeByte(8)
      ..write(obj.isSaved);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is StatusItemAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}
