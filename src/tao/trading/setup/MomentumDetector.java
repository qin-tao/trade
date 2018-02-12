package tao.trading.setup;

import java.util.HashMap;
import java.util.Map;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.MomentumDAO;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;

public class MomentumDetector {

	private final static Map<String, Integer> MomentumSize = new HashMap<String, Integer>();
	static{
		MomentumSize.put("1"+Constants.CHART_240_MIN, 30);
		MomentumSize.put("2"+Constants.CHART_240_MIN, 50);
		MomentumSize.put("3"+Constants.CHART_240_MIN, 60);
		MomentumSize.put("4"+Constants.CHART_240_MIN, 70);
		MomentumSize.put("1"+Constants.CHART_DAILY, 100);
		MomentumSize.put("2"+Constants.CHART_DAILY, 200);
		MomentumSize.put("3"+Constants.CHART_DAILY, 300);
		MomentumSize.put("4"+Constants.CHART_DAILY, 300);
	}	

	
    static public MomentumDAO detect_momentum(QuoteData[] quotes, int mEnd)
	{
		int lastbar = quotes.length - 1;
		//double avgSize = BarUtil.averageBarSizeOpenClose(quotes);

		if ( direction == Constants.DIRECTION_DOWN ){
			ConsectiveBars consecDown = Utility.findLastConsectiveDownBars(quotes, 0, mEnd);
			if ( consecDown != null ){
				
				double consecDownSize = quotes[consecDown.getBegin()].open - quotes[consecDown.getEnd()].close;
						
				if ((( consecDown.getSize() == 1 ) && ( consecDownSize > MomentumSize.get("1"+chart) * PIP_SIZE)  //2,3,4
				  ||(( consecDown.getSize() == 2 ) && ( consecDownSize > MomentumSize.get("2"+chart) * PIP_SIZE )
				  ||(( consecDown.getSize() == 3 ) && ( consecDownSize > MomentumSize.get("3"+chart) * PIP_SIZE )
				  ||(( consecDown.getSize() == 4 ) && ( consecDownSize > MomentumSize.get("4"+chart) * PIP_SIZE )
					))))){
	
					QuoteData lowest = Utility.getLow(quotes, consecDown.getEnd()+1,lastbar-1);
					if ( quotes[lastbar].low <= lowest.low )
						return null; // not finished
					
					MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_DOWN);
					momentum.startPos = consecDown.getBegin();
					momentum.endPos = consecDown.getEnd();
					momentum.startTime = quotes[consecDown.getBegin()].time;
					momentum.endTime = quotes[consecDown.getEnd()].time;
					momentum.size = consecDownSize;
					return momentum;
				}
			}
		}
		else if ( direction == Constants.DIRECTION_UP ){
			ConsectiveBars consecUp = Utility.findLastConsectiveUpBars(quotes, 0, mEnd);
			if ( consecUp != null )
			{
				double consecUpSize = quotes[consecUp.getEnd()].close - quotes[consecUp.getBegin()].open;
				
				if ((( consecUp.getSize() == 1 ) && ( consecUpSize > MomentumSize.get("1"+chart) * PIP_SIZE)  //2,3,4
				  ||(( consecUp.getSize() == 2 ) && ( consecUpSize > MomentumSize.get("2"+chart) * PIP_SIZE)
				  ||(( consecUp.getSize() == 3 ) && ( consecUpSize > MomentumSize.get("3"+chart) * PIP_SIZE)
				  ||(( consecUp.getSize() == 4 ) && ( consecUpSize > MomentumSize.get("4"+chart) * PIP_SIZE)
				   ))))){
	
						QuoteData highest = Utility.getHigh(quotes, consecUp.getEnd()+1,lastbar-1);
						if ( quotes[lastbar].high >= highest.high )
							return null; // not finished
						
						MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_UP);
						momentum.startPos = consecUp.getBegin();
						momentum.endPos = consecUp.getEnd();
						momentum.startTime = quotes[consecUp.getBegin()].time;
						momentum.endTime = quotes[consecUp.getEnd()].time;
						momentum.size = consecUpSize;
						return momentum;
				}
			}
		}
		
		return null;
	}

	
	
    static public MomentumDAO straight_momentum(QuoteData data, QuoteData[] quotes, int chart, int direction, int mEnd, double PIP_SIZE)
	{
		int lastbar = quotes.length - 1;
		//double avgSize = BarUtil.averageBarSizeOpenClose(quotes);

		if ( direction == Constants.DIRECTION_DOWN ){
			ConsectiveBars consecDown = Utility.findLastConsectiveDownBars(quotes, 0, mEnd);
			if ( consecDown != null ){
				
				double consecDownSize = quotes[consecDown.getBegin()].open - quotes[consecDown.getEnd()].close;
						
				if ((( consecDown.getSize() == 1 ) && ( consecDownSize > MomentumSize.get("1"+chart) * PIP_SIZE)  //2,3,4
				  ||(( consecDown.getSize() == 2 ) && ( consecDownSize > MomentumSize.get("2"+chart) * PIP_SIZE )
				  ||(( consecDown.getSize() == 3 ) && ( consecDownSize > MomentumSize.get("3"+chart) * PIP_SIZE )
				  ||(( consecDown.getSize() == 4 ) && ( consecDownSize > MomentumSize.get("4"+chart) * PIP_SIZE )
					))))){
	
					QuoteData lowest = Utility.getLow(quotes, consecDown.getEnd()+1,lastbar-1);
					if ( quotes[lastbar].low <= lowest.low )
						return null; // not finished
					
					MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_DOWN);
					momentum.startPos = consecDown.getBegin();
					momentum.endPos = consecDown.getEnd();
					momentum.startTime = quotes[consecDown.getBegin()].time;
					momentum.endTime = quotes[consecDown.getEnd()].time;
					momentum.size = consecDownSize;
					return momentum;
				}
			}
		}
		else if ( direction == Constants.DIRECTION_UP ){
			ConsectiveBars consecUp = Utility.findLastConsectiveUpBars(quotes, 0, mEnd);
			if ( consecUp != null )
			{
				double consecUpSize = quotes[consecUp.getEnd()].close - quotes[consecUp.getBegin()].open;
				
				if ((( consecUp.getSize() == 1 ) && ( consecUpSize > MomentumSize.get("1"+chart) * PIP_SIZE)  //2,3,4
				  ||(( consecUp.getSize() == 2 ) && ( consecUpSize > MomentumSize.get("2"+chart) * PIP_SIZE)
				  ||(( consecUp.getSize() == 3 ) && ( consecUpSize > MomentumSize.get("3"+chart) * PIP_SIZE)
				  ||(( consecUp.getSize() == 4 ) && ( consecUpSize > MomentumSize.get("4"+chart) * PIP_SIZE)
				   ))))){
	
						QuoteData highest = Utility.getHigh(quotes, consecUp.getEnd()+1,lastbar-1);
						if ( quotes[lastbar].high >= highest.high )
							return null; // not finished
						
						MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_UP);
						momentum.startPos = consecUp.getBegin();
						momentum.endPos = consecUp.getEnd();
						momentum.startTime = quotes[consecUp.getBegin()].time;
						momentum.endTime = quotes[consecUp.getEnd()].time;
						momentum.size = consecUpSize;
						return momentum;
				}
			}
		}
		
		return null;
	}


    
    static public MomentumDAO detect_strong_momentum(QuoteData[] quotes, int direction, int mEnd)
	{
		if ( direction == Constants.DIRECTION_DOWN ){
			ConsectiveBars consecDown = Utility.findLastConsectiveDownBars(quotes, 0, mEnd);
			if ( consecDown != null ){
				
				double consecDownSize = quotes[consecDown.getBegin()].open - quotes[consecDown.getEnd()].close;
				double consecDownNum = consecDown.getSize();
				double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
						
				if ((( consecDownNum == 1 ) && ( consecDownSize > 2.5 * avgBarSize )  
				  ||(( consecDownNum == 2 ) && ( consecDownSize > 3 * avgBarSize )
				  ||(( consecDownNum == 3 ) && ( consecDownSize > 3 * avgBarSize )
				  ||(( consecDownNum > 3 ) && ( consecDownSize > consecDownNum * 0.7 * avgBarSize )
					))))){
	
					MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_DOWN);
					momentum.startPos = consecDown.getBegin();
					momentum.endPos = consecDown.getEnd();
					momentum.startTime = quotes[consecDown.getBegin()].time;
					momentum.endTime = quotes[consecDown.getEnd()].time;
					momentum.size = consecDownSize;
					return momentum;
				}
			}
		}
		else if ( direction == Constants.DIRECTION_UP ){
			ConsectiveBars consecUp = Utility.findLastConsectiveUpBars(quotes, 0, mEnd);
			if ( consecUp != null )
			{
				double consecUpSize = quotes[consecUp.getEnd()].close - quotes[consecUp.getBegin()].open;
				double consecUpNum = consecUp.getSize();
				double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
				
				if ((( consecUpNum == 1 ) && ( consecUpSize > 2.5 * avgBarSize )  
				  ||(( consecUpNum == 2 ) && ( consecUpSize > 3 * avgBarSize )
				  ||(( consecUpNum == 3 ) && ( consecUpSize > 3 * avgBarSize )
				  ||(( consecUpNum > 3 ) && ( consecUpSize > consecUpNum * 0.7 * avgBarSize )
					))))){
	
						
					MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_UP);
					momentum.startPos = consecUp.getBegin();
					momentum.endPos = consecUp.getEnd();
					momentum.startTime = quotes[consecUp.getBegin()].time;
					momentum.endTime = quotes[consecUp.getEnd()].time;
					momentum.size = consecUpSize;
					return momentum;
				}
			}
		}
		
		return null;
	}


    static public MomentumDAO detect_large_momentum(QuoteData[] quotes, int direction, int mEnd)
	{
		if ( direction == Constants.DIRECTION_DOWN ){
			ConsectiveBars consecDown = Utility.findLastConsectiveDownBars(quotes, 0, mEnd);
			if ( consecDown != null ){
				
				double consecDownSize = quotes[consecDown.getBegin()].open - quotes[consecDown.getEnd()].close;
				double consecDownNum = consecDown.getSize();
				double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
						
				if ((( consecDownNum == 1 ) && ( consecDownSize > 1.6 * avgBarSize )) ||
					(( consecDownNum ==2 ) && ( consecDownSize > 2 * avgBarSize )) ||
					( consecDownNum >=3 )) {
	
					MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_DOWN);
					momentum.startPos = consecDown.getBegin();
					momentum.endPos = consecDown.getEnd();
					momentum.startTime = quotes[consecDown.getBegin()].time;
					momentum.endTime = quotes[consecDown.getEnd()].time;
					momentum.size = consecDownSize;
					return momentum;
				}
			}
		}
		else if ( direction == Constants.DIRECTION_UP ){
			ConsectiveBars consecUp = Utility.findLastConsectiveUpBars(quotes, 0, mEnd);
			if ( consecUp != null )
			{
				double consecUpSize = quotes[consecUp.getEnd()].close - quotes[consecUp.getBegin()].open;
				double consecUpNum = consecUp.getSize();
				double avgBarSize = BarUtil.averageBarSizeOpenClose(quotes);
				
				if ((( consecUpNum == 1 ) && ( consecUpSize > 1.6 * avgBarSize )) ||
					(( consecUpNum ==2 ) && ( consecUpSize > 2 * avgBarSize )) ||
					( consecUpNum >=3 )) {
						
					MomentumDAO momentum = new MomentumDAO(Constants.DIRECTION_UP);
					momentum.startPos = consecUp.getBegin();
					momentum.endPos = consecUp.getEnd();
					momentum.startTime = quotes[consecUp.getBegin()].time;
					momentum.endTime = quotes[consecUp.getEnd()].time;
					momentum.size = consecUpSize;
					return momentum;
				}
			}
		}
		
		return null;
	}

    
    
    static public MomentumDAO detect_momentum_change(QuoteData[] quotes, int direction, int start)
	{
    	int lastbar = quotes.length - 1;
    	
    	if ( direction == Constants.DIRECTION_UP){

			int lastUpBarEnd = lastbar;
			while (!BarUtil.isUpBar(quotes[lastUpBarEnd]))
				lastUpBarEnd--;
			
			int lastUpBarBegin = lastUpBarEnd;
			while (BarUtil.isUpBar(quotes[lastUpBarBegin-1]))
				lastUpBarBegin--;

			if ( lastUpBarBegin < start )
				return null;
			
			if ( quotes[lastbar].low < quotes[lastUpBarBegin].low ){
				MomentumDAO md = new MomentumDAO();
				md.lastUpBarBegin = lastUpBarBegin;
				md.triggerPrice = quotes[lastUpBarBegin].low; //quotes[lastbar-1].low;
				md.momentumChangePoint = lastbar;
				return md;
			}
		}
    	else if ( direction == Constants.DIRECTION_DOWN){

			int lastDownBarEnd = lastbar;
			while (!BarUtil.isDownBar(quotes[lastDownBarEnd]))
				lastDownBarEnd--;
			
			int lastDownBarBegin = lastDownBarEnd;
			while (BarUtil.isDownBar(quotes[lastDownBarBegin-1]))
				lastDownBarBegin--;

			if ( lastDownBarBegin < start )
				return null;
			
			if ( quotes[lastbar].high > quotes[lastDownBarBegin].high ){
				MomentumDAO md = new MomentumDAO();
				md.lastDownBarBegin = lastDownBarBegin;
				md.triggerPrice = quotes[lastDownBarBegin].high; 
				md.momentumChangePoint = lastbar-1;
				return md;
			}
		}

		return null;
	}

    static public MomentumDAO detect_momentum_change_by_close(QuoteData[] quotes, int direction, int start, int lastbar)
	{
    	double avgBarSize = BarUtil.averageBarSize(quotes);
    	
    	if ( direction == Constants.DIRECTION_UP){

    		for ( int z = lastbar-1; z > start; z--){
				int lastUpBarEnd = z;
				while (!BarUtil.isUpBar(quotes[lastUpBarEnd]))
					lastUpBarEnd--;
				
				int lastUpBarBegin = lastUpBarEnd;
				while (BarUtil.isUpBar(quotes[lastUpBarBegin-1]))
					lastUpBarBegin--;
	
				if ( lastUpBarBegin < start )
					return null;
				
				if ((( quotes[lastUpBarEnd].close - quotes[lastUpBarBegin].open ) > avgBarSize ) && ( quotes[lastbar].low < quotes[lastUpBarBegin].open )){
				//if ( quotes[lastbar].close < quotes[lastUpBarBegin].low ){
					MomentumDAO md = new MomentumDAO();
					md.lastUpBarBegin = lastUpBarBegin;
					md.triggerPrice = quotes[lastUpBarBegin].open; 
					md.momentumChangePoint = lastbar;
					return md;
				}
    		}
		}
    	else if ( direction == Constants.DIRECTION_DOWN){

    		for ( int z = lastbar-1; z > start; z--){
				int lastDownBarEnd = z;
				while (!BarUtil.isDownBar(quotes[lastDownBarEnd]))
					lastDownBarEnd--;
				
				int lastDownBarBegin = lastDownBarEnd;
				while (BarUtil.isDownBar(quotes[lastDownBarBegin-1]))
					lastDownBarBegin--;
	
				if ( lastDownBarBegin < start )
					return null;
				
				if ((( quotes[lastDownBarBegin].open - quotes[lastDownBarEnd].close ) > avgBarSize ) && ( quotes[lastbar].high > quotes[lastDownBarBegin].open )){
				//if ( quotes[lastbar].close > quotes[lastDownBarBegin].high ){
					MomentumDAO md = new MomentumDAO();
					md.lastDownBarBegin = lastDownBarBegin;
					md.triggerPrice = quotes[lastDownBarBegin].open;
					md.momentumChangePoint = lastbar;
					return md;
				}
    		}
		}

		return null;
	}
 

    static public MomentumDAO detect_momentum_change_by_close_or_large_drop(QuoteData[] quotes, int direction, int start, int lastbar)
	{
    	if ( direction == Constants.DIRECTION_UP){

			int lastUpBarEnd = lastbar;
			while (!BarUtil.isUpBar(quotes[lastUpBarEnd]))
				lastUpBarEnd--;
			
			int lastUpBarBegin = lastUpBarEnd;
			while (BarUtil.isUpBar(quotes[lastUpBarBegin-1]))
				lastUpBarBegin--;

			if ( lastUpBarBegin < start )
				return null;
			
			if ( quotes[lastbar-1].close < quotes[lastUpBarBegin].low ){
				MomentumDAO md = new MomentumDAO();
				md.lastUpBarBegin = lastUpBarBegin;
				md.triggerPrice = quotes[lastbar-1].close; 
				md.momentumChangePoint = lastbar-1;
				return md;
			}

			if ( quotes[lastbar-1].close < quotes[lastUpBarBegin].low ){
				MomentumDAO md = new MomentumDAO();
				md.lastUpBarBegin = lastUpBarBegin;
				md.triggerPrice = quotes[lastbar-1].close; 
				md.momentumChangePoint = lastbar-1;
				return md;
			}
		}
    	else if ( direction == Constants.DIRECTION_DOWN){

			int lastDownBarEnd = lastbar;
			while (!BarUtil.isDownBar(quotes[lastDownBarEnd]))
				lastDownBarEnd--;
			
			int lastDownBarBegin = lastDownBarEnd;
			while (BarUtil.isDownBar(quotes[lastDownBarBegin-1]))
				lastDownBarBegin--;

			if ( lastDownBarBegin < start )
				return null;
			
			if ( quotes[lastbar-1].close > quotes[lastDownBarBegin].high ){
				MomentumDAO md = new MomentumDAO();
				md.lastDownBarBegin = lastDownBarBegin;
				md.triggerPrice = quotes[lastbar-1].close;
				md.momentumChangePoint = lastbar-1;
				return md;
			}
		}

		return null;
	}

    
    
}
