package server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import common.AppConstant.Action;
import common.AppConstant.Message;
import common.AppConstant.State;
import common.AppUtil;
import common.Header;
import common.HeaderException;

public class ChannelHandler {

	private State state = State.IDLE;
	private long readLength = 0;
	private int headerLength = 0;
	private final Header header = new Header();
	private byte[] bytes = new byte[4096];
	private final ByteBuffer inBuf  = ByteBuffer.allocateDirect(4096);
	private ByteBuffer outBuf = ByteBuffer.allocateDirect(4096);
	private BufferedInputStream bis;
	private BufferedOutputStream bos;

	private SocketChannel channel;
	private Selector selector;
	private File root;

	public void closeIOStream() throws IOException {
		if (bis != null) {
			bis.close();
		}
		if (bos != null) {
			bos.close();
		}
	}

	public void send() throws IOException {
		// send the data in the buffer, if cannot send them
		// all at once, register for the next round, until
		// all data in the buffer has been sent.
		int remaining = outBuf.remaining();
		int written = channel.write(outBuf);
		if (written < remaining) {
			channel.register(selector, SelectionKey.OP_WRITE);
			return;
		}

		switch (state) {
		case SEND:
			// the file content has not been completely read,
			// read some more into the buffer "bytes", then 
			// put them into outgoing buffer.
			if (readLength < header.getDataLength()) {
				int read = bis.read(bytes);
				outBuf.clear();
				outBuf.put(bytes, 0, read);
				outBuf.flip();
				readLength += read;
				channel.register(selector, SelectionKey.OP_WRITE);		
			}
			// the file reading has completed, finish it properly
			// by clearing all the buffers, and go back to idle.
			else {
				readLength = 0;
				bis.close();
				bis = null;
				inBuf.clear();
				outBuf.clear();
				state = State.IDLE;
				channel.register(selector, SelectionKey.OP_READ);
			}
			break;

		default:
			outBuf.clear();
			channel.register(selector, SelectionKey.OP_READ);
			break;
		}
	}

	public void recv() throws IOException {
		inBuf.clear();
		int read = channel.read(inBuf);
		if (read == -1) {
			throw new IOException("socket closed");
		}
		if (read == 0) {
			return;
		}

		switch (state) {
		case IDLE:
			// copy received data to buffer "bytes".
			inBuf.flip();
			inBuf.get(bytes, (int)readLength, inBuf.limit());
			readLength += inBuf.limit();

			// header length is 0, meaning the header is not yet received.
			// the first 4 bytes is header length, if 4 or more than 4 bytes
			// has been read, convert it to an integer which is the
			// length of header that will come next.
			if (headerLength == 0 && readLength >= Integer.SIZE/Byte.SIZE) {
				headerLength = AppUtil.toInteger(bytes, 0);
				// the header length is now known, the bytes is buffer
				// for received data, if its size is not enough to store
				// the whole header to come, create a new buffer in size
				// of the header length, with the data in the old buffer
				// copied to the beginning of the new buffer.
				if (headerLength > bytes.length) {
					bytes = Arrays.copyOf(bytes, headerLength);
				}
			}
			// the whole header has been received, check what message it brings.
			if (headerLength > 0 && readLength >= headerLength) {
				try {
					header.toHeader(bytes, 0, headerLength);
				} catch (HeaderException e) {
					throw new IOException("Error in header");
				}
				
				switch (header.getAction()) {
				case PUT: // the client wants to send a file to the server
					preparePutAck();
					sendHeader();
					break;

				case GET: // the client wants to get a file from the server
					prepareGetAck();
					sendHeader();
					break;

				case DEL: // the client wants to delete a file on the server
					prepareDelAck();
					sendHeader();
					break;
					
				case LST:
					prepareLstAck();
					sendHeader();
					break;
					
				default:
					break;
				}
				readLength = 0;
				headerLength = 0;
			}
			break;

		case RECV:
			// write received data to output stream
			inBuf.flip();
			inBuf.get(bytes, 0, read);
			bos.write(bytes, 0, read);
			readLength += read;
			// the whole file has been received, finish it properly
			// by sending the acknowledgment, clearing buffers,
			// closing input/output streams.
			if (readLength == header.getDataLength()) {
				readLength = 0;
				bos.flush();
				bos.close();
				bos = null;
				inBuf.clear();
				outBuf.clear();
				state = State.IDLE;
				header.setAction(Action.PUT_FIN);
				sendHeader();
			}
			break;
			
		default:
			break;
		}
	}

