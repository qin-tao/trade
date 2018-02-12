package tao.trading.setup;

import tao.trading.QuoteData;
import tao.trading.SetupStatus;

public class BreakHHLLDto {

	public SetupStatus status;
	public String action;
	
	public double triggerPrice;
	
	
	public BreakHHLLDto(String action, double triggerPrice) {
		super();
		this.action = action;
		this.triggerPrice = triggerPrice;
	}

	
}
