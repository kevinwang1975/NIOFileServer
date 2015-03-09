package client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import common.AppConstant.Action;
import common.AppUtil;
import common.Header;
import common.HeaderException;

public class FileClient {

	private volatile boolean stop;
	private SocketChannel socketchannel;
	
	public void connects(String server, int port) throws IOException {
		InetSocketAddress isa = new InetSocketAddress(server, port);
		socketchannel = SocketChannel.open(isa);
	}
	
	public void close() throws IOException {
		socketchannel.close();
	}
	
	public boolean isStop() {
		return stop;
	}

	public void setStop(boolean stop) {
		this.stop = stop;
	}

	public void receive(File dir, String[] path) throws IOException {
		Header header = new Header();
		header.setAction(Action.GET);
		header.setPath(path);

		byte[] bytes = header.toBytes();

		OutputStream os = socketchannel.socket().getOutputStream();
		os.write(bytes);
		os.flush();

		int pos = 0;
		int read;
		InputStream is = socketchannel.socket().getInputStream();

		int headerLength = 0;
		String filename = AppUtil.toString(path, File.separatorChar);
		while (!isStop()) {
			read = is.read(bytes, pos, bytes.length - pos);
			if (read == -1) {
				throw new IOException("socket closed.");
			}
			pos += read;
			if (headerLength == 0 && pos >= Integer.SIZE/Byte.SIZE) {
				headerLength = AppUtil.toInteger(bytes, 0);
				if (headerLength > bytes.length) {
					bytes = Arrays.copyOf(bytes, headerLength);
				}
			}
			if (headerLength == 0 || pos < headerLength) {
				continue;
			}
			
			try {
				header.toHeader(bytes, 0, headerLength);
			} catch (HeaderException e) {
				throw new IOException("Error in header");
			}
			
			switch (header.getAction()) {
			case GET_ACK:
				switch (header.getMessage()) {
				case PERMISSION_DENIED:
					System.out.println(String.format(
							"GET: permission denied [%s].", filename));
					break;
					
				case FILE_NOT_EXIST:
					System.out.println(String.format(
							"GET: file not exists [%s].", filename));
					break;

				case FILE_EXIST:
					boolean created = false;
					File file = AppUtil.toFile(dir, path);
					if (!file.getParentFile().exists()) {
						file.getParentFile().mkdirs();
					}
					if (file.getParentFile().exists()) {
						created = file.createNewFile();
					}
					if (!created) {
						throw new IOException(String.format(
								"failed to create file [%s]", filename));
					}
					long fileLength = header.getDataLength();
					if (fileLength > 0) {
						receiveFile(file, is, fileLength);
					}
					System.out.println(String.format(
							"GET: file received [%s].", filename));
					break;

				default:
					System.out.println(String.format("Unexpected message %s.",
							header.getMessage()));
					break;
				}
				break;

			default:
				System.out.println(String.format("Unexpected action %s.",
						header.getAction()));
				break;
			}
			break;
		}
	}
	
	private void receiveFile(File file, InputStream is, long fileLength) throws IOException {
		BufferedOutputStream bos = null;
		try {
			bos = new BufferedOutputStream(new FileOutputStream(file));
			AppUtil.read(is, fileLength, bos);
		} finally {
			AppUtil.close(bos);
		}
	}
	
