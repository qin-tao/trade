package com.ib.client;

public class TradeRecord {
	
	public String tradeType;
	public String time;
	public String action;
	public double price;
	public int size;
	
	public TradeRecord(String time, String action, double price, int size) {
		super();
		this.time = time;
		this.action = action;
		this.price = price;
		this.size = size;
	}
	
	

}
