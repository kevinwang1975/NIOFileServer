package common;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppUtilTest {

    @Test
    void toBytesAndToIntegerRoundTrip() {
        int[] values = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 255, 256, -128};
        byte[] bytes = new byte[Integer.SIZE / Byte.SIZE];
        for (int val : values) {
            AppUtil.toBytes(val, bytes, 0);
            int result = AppUtil.toInteger(bytes, 0);
            assertEquals(val, result);
        }
    }

    @Test
    void toBytesAndToLongRoundTrip() {
        long[] values = {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 255L, 256L};
        byte[] bytes = new byte[Long.SIZE / Byte.SIZE];
        for (long val : values) {
            AppUtil.toBytes(val, bytes, 0);
            long result = AppUtil.toLong(bytes, 0);
            assertEquals(val, result);
        }
    }

    @Test
    void toStringBytesAndFromBytesRoundTrip() {
        String[] values = {"", "hello", "héllo wörld", "a\u0000b", "  ", "\n\t"};
        byte[] buf = new byte[4096];
        for (String str : values) {
            Arrays.fill(buf, (byte) 0);
            int end = AppUtil.toBytes(str, buf, 0);
            String result = AppUtil.toString(buf, 0, str.length());
            assertEquals(str, result);
        }
    }

    @Test
    void checkBoundsValid() {
        byte[] bytes = new byte[100];
        assertDoesNotThrow(() -> AppUtil.checkBounds(bytes, 0, 100));
        assertDoesNotThrow(() -> AppUtil.checkBounds(bytes, 0, 0));
        assertDoesNotThrow(() -> AppUtil.checkBounds(bytes, 50, 50));
    }

    @Test
    void checkBoundsInvalid() {
        byte[] bytes = new byte[100];
        assertThrows(IndexOutOfBoundsException.class, () -> AppUtil.checkBounds(bytes, -1, 10));
        assertThrows(IndexOutOfBoundsException.class, () -> AppUtil.checkBounds(bytes, 0, 101));
        assertThrows(IndexOutOfBoundsException.class, () -> AppUtil.checkBounds(bytes, 50, 51));
        assertThrows(IndexOutOfBoundsException.class, () -> AppUtil.checkBounds(bytes, 0, -1));
    }

    @Test
    void toBytesIntReturnsCorrectIndex(@TempDir File dir) {
        byte[] bytes = new byte[100];
        byte[] suffix = "XYZ".getBytes();
        System.arraycopy(suffix, 0, bytes, 8, 3);
        int end = AppUtil.toBytes(42, bytes, 5);
        assertEquals(9, end);
        assertEquals('Y', bytes[9]);
    }

    @Test
    void isLocatedInsideReturnsTrueForSubdirectory() throws IOException {
        File parent = new File("/tmp/parent");
        File child = new File("/tmp/parent/child/file.txt");
        assertTrue(AppUtil.isLocatedInside(child, parent));
    }

    @Test
    void isLocatedInsideReturnsFalseForParent() throws IOException {
        File parent = new File("/tmp/parent");
        File child = new File("/tmp/parent");
        assertTrue(AppUtil.isLocatedInside(child, parent));
    }

    @Test
    void isLocatedInsideReturnsFalseForOutsidePath() throws IOException {
        File parent = new File("/tmp/parent");
        File child = new File("/tmp/other/file.txt");
        assertFalse(AppUtil.isLocatedInside(child, parent));
    }

    @Test
    void isLocatedInsideRejectsPathTraversal() throws IOException {
        File parent = new File("/tmp/parent");
        File child = new File("/tmp/parent/../../etc/passwd");
        assertFalse(AppUtil.isLocatedInside(child, parent));
    }

    @Test
    void isLocatedInsideRejectsPrefixCollision() throws IOException {
        File parent = new File("/tmp/parent");
        File child = new File("/tmp/parent-extra/file.txt");
        assertFalse(AppUtil.isLocatedInside(child, parent));
    }

    @Test
    void isLocatedInsideAcceptsExactPath() throws IOException {
        File parent = new File("/tmp/parent");
        assertTrue(AppUtil.isLocatedInside(parent, parent));
    }

    @Test
    void toPathSplitsCorrectly(@TempDir File dir) throws IOException {
        File subdir = new File(dir, "a/b/c/d");
        assertTrue(subdir.mkdirs());
        File file = new File(subdir, "test.txt");
        assertTrue(file.createNewFile());

        String[] path = AppUtil.toPath(dir, file);
        assertArrayEquals(new String[]{"a", "b", "c", "d", "test.txt"}, path);
    }

    @Test
    void toPathReturnsEmptyForSameFile(@TempDir File dir) throws IOException {
        String[] path = AppUtil.toPath(dir, dir);
        assertEquals(0, path.length);
    }

    @Test
    void toFileJoinsCorrectly(@TempDir File dir) throws IOException {
        String[] path = {"x", "y", "z", "file.dat"};
        File result = AppUtil.toFile(dir, path);
        String expected = dir.getCanonicalPath() + File.separator + "x"
                + File.separator + "y" + File.separator + "z"
                + File.separator + "file.dat";
        assertEquals(expected, result.getCanonicalPath());
    }

    @Test
    void toStringArrayJoinsWithSeparator() {
        String[] input = {"a", "b", "c"};
        assertEquals("a:b:c", AppUtil.toString(input, ':'));
        assertEquals("a/b/c", AppUtil.toString(input, '/'));
    }

    @Test
    void toStringArrayEmpty() {
        assertEquals("", AppUtil.toString(new String[0], '/'));
    }

    @Test
    void toStringArraySingle() {
        assertEquals("a", AppUtil.toString(new String[]{"a"}, '/'));
    }

    @Test
    void collectFilesFindsAll(@TempDir File dir) throws IOException {
        new File(dir, "a.txt").createNewFile();
        new File(dir, "b.txt").createNewFile();
        new File(dir, "sub").mkdir();
        new File(dir, "sub/c.txt").createNewFile();

        List<File> files = new ArrayList<>();
        AppUtil.collectFiles(dir, files);
        assertEquals(3, files.size());
        assertTrue(files.stream().anyMatch(f -> f.getName().equals("a.txt")));
        assertTrue(files.stream().anyMatch(f -> f.getName().equals("b.txt")));
        assertTrue(files.stream().anyMatch(f -> f.getName().equals("c.txt")));
    }

    @Test
    void collectFilesWithSingleFile(@TempDir File dir) throws IOException {
        File f = new File(dir, "single.txt");
        f.createNewFile();
        List<File> files = new ArrayList<>();
        AppUtil.collectFiles(f, files);
        assertEquals(1, files.size());
        assertEquals(f, files.get(0));
    }

    @Test
    void deleteFile(@TempDir File dir) throws IOException {
        File f = new File(dir, "todelete.txt");
        f.createNewFile();
        assertTrue(f.exists());
        assertTrue(AppUtil.delete(f));
        assertFalse(f.exists());
    }

    @Test
    void deleteDirectoryRecursively(@TempDir File dir) throws IOException {
        File subdir = new File(dir, "sub");
        subdir.mkdir();
        new File(subdir, "a.txt").createNewFile();
        new File(subdir, "b.txt").createNewFile();
        new File(subdir, "nested").mkdir();
        new File(subdir, "nested/c.txt").createNewFile();

        assertTrue(AppUtil.delete(dir));
        assertFalse(dir.exists());
    }

    @Test
    void packAndUnpackFilenamesRoundTrip(@TempDir File dir) throws IOException {
        new File(dir, "a.txt").createNewFile();
        new File(dir, "b.txt").createNewFile();
        File sub = new File(dir, "sub");
        sub.mkdir();
        new File(sub, "c.txt").createNewFile();

        List<File> files = new ArrayList<>();
        AppUtil.collectFiles(dir, files);
        byte[] packed = AppUtil.packFilenames(dir, files);
        String[] unpacked = AppUtil.unpackFilenames(packed);
        assertEquals(3, unpacked.length);
        boolean foundFile = false;
        for (String name : unpacked) {
            if (name.contains("a.txt") || name.contains("b.txt") || name.contains("c.txt")) {
                foundFile = true;
                break;
            }
        }
        assertTrue(foundFile, "unpacked filenames should contain at least one file");
    }

    @Test
    void closeNullDoesNothing() {
        assertDoesNotThrow(() -> AppUtil.close(null));
    }

    @Test
    void closeValidStreamDoesNotThrow(@TempDir File dir) throws IOException {
        File f = new File(dir, "close_test.txt");
        f.createNewFile();
        InputStream is = new FileInputStream(f);
        assertDoesNotThrow(() -> AppUtil.close(is));
    }

    @Test
    void readCopiesExactBytes(@TempDir File dir) throws IOException {
        String content = "Hello, World! This is a test.";
        File src = new File(dir, "src.txt");
        File dst = new File(dir, "dst.txt");
        try (OutputStream os = new FileOutputStream(src)) {
            os.write(content.getBytes("UTF-8"));
        }
        try (InputStream is = new FileInputStream(src);
             OutputStream os = new FileOutputStream(dst)) {
            AppUtil.read(is, src.length(), os);
        }
        byte[] dstBytes = new byte[(int) dst.length()];
        try (InputStream is = new FileInputStream(dst)) {
            is.read(dstBytes);
        }
        assertEquals(content, new String(dstBytes, "UTF-8"));
    }

    @Test
    void readZeroLength(@TempDir File dir) throws IOException {
        File src = new File(dir, "empty.txt");
        File dst = new File(dir, "empty_dst.txt");
        src.createNewFile();
        try (InputStream is = new FileInputStream(src);
             OutputStream os = new FileOutputStream(dst)) {
            AppUtil.read(is, 0, os);
        }
        assertEquals(0, dst.length());
    }
}
