package uk.ac.imperial.lsds.seep.api;


public enum DataStoreType {
	
	NETWORK((short)0),
	FILE((short)1),
	IPC((short)2), // ??
	IN_MEMORY((short)3),
	KAFKA((short)4),
	HDFS((short)5),
	MEMORYMAPPED_BYTEBUFFER((short)6);
	
	private short type;
	
	DataStoreType(short type) {
		this.type = type;
	}
	
	public short ofType() {
		return type;
	}
	
}
