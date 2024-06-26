package cn.jiguang.common;

public enum DeviceType {
    
	Android("android"),
	IOS("ios"),
	HMOS("hmos"),
	QuickApp("quickapp");
	
	private final String value;
	
	private DeviceType(final String value) {
		this.value = value;
	}
	
	public String value() {
		return this.value;
	}
	
}