	public void delete(String[] path) throws IOException {
		Header header = new Header();
		header.setAction(Action.DEL);
		header.setPath(path);

		byte[] bytes = header.toBytes();

		OutputStream os = socketchannel.socket().getOutputStream();
		os.write(bytes);
		os.flush();

		int pos = 0;
		int read;
		InputStream is = socketchannel.socket().getInputStream();

		int headerLength = 0;
		String filename = AppUtil.toString(path, File.separatorChar);
		while (!isStop()) {
			read = is.read(bytes, pos, bytes.length-pos);
			if (read == -1) {
				throw new IOException("socket closed.");
			}
			if (read == 0) {
				continue;
			}
			pos += read;
			if (headerLength == 0 && pos >= Integer.SIZE/Byte.SIZE) {
				headerLength = AppUtil.toInteger(bytes, 0);
				if (headerLength > bytes.length) {
					bytes = Arrays.copyOf(bytes, headerLength);
				}
			}
			if (headerLength == 0 || pos < headerLength) {
				continue;
			}
			
			try {
				header.toHeader(bytes, 0, headerLength);
			} catch (HeaderException e) {
				throw new IOException("Error in header");
			}
			
			switch (header.getAction()) {
			case DEL_ACK:
				switch (header.getMessage()) {
				case PERMISSION_DENIED:
					System.out.println(String.format(
							"DEL: permission denied [%s].", filename));
					break;
					
				case FILE_NOT_EXIST:
					System.out.println(String.format(
							"DEL: file not exists [%s].", filename));
					break;

				case FILE_DELETED:
					System.out.println(String.format(
							"DEL: file deleted [%s].", filename));
					break;

				case FILE_NOT_DELETED:
					System.out.println(String.format(
							"DEL: file not deleted [%s].", filename));
					break;
					
				default:
					break;
				}
				break;
				
			default:
				break;
			}
			break;
		}
	}
	
	public void send(File file, String[] path) throws IOException {
		Header header = new Header();
		header.setAction(Action.PUT);
		header.setPath(path);
		header.setDataLength(file.length());
		header.setOverwrite(false);

		byte[] bytes = header.toBytes();

		OutputStream os = socketchannel.socket().getOutputStream();
		os.write(bytes);
		os.flush();

		int pos = 0;
		int read;
		InputStream is = socketchannel.socket().getInputStream();

		int headerLength = 0;
		String filename = AppUtil.toString(path, File.separatorChar);
		
		LOOP:
		while (!isStop()) {
			read = is.read(bytes, pos, bytes.length-pos);
			if (read == -1) {
				throw new IOException("socket closed.");
			}
			pos += read;
			if (headerLength == 0 && pos >= Integer.SIZE/Byte.SIZE) {
				headerLength = AppUtil.toInteger(bytes, 0);
				if (headerLength > bytes.length) {
					bytes = Arrays.copyOf(bytes, headerLength);
				}
			}
			if (headerLength == 0 || pos < headerLength) {
				continue;
			}
			
			try {
				header.toHeader(bytes, 0, headerLength);
			} catch (HeaderException e) {
				throw new IOException("Error in header");
			}
			
			switch (header.getAction()) {
			case PUT_ACK:
				switch (header.getMessage()) {
				case PERMISSION_DENIED:
					System.out.println(String.format(
							"PUT: permission denied [%s].", filename));
					break LOOP;
					
				case FILE_EXIST:
					System.out.println(String.format(
							"PUT: file exists [%s].", filename));
					break LOOP;

				case FILE_NOT_CREATED:
					System.out.println(String.format(
							"PUT: could not create file [%s].", filename));
					break LOOP;

				case FILE_CREATED:
					if (header.getDataLength() == 0) {
						System.out.println(String.format(
								"PUT: file sent [%s].", filename));
						break LOOP;
					}
					sendFile(file, os, header.getDataLength());
					pos = 0;
					break;

				default:
					System.out.println(String.format(
							"Unexpected message %s.", header.getMessage()));
					break LOOP;
				}
				break;

			case PUT_FIN:
				System.out.println(String.format(
						"PUT: file sent [%s].", filename));
				break LOOP;

			default:
				System.out.println(String.format(
						"Unexpected action %s.", header.getAction()));
				break LOOP;
			}
		}
	}
	
