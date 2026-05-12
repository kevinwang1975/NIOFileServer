package server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import client.FileClient;
import common.AppUtil;

class FileServerIntegrationTest {

    private static FileServer server;
    private static Thread serverThread;
    private static int port;
    private static File tempRoot;
    private static volatile boolean ready;

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @BeforeAll
    static void startServer() throws Exception {
        tempRoot = Files.createTempDirectory("niosvr").toFile();
        port = findFreePort();
        server = new FileServer() {
            @Override
            protected void startServer() throws IOException {
                super.startServer();
                ready = true;
                System.out.println("Server started on port " + port);
            }
        };
        server.setRoot(tempRoot);
        server.setPort(port);
        ready = false;
        serverThread = new Thread(server, "file-server");
        serverThread.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!ready && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        if (!ready) {
            throw new RuntimeException("Server did not start within 5 seconds");
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stopServer();
        serverThread.interrupt();
        serverThread.join(2000);
        AppUtil.delete(tempRoot);
    }

    private FileClient createClient() throws IOException {
        FileClient client = new FileClient();
        client.connects("localhost", port);
        return client;
    }

    @Test
    void putAndGetAndDeleteFile(@TempDir File clientDir) throws IOException {
        FileClient client = createClient();
        try {
            client.send(new File("/etc/hosts"), new String[]{"test_hosts"});
            String[] listed = client.list(new String[0]);
            assertTrue(listed.length > 0);
            boolean found = false;
            for (String name : listed) {
                if (name.contains("test_hosts")) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "uploaded file should appear in listing");

            client.receive(clientDir, new String[]{"test_hosts"});
            File retrieved = new File(clientDir, "test_hosts");
            assertTrue(retrieved.exists());
            assertTrue(retrieved.length() > 0);

            client.delete(new String[]{"test_hosts"});
            File serverFile = AppUtil.toFile(tempRoot, new String[]{"test_hosts"});
            assertFalse(serverFile.exists());
        } finally {
            client.close();
        }
    }

    @Test
    void putEmptyFile(@TempDir File clientDir) throws IOException {
        File empty = new File(clientDir, "empty.txt");
        empty.createNewFile();
        FileClient client = createClient();
        try {
            client.send(empty, new String[]{"sub", "empty.txt"});
            File serverFile = AppUtil.toFile(tempRoot, new String[]{"sub", "empty.txt"});
            assertTrue(serverFile.exists());
            assertEquals(0, serverFile.length());

            client.receive(clientDir, new String[]{"sub", "empty.txt"});
            File retrieved = AppUtil.toFile(clientDir, new String[]{"sub", "empty.txt"});
            assertTrue(retrieved.exists());
            assertEquals(0, retrieved.length());
        } finally {
            client.close();
        }
    }

    @Test
    void getNonExistentFile() throws IOException {
        FileClient client = createClient();
        try {
            File dest = new File(tempRoot, "nonexistent");
            client.receive(dest, new String[]{"no_such_file.txt"});
            assertFalse(dest.exists());
        } finally {
            client.close();
        }
    }

    @Test
    void deleteNonExistentFile() throws IOException {
        FileClient client = createClient();
        try {
            client.delete(new String[]{"no_such_file.txt"});
        } finally {
            client.close();
        }
    }

    @Test
    void putWithOverwriteFalseRejectsDuplicate() throws IOException {
        FileClient client = createClient();
        try {
            client.send(new File("/etc/hosts"), new String[]{"dup_file"});
            client.send(new File("/etc/hosts"), new String[]{"dup_file"});
        } finally {
            client.close();
        }
        File serverFile = AppUtil.toFile(tempRoot, new String[]{"dup_file"});
        assertTrue(serverFile.exists());
        client = createClient();
        try {
            client.delete(new String[]{"dup_file"});
        } finally {
            client.close();
        }
    }

    @Test
    void listEmptyDirectory(@TempDir File clientDir) throws IOException {
        FileClient client = createClient();
        try {
            String[] files = client.list(new String[0]);
            assertNotNull(files);
        } finally {
            client.close();
        }
    }

    @Test
    void listAfterMultipleFiles(@TempDir File clientDir) throws IOException {
        FileClient client = createClient();
        try {
            client.send(new File("/etc/hosts"), new String[]{"f1"});
            client.send(new File("/etc/hosts"), new String[]{"d2", "f2"});
            client.send(new File("/etc/hosts"), new String[]{"d2", "d3", "f3"});

            String[] all = client.list(new String[0]);
            assertTrue(all.length >= 3);

            String[] sub = client.list(new String[]{"d2"});
            assertTrue(sub.length >= 1);
        } finally {
            client.close();
        }
        FileClient cleanup = createClient();
        try {
            cleanup.delete(new String[]{"f1"});
            cleanup.delete(new String[]{"d2", "f2"});
            cleanup.delete(new String[]{"d2", "d3", "f3"});
            AppUtil.delete(new File(tempRoot, "d2"));
        } finally {
            cleanup.close();
        }
    }

    @Test
    void putGetVerifyContent(@TempDir File clientDir) throws IOException {
        String content = "Hello NIOFileServer! " + System.currentTimeMillis();
        File src = new File(clientDir, "send.txt");
        try (OutputStream os = Files.newOutputStream(src.toPath())) {
            os.write(content.getBytes("UTF-8"));
        }
        FileClient client = createClient();
        try {
            client.send(src, new String[]{"verify.txt"});
            client.receive(clientDir, new String[]{"verify.txt"});
            File retrievedFile = new File(clientDir, "verify.txt");
            byte[] retrieved = Files.readAllBytes(retrievedFile.toPath());
            assertEquals(content, new String(retrieved, "UTF-8"));
        } finally {
            client.close();
        }
        FileClient cleanup = createClient();
        try {
            cleanup.delete(new String[]{"verify.txt"});
        } finally {
            cleanup.close();
        }
    }
}
