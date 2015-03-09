package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public abstract class NIOServer implements Runnable {

	private ServerSocketChannel channel;
	private volatile boolean stop;
	private Selector selector;
	private int port;

	protected abstract void handleClient(SelectionKey key) throws IOException;
	protected abstract void registeredClient(SocketChannel sc) throws IOException;

	protected void startServer() throws IOException {
		if (port == 0) {
			throw new IllegalArgumentException("port not specified");
		}
		channel = ServerSocketChannel.open();
		channel.configureBlocking(false);
		ServerSocket server = channel.socket();
		server.bind(new InetSocketAddress(port));
		selector = Selector.open();
		channel.register(selector, SelectionKey.OP_ACCEPT);
	}

	protected synchronized void stopServer() throws IOException {
		stop = true;
		channel.close();
	}

	public void run() {
		try {
			startServer();
		} catch (IOException ioe) {
			System.out.println("Can't start server:  " + ioe);
			return;
		}

		while (!isStop()) {
			try {
				selector.select();
			} catch (IOException ioe) {
				System.err.println("Server error: " + ioe);
				return;
			}

			Iterator<SelectionKey> it = selector.selectedKeys().iterator();
			while (it.hasNext()) {
				SelectionKey key = (SelectionKey) it.next();
				if (key.isReadable() || key.isWritable()) {
					// Key represents a socket client
					try {
						handleClient(key);
					} catch (IOException ioe) {
						// Client disconnected
						key.cancel();
					}
				} else if (key.isAcceptable()) {
					try {
						handleServer(key);
					} catch (IOException ioe) {
						// Accept error; treat as fatal
						throw new IllegalStateException(ioe);
					}
				} else {
					System.out.println("unknown key state");
				}
				it.remove();
			}
		}
	}

	protected void handleServer(SelectionKey key) throws IOException {
		SocketChannel sc = channel.accept();
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ);
		registeredClient(sc);
	}

	public synchronized boolean isStop() {
		return stop;
	}

	public synchronized void setStop(boolean stop) {
		this.stop = stop;
	}

	public ServerSocketChannel getChannel() {
		return channel;
	}

	public void setChannel(ServerSocketChannel channel) {
		this.channel = channel;
	}

	public Selector getSelector() {
		return selector;
	}

	public void setSelector(Selector selector) {
		this.selector = selector;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

}