	private void sendFile(File file, OutputStream os, long fileLength) throws IOException {
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(new FileInputStream(file));
			AppUtil.read(bis, fileLength, os);
		}
		finally {
			AppUtil.close(bis);
		}
	}
	
	public String[] list(String[] path) throws IOException {
		Header header = new Header();
		header.setAction(Action.LST);
		header.setPath(path);

		byte[] bytes = header.toBytes();

		OutputStream os = socketchannel.socket().getOutputStream();
		os.write(bytes);
		os.flush();

		int pos = 0;
		int read;
		InputStream is = socketchannel.socket().getInputStream();

		String[] filenames = new String[0];

		int headerLength = 0;
		String filename = AppUtil.toString(path, File.separatorChar);
		while (!isStop()) {
			read = is.read(bytes, pos, bytes.length - pos);
			if (read == -1) {
				throw new IOException("socket closed.");
			}
			pos += read;
			if (headerLength == 0 && pos >= Integer.SIZE/Byte.SIZE) {
				headerLength = AppUtil.toInteger(bytes, 0);
				if (headerLength > bytes.length) {
					bytes = Arrays.copyOf(bytes, headerLength);
				}
			}
			if (headerLength == 0 || pos < headerLength) {
				continue;
			}
			
			try {
				header.toHeader(bytes, 0, headerLength);
			} catch (HeaderException e) {
				throw new IOException("Error in header");
			}
			
			switch (header.getAction()) {
			case LST_ACK:
				switch (header.getMessage()) {
				case PERMISSION_DENIED:
					System.out.println(String.format(
							"LST: permission denied [%s].", filename));
					break;
					
				case FILE_NOT_EXIST:
					System.out.println(String.format(
							"LST: dir/file not exists [%s].", filename));
					break;

				case FILE_EXIST:
					long dataLength = header.getDataLength();
					if (dataLength > 0) {
						filenames = receiveList(is, dataLength);
					}
					System.out.println(String.format(
							"LST: list received [%s].", filename));
					break;

				default:
					System.out.println(String.format("Unexpected message %s.",
							header.getMessage()));
					break;
				}
				break;

			default:
				System.out.println(String.format("Unexpected action %s.",
						header.getAction()));
				break;
			}
			break;
		}
		return filenames;
	}
	
	private String[] receiveList(InputStream is, long dataLength) throws IOException {
		// an integer should be big enough for the size of a list of filenames
		ByteArrayOutputStream baos = new ByteArrayOutputStream((int) dataLength);
		AppUtil.read(is, dataLength, baos);
		byte[] bytes = baos.toByteArray();
		String[] filenames = AppUtil.unpackFilenames(bytes);
		return filenames;
	}
	
	public static void main(String[] args) {
		final String server   = "localhost";
		final int    port     = 12345;

		final String userhome = System.getProperty("user.home");
		final String readfrom = userhome + File.separator + 
				"tmp" + File.separator + "readfrom";
		final String saveto   = userhome + File.separator +
				"tmp" + File.separator + "saveto";

		final File srcdir = new File(readfrom);
		if (!srcdir.exists()) {
			System.err.println(String.format(
					"no such a director: %s", srcdir.getAbsolutePath()));
			System.exit(-1);
		}
		final File dstdir = new File(saveto);
		if (dstdir.exists() && !AppUtil.delete(dstdir)) {
			System.err.println(String.format(
					"could not delete directory %s", dstdir.getAbsolutePath()));
			System.exit(-2);
		}

		long startTimeMillis = System.currentTimeMillis();

		// remove existing files from the server
		try {
			FileClient client = new FileClient();
			client.connects(server, port);
			String[] filenames = client.list(new String[0]);
			System.out.println("existing file count:" + filenames.length);
			for (String filename : filenames) {
				client.delete(filename.split(File.separator));
			}
			client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		final ExecutorService threadPool = Executors.newFixedThreadPool(8);
		
		final AtomicInteger success = new AtomicInteger(0);
		final AtomicInteger failure = new AtomicInteger(0);
		
		Collection<File> fileList = new ArrayList<File>();
		AppUtil.collectFiles(srcdir, fileList);
		
		// send files to server, then retrieve them back
		for (final File file : fileList) {
			threadPool.submit(new Runnable() {
				@Override
				public void run() {
					try {
						FileClient client = new FileClient();
						client.connects(server, port);
						String[] path = AppUtil.toPath(srcdir, file);
						client.send(file, path);
						client.receive(dstdir, path);
						client.close();
						success.incrementAndGet();
					} catch (IOException e) {
						failure.incrementAndGet();
						e.printStackTrace();
					}
				}
			});
		}
		
		while (success.get() + failure.get() < fileList.size());
		threadPool.shutdown();
		
		long endTimeMillis = System.currentTimeMillis();
		System.out.println("time elapsed (ms):" + (endTimeMillis - startTimeMillis));
		System.out.println("success:" + success.get());
		System.out.println("failure:" + failure.get());
	}
}