	/**
	 * send the header that has been prepared.
	 * @throws IOException
	 */
	private void sendHeader() throws IOException {
		byte[] bytes = header.toBytes();
		if (outBuf.capacity() >= bytes.length) {
			outBuf.clear();
		}
		else {
			outBuf = ByteBuffer.allocateDirect(bytes.length);
		}
		outBuf.put(bytes, 0, bytes.length);
		outBuf.flip();
		channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE );	
	}

	/**
	 * prepare the acknowledgment to the Put request. 
	 * @throws IOException
	 */
	private void preparePutAck() throws IOException {
		File file = AppUtil.toFile(root, header.getPath());
		if (!AppUtil.isLocatedInside(file, root)) {
			header.setMessage(Message.PERMISSION_DENIED);
			state = State.IDLE;
		}
		else if (file.exists() && !header.isOverwrite()) {
			header.setMessage(Message.FILE_EXIST);
			state = State.IDLE;
		}
		else {
			boolean created = false;
			if (!file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			if (file.getParentFile().exists()) {
				created = file.createNewFile();
			}
			if (created) {
				header.setMessage(Message.FILE_CREATED);
				if (header.getDataLength() > 0) {
					bos = new BufferedOutputStream(new FileOutputStream(file));
					state = State.RECV;
				}
				else {
					state = State.IDLE;
				}
			}
			else {
				header.setMessage(Message.FILE_NOT_CREATED);
				state = State.IDLE;
			}
		}
		header.setAction(Action.PUT_ACK);
	}

	/**
	 * prepare the acknowledgment to the Get request.
	 * @throws IOException
	 */
	private void prepareGetAck() throws IOException {
		File file = AppUtil.toFile(root, header.getPath());
		if (!AppUtil.isLocatedInside(file, root)) {
			header.setMessage(Message.PERMISSION_DENIED);
			state = State.IDLE;
		}
		else if (!file.exists()) {
			header.setMessage(Message.FILE_NOT_EXIST);
			state = State.IDLE;
		}
		else {
			header.setMessage(Message.FILE_EXIST);
			header.setDataLength(file.length());
			if (header.getDataLength() > 0) {
				state = State.SEND;
				bis = new BufferedInputStream(new FileInputStream(file));
			}
			else {
				state = State.IDLE;
			}
		}
		header.setAction(Action.GET_ACK);
	}

	/**
	 * prepare the acknowledgment to the Del request.
	 * @throws IOException
	 */
	private void prepareDelAck() throws IOException {
		File file = AppUtil.toFile(root, header.getPath());
		if (!AppUtil.isLocatedInside(file, root)) {
			header.setMessage(Message.PERMISSION_DENIED);
		}
		else if (!file.exists()) {
			header.setMessage(Message.FILE_NOT_EXIST);
		}
		else if (file.delete()) {
			header.setMessage(Message.FILE_DELETED);
		}
		else {
			header.setMessage(Message.FILE_NOT_DELETED);
		}
		state = State.IDLE;
		header.setAction(Action.DEL_ACK);
	}
	
	/**
	 * prepare the acknowledgment to the List request.
	 * @throws IOException
	 */
	private void prepareLstAck() throws IOException {
		File file = AppUtil.toFile(root, header.getPath());
		if (!AppUtil.isLocatedInside(file, root)) {
			header.setMessage(Message.PERMISSION_DENIED);
			state = State.IDLE;
		}
		else if (!file.exists()) {
			header.setMessage(Message.FILE_NOT_EXIST);
			state = State.IDLE;
		}
		else {
			header.setMessage(Message.FILE_EXIST);
			List<File> files = new ArrayList<File>();
			AppUtil.collectFiles(file, files);
			if (files.size() == 0) {
				header.setDataLength(0);
				state = State.IDLE;
			}
			else {
				byte[] bytes = AppUtil.packFilenames(root, files);
				header.setDataLength(bytes.length);
				state = State.SEND;
				bis = new BufferedInputStream(new ByteArrayInputStream(bytes));
			}
		}
		header.setAction(Action.LST_ACK);
	}
	
	public Selector getSelector() {
		return selector;
	}

	public void setSelector(Selector selector) {
		this.selector = selector;
	}

	public File getRoot() {
		return root;
	}

	public void setRoot(File root) {
		this.root = root;
	}

	public SocketChannel getChannel() {
		return channel;
	}

	public void setChannel(SocketChannel channel) {
		this.channel = channel;
	}

}
