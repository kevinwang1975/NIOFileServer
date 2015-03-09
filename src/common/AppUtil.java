package common;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AppUtil {

	private AppUtil(){}
	
	/**
	 * Collect all the files under the given directory, or just the given file itself if
	 * it is not a directory.
	 * 
	 * @param file
	 * @param files
	 */
	public static void collectFiles(File file, Collection<File> files) {
		if (file.isFile()) {
			files.add(file);
		}
		else if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				collectFiles(f, files);
			}
		}
	}

	/**
	 * Split the path of file to a series of sub-path relative to the given directory.
	 * This will help overcome the issue of different file separator in OSs.
	 * 
	 * @param dir
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static String[] toPath(File dir, File file) throws IOException {
		if (dir != null) {
			String dirPath = dir.getCanonicalPath();
			String filePath = file.getCanonicalPath();
			String subpath = filePath.substring(dirPath.length() + 1);
			String[] path = subpath.split(File.separator);
			return path;
		}
		return new String[] { file.getName() };
	}

	/**
	 * Build a full path of file with given directory, and relative path.
	 * 
	 * @param dir
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static File toFile(File dir, String[] path) throws IOException {
		String dirPath = dir.getCanonicalPath();
		StringBuilder sb = new StringBuilder(dirPath);
		for (int i = 0; i < path.length; i++) {
			sb.append(File.separator);
			sb.append(path[i]);
		}
		return new File(sb.toString());
	}
	
	/**
	 * Concatenate strings with the given separator char.
	 * 
	 * @param strs
	 * @param separator
	 * @return
	 */
	public static String toString(String[] strs, char separator) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < strs.length; i++) {
			sb.append(strs[i]);
			if (i+1 < strs.length) {
				sb.append(separator);
			}
		}
		return sb.toString();
	}
	
	/**
	 * Delete a file, or a directory recursively.
	 * 
	 * @param file
	 * @return
	 */
	public static boolean delete(File file) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for(File f : files) {
				if (!delete(f)) {
					return false;
				}
			}
		}
		return file.delete();
	}

	/**
	 * Convert int to bytes and store them in the array starting at the given index.
	 * 
	 * @param val
	 * @param bytes
	 * @param index
	 * @return the ending index, exclusive.
	 */
	public static int toBytes(int val, byte[] bytes, int index) {
		int typeBytes = Integer.SIZE/Byte.SIZE;
		checkBounds(bytes, index, typeBytes);
		for (int i = 0; i < typeBytes; i++) {
			int shiftBits = Byte.SIZE*(typeBytes - i - 1);
			byte b = (byte) (val >> shiftBits);
			bytes[index++] = b;
		}
		return index;
	}

	/**
	 * Convert long to bytes and store them in the array starting at the given index.
	 * @param val
	 * @param bytes
	 * @param index
	 * @return the ending index, exclusive.
	 */
	public static int toBytes(long val, byte[] bytes, int index) {
		int typeBytes = Long.SIZE/Byte.SIZE;
		checkBounds(bytes, index, typeBytes);
		for (int i = 0; i < typeBytes; i++) {
			int shiftBits = Byte.SIZE*(typeBytes - i - 1);
			byte b = (byte) (val >> shiftBits);
			bytes[index++] = b;
		}
		return index;
	}
	
	/**
	 * Convert string to bytes and store them in the array starting at the given index.
	 * 
	 * @param str
	 * @param bytes
	 * @param index
	 * @return the ending index, exclusive.
	 */
	public static int toBytes(String str, byte[] bytes, int index) {
		int typeBytes = Character.SIZE/Byte.SIZE;
		checkBounds(bytes, index, typeBytes*str.length());
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			for (int j = typeBytes - 1; j >= 0; j--) {
				int shiftBits = Byte.SIZE*j;
				byte b = (byte) (c >> shiftBits);
				bytes[index++] = b;
			}
		}
		return index;
	}

	/** 
	 * Convert the necessary number of bytes starting at the given index
	 * to an int.
	 * 
	 * @param bytes
	 * @param index
	 * @return
	 */
	public static int toInteger(byte[] bytes, int index) {
		int val = 0;
		int typeBytes = Integer.SIZE/Byte.SIZE;
		checkBounds(bytes, index, typeBytes);
		for (int i = 0; i < typeBytes; i++) {
			int b = bytes[index++];
			b = (b < 0) ? b + 256 : b;
			int shiftBits = Byte.SIZE*(typeBytes - i - 1);
			val += (b << shiftBits);
		}
		return val;
	}

	/** 
	 * Convert the necessary number of bytes starting at the given index
	 * to a long.
	 * 
	 * @param bytes
	 * @param index
	 * @return
	 */
	public static long toLong(byte[] bytes, int index) {
		long val = 0;
		int typeBytes = Long.SIZE/Byte.SIZE;
		checkBounds(bytes, index, typeBytes);
		for (int i = 0; i < typeBytes; i++) {
			int b = bytes[index++];
			b = (b < 0) ? b + 256 : b;
			int shiftBits = Byte.SIZE*(typeBytes - i - 1);
			val += (b << shiftBits);
		}
		return val;
	}

	/**
	 * Convert the bytes starting at the given index to a string of
	 * the given length.
	 * 
	 * @param bytes
	 * @param index
	 * @param strLeng
	 * @return
	 */
	public static String toString(byte[] bytes, int index, int strLeng) {
		int typeBytes = Character.SIZE/Byte.SIZE;
		checkBounds(bytes, index, typeBytes*strLeng);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < strLeng; i++) {
			int c = 0;
			for (int j = 0; j < typeBytes; j++) {
				int b = bytes[index++];
				b = (b < 0) ? b + 256 : b;
				int shiftBits = Byte.SIZE*j;
				c = (c << shiftBits) + b;
			}
			sb.append((char)c);
		}
		return sb.toString();
	}
	
	
	public static byte[] packFilenames(File dir, List<File> files) throws IOException {
		byte[] bytes = new byte[1024*1024];
		int index = 0;
		index = toBytes(files.size(), bytes, index);
		for (File file : files) {
			String[] path = toPath(dir, file);
			bytes = ensureCapacity(bytes, index+Integer.SIZE/Byte.SIZE);
			index = toBytes(path.length, bytes, index);
			for (String level : path) {
				bytes = ensureCapacity(bytes, index+Integer.SIZE/Byte.SIZE);
				index = toBytes(level.length(), bytes, index);
				bytes = ensureCapacity(bytes, index+level.length()*Character.SIZE/Byte.SIZE);
				index = toBytes(level, bytes, index);
			}
		}
		bytes = Arrays.copyOf(bytes, index);
		return bytes;
	}
	
	public static String[] unpackFilenames(byte[] bytes) {
		int index = 0;
		int fileCount = toInteger(bytes, index);
		String[] filenames = new String[fileCount];
		index += Integer.SIZE/Byte.SIZE;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fileCount; i++) {
			int levelCount = toInteger(bytes, index);
			index += Integer.SIZE/Byte.SIZE;
			sb.delete(0, sb.length());
			for (int j = 0; j < levelCount; j++) {
				sb.append(File.separator);
				int strLeng = toInteger(bytes, index);
				index += Integer.SIZE/Byte.SIZE;
				String level = toString(bytes, index, strLeng);
				sb.append(level);
				index += strLeng*Character.SIZE/Byte.SIZE;
			}
			filenames[i] = sb.toString();
		}
		return filenames;
	}
	
	/**
	 * Utility method used to ensure the capacity of byte array.
	 * 
	 * @param bytes
	 * @param size
	 * @param factor
	 * @return
	 */
	private static byte[] ensureCapacity(byte[] bytes, int capacity) {
		if (bytes.length < capacity) {
			int newsize = Math.max(1, bytes.length) << 1;
			bytes = Arrays.copyOf(bytes, newsize);
		}
		return bytes;
	}
	
	/**
	 * Utility method used to bounds check the byte array
     * and requested offset & length values.
     * 
	 * @param bytes
	 * @param offset
	 * @param length
	 */
	public static void checkBounds(byte[] bytes, int offset, int length) {
        if (length < 0)
            throw new IndexOutOfBoundsException("Index out of range: " + length);
        if (offset < 0)
            throw new IndexOutOfBoundsException("Index out of range: " + offset);
        if (offset > bytes.length - length)
            throw new IndexOutOfBoundsException("Index out of range: " + (offset + length));
    }
	
	/**
	 * Test if a given file is located inside the given directory.
	 * 
	 * @param file
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static boolean isLocatedInside(File file, File dir) throws IOException {
		return file.getCanonicalPath().startsWith(dir.getCanonicalPath());
	}
	
	public static void close(Closeable io) {
		if (io != null) {
			try {
				io.close();
			} catch (IOException e) {
				e.printStackTrace();
				// nothing we can do about
			}
		}
	}
	
	public static void read(InputStream is, long dataLength, OutputStream os) throws IOException {
		int read;
		byte[] bytes = new byte[1024*4];
		long readLength = 0;
		while (true) {
			read = is.read(bytes);
			if (read == -1) {
				break;
			}
			if (read == 0) {
				continue;
			}
			readLength += read;
			if (readLength > dataLength) {
				read -= (readLength - dataLength);
			}
			os.write(bytes, 0, read);
			if (readLength >= dataLength) {
				break;
			}
		}
		os.flush();
	}
	
}
