// Use of this source code is governed by a BSD-style license
package com.cs.common.model;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class JarFileClassLoaderTest {

    private Path tempDirWithJars() throws IOException {
        Path root = Files.createTempDirectory("jarloader");
        Files.createFile(root.resolve("a.jar"));
        Files.createFile(root.resolve("readme.txt"));
        Path sub = root.resolve("sub");
        Files.createDirectory(sub);
        Files.createFile(sub.resolve("b.jar"));
        return root;
    }

    @Test
    public void testCollectsJarsFromRootAndNestedDirs() throws IOException {
        Path root = tempDirWithJars();
        try (URLClassLoader loader = new JarFileClassLoader(root.toString(), getClass().getClassLoader())) {
            String[] urls = Arrays.stream(loader.getURLs()).map(URL::toString).toArray(String[]::new);
            assertEquals(2, urls.length);
            assertTrue(urls[0].endsWith("a.jar"));
            assertTrue(urls[1].endsWith("sub/b.jar") || urls[1].endsWith("sub" + File.separator + "b.jar"));
        }
    }

    @Test
    public void testVarargsConstructorAggregatesPaths() throws IOException {
        Path root = tempDirWithJars();
        Path other = Files.createTempDirectory("jarloader2");
        Files.createFile(other.resolve("c.jar"));
        try (URLClassLoader loader = new JarFileClassLoader(
                new String[]{root.toString(), other.toString()}, getClass().getClassLoader())) {
            assertEquals(3, loader.getURLs().length);
        }
    }

    @Test
    public void testNullPathsRejected() {
        try {
            new JarFileClassLoader((String[]) null, getClass().getClassLoader());
            fail("null paths array must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        // single null path: current implementation surfaces NPE while scanning the null entry
        try {
            new JarFileClassLoader((String) null, getClass().getClassLoader());
            fail("null single path must be rejected");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testEmptyPathsArrayRejected() {
        try {
            new JarFileClassLoader(new String[0], getClass().getClassLoader());
            fail("empty paths must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testMissingDirectoryYieldsNoJarError() {
        try {
            new JarFileClassLoader(new File("/definitely/not/exist-" + System.nanoTime()).toString(),
                    getClass().getClassLoader());
            fail("missing directory must raise No jar file found");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("No jar file found"));
        }
    }
}
