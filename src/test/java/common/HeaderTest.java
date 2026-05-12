package common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import common.AppConstant.Action;
import common.AppConstant.Message;

class HeaderTest {

    private Header roundTrip(Header h) throws HeaderException {
        byte[] bytes = h.toBytes();
        Header result = new Header();
        result.toHeader(bytes, 0, bytes.length);
        return result;
    }

    @Test
    void getAndSetActions() {
        for (Action action : Action.values()) {
            Header h = new Header();
            h.setAction(action);
            assertEquals(action, h.getAction());
        }
    }

    @Test
    void getAndSetMessages() {
        for (Message msg : Message.values()) {
            Header h = new Header();
            h.setMessage(msg);
            assertEquals(msg, h.getMessage());
        }
    }

    @Test
    void roundTripGetActionNoPath() throws HeaderException {
        Header h = new Header();
        h.setAction(Action.GET);
        h.setDataLength(0);
        Header result = roundTrip(h);
        assertEquals(Action.GET, result.getAction());
        assertEquals(0, result.getDataLength());
        assertNull(result.getPath());
    }

    @Test
    void roundTripPutActionWithPath() throws HeaderException {
        Header h = new Header();
        h.setAction(Action.PUT);
        h.setDataLength(1024);
        h.setPath(new String[]{"dir1", "dir2", "file.txt"});
        h.setOverwrite(true);
        Header result = roundTrip(h);
        assertEquals(Action.PUT, result.getAction());
        assertEquals(1024, result.getDataLength());
        assertArrayEquals(new String[]{"dir1", "dir2", "file.txt"}, result.getPath());
        assertTrue(result.isOverwrite());
    }

    @Test
    void roundTripPutActionEmptyPath() throws HeaderException {
        Header h = new Header();
        h.setAction(Action.LST);
        h.setPath(new String[0]);
        h.setMessage(Message.FILE_EXIST);
        Header result = roundTrip(h);
        assertEquals(Action.LST, result.getAction());
        assertArrayEquals(new String[0], result.getPath());
        assertEquals(Message.FILE_EXIST, result.getMessage());
    }

    @Test
    void roundTripSinglePathSegment() throws HeaderException {
        Header h = new Header();
        h.setAction(Action.DEL);
        h.setPath(new String[]{"file.txt"});
        h.setMessage(Message.FILE_DELETED);
        Header result = roundTrip(h);
        assertEquals(Action.DEL, result.getAction());
        assertArrayEquals(new String[]{"file.txt"}, result.getPath());
        assertEquals(Message.FILE_DELETED, result.getMessage());
    }

    @Test
    void overwriteFlagRoundTrip() throws HeaderException {
        Header h = new Header();
        h.setAction(Action.PUT);
        h.setPath(new String[]{"f"});
        h.setOverwrite(true);
        assertTrue(roundTrip(h).isOverwrite());

        h.setOverwrite(false);
        assertFalse(roundTrip(h).isOverwrite());
    }

    @Test
    void zeroDataLength() throws HeaderException {
        Header h = new Header();
        h.setAction(Action.PUT);
        h.setPath(new String[]{"empty.txt"});
        h.setDataLength(0);
        Header result = roundTrip(h);
        assertEquals(0, result.getDataLength());
    }

    @Test
    void largePath() throws HeaderException {
        String[] path = new String[50];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            path[i] = "level" + i;
            sb.append("/level").append(i);
        }
        Header h = new Header();
        h.setAction(Action.GET);
        h.setPath(path);
        h.setDataLength(12345);
        Header result = roundTrip(h);
        assertEquals(12345, result.getDataLength());
        assertArrayEquals(path, result.getPath());
    }

    @Test
    void headerLengthMethodConsistency() {
        Header h = new Header();
        h.setAction(Action.PUT);
        h.setPath(new String[]{"a", "b"});
        h.setDataLength(100);
        byte[] bytes = h.toBytes();
        assertEquals(bytes.length, h.length());
        assertEquals(bytes.length, h.requiredLength() + h.optionalLength());
    }

    @Test
    void headerLengthNoPath() {
        Header h = new Header();
        h.setAction(Action.NONE);
        assertEquals(h.requiredLength(), h.length());
        assertEquals(0, h.optionalLength());
    }

    @Test
    void toHeaderRejectsTruncatedData() {
        Header h = new Header();
        h.setAction(Action.GET);
        h.setPath(new String[]{"a"});
        byte[] bytes = h.toBytes();
        assertThrows(HeaderException.class, () -> {
            new Header().toHeader(bytes, 0, bytes.length - 1);
        });
    }

    @Test
    void toHeaderRejectsMismatchedLength() {
        byte[] bytes = new byte[100];
        AppUtil.toBytes(42, bytes, 0); // wrong header length
        assertThrows(HeaderException.class, () -> {
            new Header().toHeader(bytes, 0, 100);
        });
    }

    @Test
    void toHeaderRejectsInvalidActionOrdinal() {
        byte[] bytes = new byte[20];
        int index = 0;
        index = AppUtil.toBytes(20, bytes, index); // header length
        index = AppUtil.toBytes(999, bytes, index); // invalid action ordinal
        index = AppUtil.toBytes(0, bytes, index);   // option
        index = AppUtil.toBytes(0, bytes, index);   // message
        index = AppUtil.toBytes(0, bytes, index);   // optional length
        assertThrows(HeaderException.class, () -> {
            new Header().toHeader(bytes, 0, 20);
        });
    }

    @Test
    void toHeaderRejectsInvalidMessageOrdinal() {
        byte[] bytes = new byte[20];
        int index = 0;
        index = AppUtil.toBytes(20, bytes, index);
        index = AppUtil.toBytes(0, bytes, index);   // NONE action
        index = AppUtil.toBytes(0, bytes, index);   // option
        index = AppUtil.toBytes(999, bytes, index); // invalid message ordinal
        index = AppUtil.toBytes(0, bytes, index);   // optional length
        assertThrows(HeaderException.class, () -> {
            new Header().toHeader(bytes, 0, 20);
        });
    }
}
