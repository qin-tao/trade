package com.ib.client;

public class TradeStop {
	int id;
	String tradeType;
	double price;
	double positionSize;
	

	public TradeStop(int id, String tradeType, double price, double positionSize) {
		super();
		this.id = id;
		this.tradeType = tradeType;
		this.price = price;
		this.positionSize = positionSize;
	}
	
	
	

}
