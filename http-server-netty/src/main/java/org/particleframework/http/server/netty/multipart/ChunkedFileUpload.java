/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.netty.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Wrapper around {@link FileUpload} to allow a file upload to track its position and return the current chunk via {@link #getCurrentChunk()}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ChunkedFileUpload implements FileUpload {
    private final int length;
    private final FileUpload fileUpload;

    public ChunkedFileUpload(int length, FileUpload fileUpload) {
        this.length = length;
        this.fileUpload = fileUpload;
    }

    /**
     * @return The current chunk
     * @throws IOException If it cannot be read
     */
    public ByteBuf getCurrentChunk() throws IOException {
        return fileUpload.getChunk(length);
    }


    @Override
    public String getFilename() {
        return fileUpload.getFilename();
    }

    @Override
    public void setFilename(String filename) {
        fileUpload.setFilename(filename);
    }

    @Override
    public void setContentType(String contentType) {
        fileUpload.setContentType(contentType);
    }

    @Override
    public String getContentType() {
        return fileUpload.getContentType();
    }

    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
        fileUpload.setContentTransferEncoding(contentTransferEncoding);
    }

    @Override
    public String getContentTransferEncoding() {
        return fileUpload.getContentTransferEncoding();
    }

    @Override
    public long getMaxSize() {
        return fileUpload.getMaxSize();
    }

    @Override
    public void setMaxSize(long maxSize) {
        fileUpload.setMaxSize(maxSize);
    }

    @Override
    public void checkSize(long newSize) throws IOException {
        fileUpload.checkSize(newSize);
    }

    @Override
    public void setContent(ByteBuf buffer) throws IOException {
        fileUpload.setContent(buffer);
    }

    @Override
    public void addContent(ByteBuf buffer, boolean last) throws IOException {
        fileUpload.addContent(buffer, last);
    }

    @Override
    public void setContent(File file) throws IOException {
        fileUpload.setContent(file);
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
        fileUpload.setContent(inputStream);
    }

    @Override
    public boolean isCompleted() {
        return fileUpload.isCompleted();
    }

    @Override
    public long length() {
        return fileUpload.length();
    }

    @Override
    public long definedLength() {
        return fileUpload.definedLength();
    }

    @Override
    public void delete() {
        fileUpload.delete();
    }

    @Override
    public byte[] get() throws IOException {
        return fileUpload.get();
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
        return fileUpload.getByteBuf();
    }

    @Override
    public ByteBuf getChunk(int length) throws IOException {
        return fileUpload.getChunk(length);
    }


    @Override
    public String getString() throws IOException {
        return fileUpload.toString();
    }

    @Override
    public String getString(Charset encoding) throws IOException {
        return fileUpload.getString(encoding);
    }

    @Override
    public void setCharset(Charset charset) {
        fileUpload.setCharset(charset);
    }

    @Override
    public Charset getCharset() {
        return fileUpload.getCharset();
    }

    @Override
    public boolean renameTo(File dest) throws IOException {
        return fileUpload.renameTo(dest);
    }

    @Override
    public boolean isInMemory() {
        return fileUpload.isInMemory();
    }

    @Override
    public File getFile() throws IOException {
        return fileUpload.getFile();
    }

    @Override
    public ByteBuf content() {
        return fileUpload.content();
    }

    @Override
    public FileUpload copy() {
        return fileUpload.copy();
    }

    @Override
    public FileUpload duplicate() {
        return fileUpload.duplicate();
    }

    @Override
    public FileUpload retainedDuplicate() {
        return fileUpload.retainedDuplicate();
    }

    @Override
    public FileUpload replace(ByteBuf content) {
        return fileUpload.replace(content);
    }

    @Override
    public String getName() {
        return fileUpload.getName();
    }

    @Override
    public HttpDataType getHttpDataType() {
        return fileUpload.getHttpDataType();
    }

    @Override
    public int refCnt() {
        return fileUpload.refCnt();
    }

    @Override
    public FileUpload retain() {
        return fileUpload.retain();
    }

    @Override
    public FileUpload retain(int increment) {
        return fileUpload.retain(increment);
    }

    @Override
    public FileUpload touch() {
        return fileUpload.touch();
    }

    @Override
    public FileUpload touch(Object hint) {
        return fileUpload.touch(hint);
    }

    @Override
    public boolean release() {
        return fileUpload.release();
    }

    @Override
    public boolean release(int decrement) {
        return fileUpload.release(decrement);
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
        return fileUpload.compareTo(o);
    }
}
