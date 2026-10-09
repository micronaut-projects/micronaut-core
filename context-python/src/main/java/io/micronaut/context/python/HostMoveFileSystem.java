/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python;

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * The filesystem of a GraalPy context that runs on the virtual filesystem, with {@code move}
 * dispatched to the host for host paths.
 * <p>
 * GraalPy combines its virtual filesystem and the host filesystem with a Truffle composite
 * filesystem, which does not implement {@code move}: every move falls back to copy and delete
 * and rejects {@link StandardCopyOption#ATOMIC_MOVE}. The Java POSIX backend implements
 * {@code os.rename} and {@code os.replace} as an atomic move, so both failed with
 * "Atomic move not supported" for ordinary host files. A move whose source and target are both
 * outside the mount point of the virtual filesystem is performed by the host filesystem here,
 * atomically when the host supports it (two paths on the same file store) and as a plain
 * replacing move otherwise. Moves that involve the read-only virtual filesystem keep the
 * behavior of the composite filesystem.
 */
final class HostMoveFileSystem implements FileSystem {

    private static final Logger LOG = LoggerFactory.getLogger(HostMoveFileSystem.class);
    private static final String DELEGATING_FILE_SYSTEM_FIELD = "delegatingFileSystem";

    private final FileSystem delegate;
    private final FileSystem host;
    private final Path mountPoint;

    HostMoveFileSystem(FileSystem delegate, FileSystem host, Path mountPoint) {
        this.delegate = delegate;
        this.host = host;
        this.mountPoint = mountPoint;
    }

    /**
     * Wrap the filesystem GraalPy uses for the given virtual filesystem.
     *
     * @param vfs The virtual filesystem; it must allow host read and write access (the default)
     * @return The filesystem, or {@code null} when the GraalPy version does not expose it
     */
    static @Nullable FileSystem of(VirtualFileSystem vfs) {
        FileSystem delegate = delegatingFileSystem(vfs);
        if (delegate == null) {
            return null;
        }
        FileSystem host = FileSystem.newDefaultFileSystem();
        return new HostMoveFileSystem(delegate, host, host.parsePath(vfs.getMountPoint()));
    }

    private static @Nullable FileSystem delegatingFileSystem(VirtualFileSystem vfs) {
        try {
            Field field = VirtualFileSystem.class.getDeclaredField(DELEGATING_FILE_SYSTEM_FIELD);
            field.setAccessible(true);
            return field.get(vfs) instanceof FileSystem fileSystem ? fileSystem : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug("GraalPy does not expose the filesystem of its virtual filesystem; os.rename of host files stays unsupported", e);
            return null;
        }
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        if (isInVirtualFileSystem(source) || isInVirtualFileSystem(target)) {
            delegate.move(source, target, options);
            return;
        }
        Path absoluteSource = delegate.toAbsolutePath(source);
        Path absoluteTarget = delegate.toAbsolutePath(target);
        try {
            host.move(absoluteSource, absoluteTarget, options);
        } catch (AtomicMoveNotSupportedException e) {
            // another file store: the move cannot be atomic, replace the target like a non-atomic rename
            host.move(absoluteSource, absoluteTarget, Arrays.stream(options)
                .filter(option -> option != StandardCopyOption.ATOMIC_MOVE)
                .toArray(CopyOption[]::new));
        }
    }

    private boolean isInVirtualFileSystem(Path path) {
        return delegate.toAbsolutePath(path).normalize().startsWith(mountPoint);
    }

    @Override
    public Path parsePath(URI uri) {
        return delegate.parsePath(uri);
    }

    @Override
    public Path parsePath(String path) {
        return delegate.parsePath(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        delegate.checkAccess(path, modes, linkOptions);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        delegate.createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        delegate.delete(path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return delegate.newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return delegate.newDirectoryStream(dir, filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return delegate.toAbsolutePath(path);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        return delegate.toRealPath(path, linkOptions);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return delegate.readAttributes(path, attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        delegate.setAttribute(path, attribute, value, options);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        delegate.copy(source, target, options);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        delegate.createLink(link, existing);
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        delegate.createSymbolicLink(link, target, attrs);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        return delegate.readSymbolicLink(link);
    }

    @Override
    public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        delegate.setCurrentWorkingDirectory(currentWorkingDirectory);
    }

    @Override
    public String getSeparator() {
        return delegate.getSeparator();
    }

    @Override
    public String getPathSeparator() {
        return delegate.getPathSeparator();
    }

    @Override
    public String getMimeType(Path path) {
        return delegate.getMimeType(path);
    }

    @Override
    public Charset getEncoding(Path path) {
        return delegate.getEncoding(path);
    }

    @Override
    public Path getTempDirectory() {
        return delegate.getTempDirectory();
    }

    @Override
    public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
        return delegate.isSameFile(path1, path2, options);
    }

    @Override
    public long getFileStoreTotalSpace(Path path) throws IOException {
        return delegate.getFileStoreTotalSpace(path);
    }

    @Override
    public long getFileStoreUnallocatedSpace(Path path) throws IOException {
        return delegate.getFileStoreUnallocatedSpace(path);
    }

    @Override
    public long getFileStoreUsableSpace(Path path) throws IOException {
        return delegate.getFileStoreUsableSpace(path);
    }

    @Override
    public long getFileStoreBlockSize(Path path) throws IOException {
        return delegate.getFileStoreBlockSize(path);
    }

    @Override
    public boolean isFileStoreReadOnly(Path path) throws IOException {
        return delegate.isFileStoreReadOnly(path);
    }
}
