package tao.trading.setup;

import tao.trading.QuoteData;
import tao.trading.SetupStatus;

public class DiverageWithWeakBreakOutDto {

	public SetupStatus status;
	public String action;
	
	public int triggerPos;
	public int prevPeakPos;
	public String triggerTime;
	public double triggerPrice;
	
	
	public String toString(QuoteData[] quotes){
		return "DiverageWithWeakBreakOut: [status=" + status + ", action="
				+ action + ", triggerPos=" + quotes[triggerPos].time + ", prevPeakPos="
				+ quotes[prevPeakPos].time + " " + quotes[prevPeakPos].high + " " + quotes[prevPeakPos].low + ", triggerTime=" + triggerTime + "]";
		
	}
	
	
}
