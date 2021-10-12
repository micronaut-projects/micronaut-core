/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

/**
 * Delegates all functionality to the provided buffer.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
@Internal
public class ByteBufDelegate extends ByteBuf {

    private final ByteBuf byteBuf;

    /**
     * @param byteBuf The buffer to delegate to
     */
    public ByteBufDelegate(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    @Override
    public final boolean hasMemoryAddress() {
        return byteBuf.hasMemoryAddress();
    }

    @Override
    public final long memoryAddress() {
        return byteBuf.memoryAddress();
    }

    @Override
    public final int capacity() {
        return byteBuf.capacity();
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        byteBuf.capacity(newCapacity);
        return this;
    }

    @Override
    public final int maxCapacity() {
        return byteBuf.maxCapacity();
    }

    @Override
    public final ByteBufAllocator alloc() {
        return byteBuf.alloc();
    }

    @Override
    @Deprecated
    public final ByteOrder order() {
        return byteBuf.order();
    }

    @Override
    @Deprecated
    public ByteBuf order(ByteOrder endianness) {
        return byteBuf.order(endianness);
    }

    @Override
    public final ByteBuf unwrap() {
        return byteBuf;
    }

    @Override
    public ByteBuf asReadOnly() {
        return byteBuf.asReadOnly();
    }

    @Override
    public boolean isReadOnly() {
        return byteBuf.isReadOnly();
    }

    @Override
    public final boolean isDirect() {
        return byteBuf.isDirect();
    }

    @Override
    public final int readerIndex() {
        return byteBuf.readerIndex();
    }

    @Override
    public final ByteBuf readerIndex(int readerIndex) {
        byteBuf.readerIndex(readerIndex);
        return this;
    }

    @Override
    public final int writerIndex() {
        return byteBuf.writerIndex();
    }

    @Override
    public final ByteBuf writerIndex(int writerIndex) {
        byteBuf.writerIndex(writerIndex);
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        byteBuf.setIndex(readerIndex, writerIndex);
        return this;
    }

    @Override
    public final int readableBytes() {
        return byteBuf.readableBytes();
    }

    @Override
    public final int writableBytes() {
        return byteBuf.writableBytes();
    }

    @Override
    public final int maxWritableBytes() {
        return byteBuf.maxWritableBytes();
    }

    @Override
    public final boolean isReadable() {
        return byteBuf.isReadable();
    }

    @Override
    public final boolean isWritable() {
        return byteBuf.isWritable();
    }

    @Override
    public final ByteBuf clear() {
        byteBuf.clear();
        return this;
    }

    @Override
    public final ByteBuf markReaderIndex() {
        byteBuf.markReaderIndex();
        return this;
    }

    @Override
    public final ByteBuf resetReaderIndex() {
        byteBuf.resetReaderIndex();
        return this;
    }

    @Override
    public final ByteBuf markWriterIndex() {
        byteBuf.markWriterIndex();
        return this;
    }

    @Override
    public final ByteBuf resetWriterIndex() {
        byteBuf.resetWriterIndex();
        return this;
    }

    @Override
    public ByteBuf discardReadBytes() {
        byteBuf.discardReadBytes();
        return this;
    }

    @Override
    public ByteBuf discardSomeReadBytes() {
        byteBuf.discardSomeReadBytes();
        return this;
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        byteBuf.ensureWritable(minWritableBytes);
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return byteBuf.ensureWritable(minWritableBytes, force);
    }

    @Override
    public boolean getBoolean(int index) {
        return byteBuf.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return byteBuf.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return byteBuf.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return byteBuf.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return byteBuf.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return byteBuf.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return byteBuf.getUnsignedShortLE(index);
    }

    @Override
    public int getMedium(int index) {
        return byteBuf.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        return byteBuf.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return byteBuf.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return byteBuf.getUnsignedMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return byteBuf.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return byteBuf.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return byteBuf.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return byteBuf.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return byteBuf.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return byteBuf.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return byteBuf.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return byteBuf.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        return byteBuf.getDouble(index);
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst) {
        byteBuf.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int length) {
        byteBuf.getBytes(index, dst, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        byteBuf.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        byteBuf.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        byteBuf.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        byteBuf.getBytes(index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        byteBuf.getBytes(index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return byteBuf.getBytes(index, out, length);
    }

    @Override
    public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return byteBuf.getBytes(index, out, position, length);
    }

    @Override
    public CharSequence getCharSequence(int index, int length, Charset charset) {
        return byteBuf.getCharSequence(index, length, charset);
    }

    @Override
    public ByteBuf setBoolean(int index, boolean value) {
        byteBuf.setBoolean(index, value);
        return this;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        byteBuf.setByte(index, value);
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        byteBuf.setShort(index, value);
        return this;
    }

    @Override
    public ByteBuf setShortLE(int index, int value) {
        byteBuf.setShortLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        byteBuf.setMedium(index, value);
        return this;
    }

    @Override
    public ByteBuf setMediumLE(int index, int value) {
        byteBuf.setMediumLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        byteBuf.setInt(index, value);
        return this;
    }

    @Override
    public ByteBuf setIntLE(int index, int value) {
        byteBuf.setIntLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        byteBuf.setLong(index, value);
        return this;
    }

    @Override
    public ByteBuf setLongLE(int index, long value) {
        byteBuf.setLongLE(index, value);
        return this;
    }

    @Override
    public ByteBuf setChar(int index, int value) {
        byteBuf.setChar(index, value);
        return this;
    }

    @Override
    public ByteBuf setFloat(int index, float value) {
        byteBuf.setFloat(index, value);
        return this;
    }

    @Override
    public ByteBuf setDouble(int index, double value) {
        byteBuf.setDouble(index, value);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src) {
        byteBuf.setBytes(index, src);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int length) {
        byteBuf.setBytes(index, src, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        byteBuf.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        byteBuf.setBytes(index, src);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        byteBuf.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        byteBuf.setBytes(index, src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return byteBuf.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return byteBuf.setBytes(index, in, length);
    }

    @Override
    public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        return byteBuf.setBytes(index, in, position, length);
    }

    @Override
    public ByteBuf setZero(int index, int length) {
        byteBuf.setZero(index, length);
        return this;
    }

    @Override
    public int setCharSequence(int index, CharSequence sequence, Charset charset) {
        return byteBuf.setCharSequence(index, sequence, charset);
    }

    @Override
    public boolean readBoolean() {
        return byteBuf.readBoolean();
    }

    @Override
    public byte readByte() {
        return byteBuf.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return byteBuf.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return byteBuf.readShort();
    }

    @Override
    public short readShortLE() {
        return byteBuf.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return byteBuf.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return byteBuf.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return byteBuf.readMedium();
    }

    @Override
    public int readMediumLE() {
        return byteBuf.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return byteBuf.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return byteBuf.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return byteBuf.readInt();
    }

    @Override
    public int readIntLE() {
        return byteBuf.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return byteBuf.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return byteBuf.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return byteBuf.readLong();
    }

    @Override
    public long readLongLE() {
        return byteBuf.readLongLE();
    }

    @Override
    public char readChar() {
        return byteBuf.readChar();
    }

    @Override
    public float readFloat() {
        return byteBuf.readFloat();
    }

    @Override
    public double readDouble() {
        return byteBuf.readDouble();
    }

    @Override
    public ByteBuf readBytes(int length) {
        return byteBuf.readBytes(length);
    }

    @Override
    public ByteBuf readSlice(int length) {
        return byteBuf.readSlice(length);
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return byteBuf.readRetainedSlice(length);
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst) {
        byteBuf.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int length) {
        byteBuf.readBytes(dst, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuf dst, int dstIndex, int length) {
        byteBuf.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        byteBuf.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        byteBuf.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf readBytes(ByteBuffer dst) {
        byteBuf.readBytes(dst);
        return this;
    }

    @Override
    public ByteBuf readBytes(OutputStream out, int length) throws IOException {
        byteBuf.readBytes(out, length);
        return this;
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return byteBuf.readBytes(out, length);
    }

    @Override
    public int readBytes(FileChannel out, long position, int length) throws IOException {
        return byteBuf.readBytes(out, position, length);
    }

    @Override
    public CharSequence readCharSequence(int length, Charset charset) {
        return byteBuf.readCharSequence(length, charset);
    }

    @Override
    public ByteBuf skipBytes(int length) {
        byteBuf.skipBytes(length);
        return this;
    }

    @Override
    public ByteBuf writeBoolean(boolean value) {
        byteBuf.writeBoolean(value);
        return this;
    }

    @Override
    public ByteBuf writeByte(int value) {
        byteBuf.writeByte(value);
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        byteBuf.writeShort(value);
        return this;
    }

    @Override
    public ByteBuf writeShortLE(int value) {
        byteBuf.writeShortLE(value);
        return this;
    }

    @Override
    public ByteBuf writeMedium(int value) {
        byteBuf.writeMedium(value);
        return this;
    }

    @Override
    public ByteBuf writeMediumLE(int value) {
        byteBuf.writeMediumLE(value);
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        byteBuf.writeInt(value);
        return this;
    }

    @Override
    public ByteBuf writeIntLE(int value) {
        byteBuf.writeIntLE(value);
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        byteBuf.writeLong(value);
        return this;
    }

    @Override
    public ByteBuf writeLongLE(long value) {
        byteBuf.writeLongLE(value);
        return this;
    }

    @Override
    public ByteBuf writeChar(int value) {
        byteBuf.writeChar(value);
        return this;
    }

    @Override
    public ByteBuf writeFloat(float value) {
        byteBuf.writeFloat(value);
        return this;
    }

    @Override
    public ByteBuf writeDouble(double value) {
        byteBuf.writeDouble(value);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src) {
        byteBuf.writeBytes(src);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int length) {
        byteBuf.writeBytes(src, length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuf src, int srcIndex, int length) {
        byteBuf.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        byteBuf.writeBytes(src);
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        byteBuf.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf writeBytes(ByteBuffer src) {
        byteBuf.writeBytes(src);
        return this;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        return byteBuf.writeBytes(in, length);
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        return byteBuf.writeBytes(in, length);
    }

    @Override
    public int writeBytes(FileChannel in, long position, int length) throws IOException {
        return byteBuf.writeBytes(in, position, length);
    }

    @Override
    public ByteBuf writeZero(int length) {
        byteBuf.writeZero(length);
        return this;
    }

    @Override
    public int writeCharSequence(CharSequence sequence, Charset charset) {
        return byteBuf.writeCharSequence(sequence, charset);
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return byteBuf.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return byteBuf.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return byteBuf.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return byteBuf.bytesBefore(index, length, value);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return byteBuf.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return byteBuf.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return byteBuf.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return byteBuf.forEachByteDesc(index, length, processor);
    }

    @Override
    public ByteBuf copy() {
        return byteBuf.copy();
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return byteBuf.copy(index, length);
    }

    @Override
    public ByteBuf slice() {
        return byteBuf.slice();
    }

    @Override
    public ByteBuf retainedSlice() {
        return byteBuf.retainedSlice();
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return byteBuf.slice(index, length);
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return byteBuf.retainedSlice(index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return byteBuf.duplicate();
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return byteBuf.retainedDuplicate();
    }

    @Override
    public int nioBufferCount() {
        return byteBuf.nioBufferCount();
    }

    @Override
    public ByteBuffer nioBuffer() {
        return byteBuf.nioBuffer();
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return byteBuf.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] nioBuffers() {
        return byteBuf.nioBuffers();
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return byteBuf.nioBuffers(index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return byteBuf.internalNioBuffer(index, length);
    }

    @Override
    public boolean hasArray() {
        return byteBuf.hasArray();
    }

    @Override
    public byte[] array() {
        return byteBuf.array();
    }

    @Override
    public int arrayOffset() {
        return byteBuf.arrayOffset();
    }

    @Override
    public String toString(Charset charset) {
        return byteBuf.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return byteBuf.toString(index, length, charset);
    }

    @Override
    public int hashCode() {
        return byteBuf.hashCode();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object obj) {
        return byteBuf.equals(obj);
    }

    @Override
    public int compareTo(ByteBuf buffer) {
        return byteBuf.compareTo(buffer);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + byteBuf.toString() + ')';
    }

    @Override
    public ByteBuf retain(int increment) {
        byteBuf.retain(increment);
        return this;
    }

    @Override
    public ByteBuf retain() {
        byteBuf.retain();
        return this;
    }

    @Override
    public ByteBuf touch() {
        byteBuf.touch();
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        byteBuf.touch(hint);
        return this;
    }

    @Override
    public final boolean isReadable(int size) {
        return byteBuf.isReadable(size);
    }

    @Override
    public final boolean isWritable(int size) {
        return byteBuf.isWritable(size);
    }

    @Override
    public final int refCnt() {
        return byteBuf.refCnt();
    }

    @Override
    public boolean release() {
        return byteBuf.release();
    }

    @Override
    public boolean release(int decrement) {
        return byteBuf.release(decrement);
    }
}
