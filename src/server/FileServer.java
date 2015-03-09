package server;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class FileServer extends NIOServer {

	private File root;

	private Map<SocketChannel, ChannelHandler> clients = new HashMap<SocketChannel, ChannelHandler>();

	@Override
	protected void startServer() throws IOException {
		if (!root.exists()) {
			boolean made = root.mkdirs();
			if (!made) {
				throw new IOException("could not create root folder");
			}
		}
		super.startServer();
	}

	@Override
	protected void handleClient(SelectionKey key) throws IOException {
		ChannelHandler hanlder = clients.get(key.channel());
		if (hanlder == null) {
			throw new IllegalStateException("unknown client");
		}
		try {
			if(key.isWritable()) {
				hanlder.send();
			}
			if (key.isReadable()) {
				hanlder.recv();
			}	
		} catch (IOException e) {
			hanlder.closeIOStream();
			System.out.println("remove client: " + key.channel());
			clients.remove(key.channel());
			key.channel().close();
			throw e;
		}
	}

	@Override
	protected void registeredClient(SocketChannel sc) throws IOException {
		ChannelHandler hanlder = new ChannelHandler();
		hanlder.setChannel(sc);
		hanlder.setRoot(getRoot());
		hanlder.setSelector(getSelector());
		clients.put(sc, hanlder);
	}

	public File getRoot() {
		return root;
	}

	public void setRoot(File root) {
		this.root = root;
	}

	public static void main(String[] args) {
		String userhome = System.getProperty("user.home");
		String rootdir = userhome + File.separator + 
				"tmp" + File.separator + "svrfiles";
		FileServer filesrv = new FileServer();
		filesrv.setPort(12345);
		filesrv.setRoot(new File(rootdir));
		System.out.println(String.format("File server started on port %d...", filesrv.getPort()));
		filesrv.run();
	}
}
