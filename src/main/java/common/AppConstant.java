package common;

public class AppConstant {

	public static enum Action {
		NONE,
		PUT, 
		PUT_ACK, 
		PUT_FIN,
		GET, 
		GET_ACK, 
		DEL, 
		DEL_ACK,
		LST,
		LST_ACK
	}
	
	public static enum State {
		IDLE, 
		RECV, 
		SEND
	}
	
	public static enum Message {
		NONE,
		FILE_EXIST, 
		FILE_NOT_EXIST,
		FILE_CREATED, 
		FILE_NOT_CREATED,
		FILE_DELETED, 
		FILE_NOT_DELETED,
		PERMISSION_DENIED
	}
	
}
