package common;

import common.AppConstant.Action;
import common.AppConstant.Message;


public class Header {

	// option bit masks
	private final static int OVERWRITE_BITMASK = 0x01;

	// fields
	private Action action = Action.NONE;
	private long dataLength;
	private int option;
	private Message message = Message.NONE;
	private String[] path;

	public boolean isOverwrite() {
		return (option & OVERWRITE_BITMASK) != 0;
	}

	public void setOverwrite(boolean overwrite) {
		setOptionBit(overwrite, OVERWRITE_BITMASK);
	}

	private void setOptionBit(boolean b, int mask) {
		if (b) {
			option |= mask;
		}
		else {
			option &= ~mask;
		}
	}

	public int length() {
		return requiredLength() + optionalLength();
	}
	
	public int requiredLength() {
		int length = Integer.SIZE/Byte.SIZE; // header length field
		length += Integer.SIZE/Byte.SIZE; // action field
		length += Integer.SIZE/Byte.SIZE; // option field
		length += Integer.SIZE/Byte.SIZE; // error field
		length += Integer.SIZE/Byte.SIZE; // optional section length
		return length;
	}
	
	public int optionalLength() {
		int length = 0;
		if (path != null) { // path field
			length += Long.SIZE/Byte.SIZE; // data length
			length += Integer.SIZE/Byte.SIZE; // levels of path
			for (int i = 0; i < path.length; i++) {
				length += Integer.SIZE/Byte.SIZE; // level length
				length += path[i].length()*(Character.SIZE/Byte.SIZE); // number of chars (2bytes/char)
			}
		}
		return length;
	}

	public byte[] toBytes() {
		int requiredLength = requiredLength();
		int optionalLength = optionalLength();
		int headerLength = requiredLength + optionalLength;
		
		byte[] bytes = new byte[headerLength]; 
		int index = 0;
		
		index = AppUtil.toBytes(headerLength, bytes, index);
		index = AppUtil.toBytes(action.ordinal(), bytes, index);
		index = AppUtil.toBytes(option, bytes, index);
		index = AppUtil.toBytes(message.ordinal(), bytes, index);
		index = AppUtil.toBytes(optionalLength, bytes, index);
		
		if (optionalLength > 0) {
			index = AppUtil.toBytes(dataLength, bytes, index);
			index = AppUtil.toBytes(path.length, bytes, index);
			for (int i = 0; i < path.length; i++) {
				String str = path[i];
				index = AppUtil.toBytes(str.length(), bytes, index);
				index = AppUtil.toBytes(str, bytes, index);
			}
		}
		return bytes;
	}

	public void toHeader(byte[] bytes, int offset, int length) throws HeaderException {
		AppUtil.checkBounds(bytes, offset, length);
		
		int requiredLength = requiredLength();
		if (length < requiredLength) {
			throw new HeaderException();
		}
		
		int index = offset;
		int headerLength = AppUtil.toInteger(bytes, index);
		if (length != headerLength) {
			throw new HeaderException();
		}
		index += (Integer.SIZE/Byte.SIZE);

		action = Action.NONE;
		int actionOrdinal = AppUtil.toInteger(bytes, index);
		Action[] actions = Action.values();
		if (actionOrdinal >= 0 && actionOrdinal < actions.length) {
			action = actions[actionOrdinal];
		}
		else {
			throw new HeaderException();
		}
		index += (Integer.SIZE/Byte.SIZE);
		
		option = AppUtil.toInteger(bytes, index);
		index += (Integer.SIZE/Byte.SIZE);
		
		int messageOrdinal = AppUtil.toInteger(bytes, index);
		message = Message.NONE;
		Message[] messages = Message.values();
		if (messageOrdinal >= 0 && messageOrdinal < messages.length) {
			message = messages[messageOrdinal];
		}
		else {
			throw new HeaderException();
		}
		index += (Integer.SIZE/Byte.SIZE);
		
		int optionalLength = AppUtil.toInteger(bytes, index);
		if ( optionalLength != headerLength - requiredLength) {
			throw new HeaderException();
		}
		index += (Integer.SIZE/Byte.SIZE);
		
		if (optionalLength > 0) { // optional
			dataLength = AppUtil.toLong(bytes, index);
			index += (Long.SIZE/Byte.SIZE);
			int pathLength = AppUtil.toInteger(bytes, index);
			index += (Integer.SIZE/Byte.SIZE);
			path = new String[pathLength];
			for (int i = 0; i < pathLength; i++) {
				int strLeng = AppUtil.toInteger(bytes, index);
				index += (Integer.SIZE/Byte.SIZE);
				path[i] = AppUtil.toString(bytes, index, strLeng);
				index += strLeng*(Character.SIZE/Byte.SIZE);
			}
		}
	}
	
	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public long getDataLength() {
		return dataLength;
	}

	public void setDataLength(long dataLength) {
		this.dataLength = dataLength;
	}

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}

	public String[] getPath() {
		return path;
	}

	public void setPath(String[] path) {
		this.path = path;
	}
}
