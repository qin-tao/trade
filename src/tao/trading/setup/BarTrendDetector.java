package tao.trading.setup;

import java.util.ArrayList;
import java.util.Iterator;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.MABreakOutList;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;

public class BarTrendDetector {

	public QuoteData[] original_quotes; 		
	public ArrayList<BarTrendBarDTO> bars; 		
	public ArrayList<BarTrendTrendDTO> trend_sorters;
	double averageBarSize,avgBarSizeOpenClose;

	
	public BarTrendDetector (QuoteData[] quotes, double PIP_SIZE ){
		int lastbar = quotes.length - 1;
		original_quotes = quotes;
		bars = new ArrayList<BarTrendBarDTO>();
		averageBarSize = BarUtil.averageBarSize(original_quotes);
		avgBarSizeOpenClose = BarUtil.averageBarSizeOpenClose(original_quotes);
				
		// consolidate the up/down bars
		BarTrendBarDTO currElement = null;
		if (BarUtil.isUpBar(original_quotes[0]))
			currElement = new BarTrendBarDTO(Constants.DIRECTION_UP);
		else 
			currElement = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
		currElement.begin = currElement.end = 0;
		bars.add(currElement);

		//double avgBarSize = BarUtil.averageBarSizeOpenClose(original_quotes);

		for ( int i = 1; i <= lastbar; i++){
			if (BarUtil.isUpBar(quotes[i])/* && ( BarUtil.barSize(quotes[i]) > 5 * PIP_SIZE )*/){
				if (currElement.direction == Constants.DIRECTION_DOWN ){
					currElement = new BarTrendBarDTO(Constants.DIRECTION_UP);
					currElement.begin = currElement.end = i;
					bars.add(currElement);
					continue;
				}
			}
			else if (BarUtil.isDownBar(quotes[i])/* && ( BarUtil.barSize(quotes[i]) > 5 * PIP_SIZE*//* avgBarSize/6*/ ){
				if (( currElement.direction == Constants.DIRECTION_UP )){
					currElement = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
					currElement.begin = currElement.end = i;
					bars.add(currElement);
					continue;
				}
			}

			currElement.end = i;  // open = close no direction
		}
		
		sortTrendChange();
	}
	
	
	public void sortTrendChange(){
		trend_sorters = new ArrayList<BarTrendTrendDTO>(); ;
		BarTrendTrendDTO current = new BarTrendTrendDTO(bars.get(0).direction);
		current.begin = current.end = 0;
		trend_sorters.add(current);
		
		for ( int i = 1; i < bars.size(); i++){
			if ( bars.get(i).direction == Constants.DIRECTION_UP ){
				if ( current.direction == Constants.DIRECTION_DOWN ){
					//if ( getHigh(trend_detectors.get(i)) > original_quotes[trend_detectors.get(i-1).begin].open){
					//if ( getHigh(bars.get(i)) > original_quotes[bars.get(i-1).begin].high){
					if ( original_quotes[bars.get(i).end].close > original_quotes[bars.get(i-1).begin].open){
						current = new BarTrendTrendDTO(Constants.DIRECTION_UP );
						current.begin = current.end = i;
						current.triggerPrice = original_quotes[bars.get(i-1).begin].open;
						//no need for trigger pos now
						/*for ( int j = bars.get(i).begin; j<=bars.get(i).end;j++){
							//if (original_quotes[j].high > original_quotes[trend_detectors.get(i-1).begin].open){
							if (original_quotes[j].high > original_quotes[bars.get(i-1).begin].high){
								current.triggerPos = j;
								break;
							}
						}*/
						trend_sorters.add(current);
						continue;
					}
				}
			}
			else if ( bars.get(i).direction == Constants.DIRECTION_DOWN ){
				if ( current.direction == Constants.DIRECTION_UP ){
					//if ( getLow(trend_detectors.get(i)) < original_quotes[trend_detectors.get(i-1).begin].open){
					//if ( getLow(bars.get(i)) < original_quotes[bars.get(i-1).begin].low){
					if ( original_quotes[bars.get(i).end].close < original_quotes[bars.get(i-1).begin].open){
						current = new BarTrendTrendDTO(Constants.DIRECTION_DOWN );
						current.begin = current.end = i;
						current.triggerPrice = original_quotes[bars.get(i-1).begin].open;
						//no need for trigger pos now
						/*for ( int j = bars.get(i).begin; j<=bars.get(i).begin;j++){
							//if (original_quotes[j].low < original_quotes[trend_detectors.get(i-1).end].open){
							if (original_quotes[j].low < original_quotes[bars.get(i-1).end].low){
								current.triggerPos = j;
								break;
							}
						}*/
						trend_sorters.add(current);
						continue;
					}
				}
			}
			// default, the current continues
			current.end = i;
		}
	}

	
	
	

	public void printTrend(QuoteData[] quotes){
		   Iterator<BarTrendBarDTO> it = bars.iterator();
		    while(it.hasNext()) {
		      BarTrendBarDTO obj = it.next();
		      if ( obj.newTrend ){
			      System.out.println(obj.toString(quotes));
		    	  //System.out.println(obj.trend + " " + obj.time);
		      }
		    }
	
	}
	
	
	public BarTrendBarDTO getLastTrend(){
		return bars.get(trend_sorters.size()-1);
	}
	
	// obsolete
	public BarTrendBarDTO detectTrend(QuoteData lastBar){
		int lastbar = bars.size()-1;
		BarTrendBarDTO preElement = bars.get(lastbar-1);
		BarTrendBarDTO curElement = bars.get(lastbar);
		if (preElement.direction == Constants.DIRECTION_DOWN){ 
			if (lastBar.high > original_quotes[preElement.begin].open ){
				BarTrendBarDTO t = new BarTrendBarDTO(Constants.DIRECTION_UP);
				t.newTrend = true;
				t.triggerPrice = original_quotes[preElement.begin].open;
				t.begin = preElement.begin;
				t.end = preElement.end;
				t.trendBar = original_quotes[preElement.end];
				return t;
			}
			else{
				BarTrendBarDTO t = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
				return t;
			}
		}	
		if (preElement.direction == Constants.DIRECTION_UP){
			if (lastBar.low < original_quotes[preElement.begin].open ){
				BarTrendBarDTO t = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
				t.newTrend = true;
				t.triggerPrice = original_quotes[preElement.begin].open;
				t.begin = preElement.begin;
				t.end = preElement.end;
				t.trendBar = original_quotes[preElement.end];
				return t;
			}
			else{
				BarTrendBarDTO t = new BarTrendBarDTO(Constants.DIRECTION_UP);
				return t;
			}
		}
		return null;
	}
	
	
	
	// obsolete
	public BarTrendBarDTO detectTrend2(){
		int lastElement = bars.size()-1;
		BarTrendBarDTO preElement = bars.get(lastElement-1);
		BarTrendBarDTO curElement = bars.get(lastElement);
		int lastbar = original_quotes.length -1;
				
		boolean missed = false;
		if (preElement.direction == Constants.DIRECTION_DOWN){ 
			if (original_quotes[lastbar].high > original_quotes[preElement.begin].open ){
				
				for ( int i = preElement.begin + 1; i < lastbar; i++ )
					if ( original_quotes[i].high > original_quotes[preElement.begin].open )
						missed = true;
					
				if (!missed){
					BarTrendBarDTO t = new BarTrendBarDTO(Constants.DIRECTION_UP);
					t.newTrend = true;
					t.triggerPrice = original_quotes[preElement.begin].open;
					t.begin = preElement.begin;
					t.end = preElement.end;
					t.trendBar = original_quotes[preElement.end];
					return t;
				}
			}
		}	
		if (preElement.direction == Constants.DIRECTION_UP){
			if (original_quotes[lastbar].low < original_quotes[preElement.begin].open ){

				for ( int i = preElement.begin + 1; i < lastbar; i++ )
					if ( original_quotes[i].low < original_quotes[preElement.begin].open )
						missed = true;

				if (!missed){
					BarTrendBarDTO t = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
					t.newTrend = true;
					t.triggerPrice = original_quotes[preElement.begin].open;
					t.begin = preElement.begin;
					t.end = preElement.end;
					t.trendBar = original_quotes[preElement.end];
					return t;
				}
			}
		}
		return null;
	}

	
	public BarTrendBarDTO detectTrendChange3(){
		
		int lastbar = original_quotes.length - 1;
		BarTrendTrendDTO lastTrend = trend_sorters.get(trend_sorters.size() - 1);

		if (lastTrend.triggerPos == lastbar ){
		//if (lastTrend.begin == lastTrend.end ){
			BarTrendBarDTO t = new BarTrendBarDTO(lastTrend.direction);
			t.triggerPrice = lastTrend.triggerPrice;
			return t;
		}
		
		return null;
	}
	

	
	//this one
	public int detectTrend(){
		int lastElement = bars.size()-1;
		BarTrendBarDTO preElement = bars.get(lastElement-1);
		BarTrendBarDTO curElement = bars.get(lastElement);

		
		double avgOpenCloseBarSize = BarUtil.averageBarSizeOpenClose(original_quotes);
		if ( Math.abs(original_quotes[curElement.begin].open - original_quotes[curElement.end].close ) > 2.5 * avgOpenCloseBarSize){
			if ( curElement.direction == Constants.DIRECTION_UP )
				return Constants.TREND_UP;
			else if ( curElement.direction == Constants.DIRECTION_DOWN )
				return Constants.TREND_DOWN;
		}
		
		if ( Math.abs(original_quotes[preElement.begin].open - original_quotes[preElement.end].close ) > 2.5 * avgOpenCloseBarSize){
			if ( preElement.direction == Constants.DIRECTION_UP ){
				double point618 = original_quotes[preElement.end].high - 0.618*(original_quotes[preElement.end].high - original_quotes[preElement.begin].low);
				//if ( original_quotes[curElement.end].low > original_quotes[preElement.begin].open )
				if ( original_quotes[curElement.end].low > point618 )
					return Constants.TREND_UP;
			}	
			else if ( preElement.direction == Constants.DIRECTION_DOWN ){
				double point618 = original_quotes[preElement.end].low + 0.618*(original_quotes[preElement.begin].high - original_quotes[preElement.end].low);
				//if ( original_quotes[curElement.end].high < original_quotes[preElement.begin].open )
				if ( original_quotes[curElement.end].high < point618 )
					return Constants.TREND_DOWN;
			}	
		}
		
		return Constants.TREND_UNKNOWN;
		
		/*
		if (preElement.direction == Constants.DIRECTION_DOWN){ 
			if (getHigh(curElement) < original_quotes[preElement.begin].open ){
				if (getLow(preElement) < original_quotes[prepreElement.begin].open){
					BarTrendBarDTO d = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
					d.begin = preElement.begin;
					return d;
				}else{
					BarTrendBarDTO d = new BarTrendBarDTO(Constants.DIRECTION_UP);
					d.begin = prepreElement.begin;
					return d;
				}
			}else{
				BarTrendBarDTO d = new BarTrendBarDTO(Constants.DIRECTION_UP);
				d.begin = curElement.begin;
				return d;
			}
		}	
		if (preElement.direction == Constants.DIRECTION_UP){
			if (getLow(curElement) > original_quotes[preElement.begin].open ){
				if (getHigh(preElement) > original_quotes[prepreElement.begin].open){
					BarTrendBarDTO d = new BarTrendBarDTO(Constants.DIRECTION_UP);
					d.begin = preElement.begin;
					return d;
				}else{
					BarTrendBarDTO d = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
					d.begin = prepreElement.begin;
					return d;
				}
			}else{
				BarTrendBarDTO d = new BarTrendBarDTO(Constants.DIRECTION_DOWN);
				d.begin = curElement.begin;
				return d;
			}
		}
		return null;*/
		
	}

	public BarTrendTrendDTO getTrendDirection2(){
		return trend_sorters.get(trend_sorters.size()-1);
	}	
	
	
	
	
	public 	BarTrendBarDTO detectBreakOut(){
		int lastbar = original_quotes.length-1;
		int breakOutBar = 0;
		boolean found = false;
		
		/*
		breakOutBar = lastbar;
		double avgLast4HighLow = BarUtil.averageBarSize(original_quotes, breakOutBar-4, breakOutBar-1);
		if (BarUtil.isUpBar(original_quotes[breakOutBar]) && (original_quotes[breakOutBar].close - Utility.getHigh(original_quotes, breakOutBar-4, breakOutBar-1).high > avgLast4HighLow)){
			double avg4Size = BarUtil.averageBarSizeOpenClose(original_quotes, breakOutBar-4, breakOutBar-1);
			double breakOutBarSize = BarUtil.barSize(original_quotes[breakOutBar]);
			if (( breakOutBarSize > 3 * avg4Size )) 
				found = true;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && ( Utility.getLow(original_quotes, breakOutBar-4, breakOutBar-1).low - original_quotes[breakOutBar].close > avgLast4HighLow)){
			double avg4Size = BarUtil.averageBarSizeOpenClose(original_quotes, breakOutBar-4, breakOutBar-1);
			double breakOutBarSize = BarUtil.barSize(original_quotes[breakOutBar]);
			if (( breakOutBarSize > 3 * avg4Size )) 
				found = true;
		}*/
		
		if ( found == false ){
			breakOutBar = lastbar-1;
			double avg4Size = BarUtil.averageBarSizeOpenClose(original_quotes, breakOutBar-4, breakOutBar-1);
			double breakOutBarSize = BarUtil.barSize(original_quotes[breakOutBar]);
			if (!( breakOutBarSize > 3 * avg4Size )) /*&& (prevBarSize > 2 * avgOpenCloseSize )*/
				return null;
		}
		
		
		double avgOpenCloseSize = BarUtil.averageBarSizeOpenClose(original_quotes);
		
		// filters
		// filter : has to break out the last 4 bars
		if (BarUtil.isUpBar(original_quotes[breakOutBar])){
			for ( int i = breakOutBar-1; i >= breakOutBar-4; i--)
				if (original_quotes[i].high > original_quotes[breakOutBar].high)
					return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar])){
			for ( int i = lastbar-2; i >= lastbar-5; i--)
				if (original_quotes[i].low < original_quotes[breakOutBar].low)
					return null;
		}
		
		// filter : has to be double size of every bar
		for ( int i = breakOutBar-1; i >= breakOutBar-4; i--)
			if (BarUtil.barSize(original_quotes[i]) > BarUtil.barSize(original_quotes[breakOutBar])/2)
				return null;
		
		
		// filter : not the 4th bar at the same direction
		if (BarUtil.isUpBar(original_quotes[breakOutBar])){
			if ( BarUtil.isUpBar(original_quotes[breakOutBar-1]) && BarUtil.isUpBar(original_quotes[breakOutBar-2]) && BarUtil.isUpBar(original_quotes[breakOutBar-3]))
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar])){
			if ( BarUtil.isDownBar(original_quotes[breakOutBar-1]) && BarUtil.isDownBar(original_quotes[breakOutBar-2]) && BarUtil.isDownBar(original_quotes[breakOutBar-3]))
					return null;
		}

		
		// filter : has to break out double of the average 4 bar's high/low
		/*double avg4HighLow = BarUtil.averageBarSize(original_quotes, breakOutBar-4, breakOutBar-1);
		for ( int i = breakOutBar-1; i >= breakOutBar-4; i--){
			if (BarUtil.isUpBar(original_quotes[breakOutBar]) && ((original_quotes[breakOutBar].close - original_quotes[i].high) < 0.3 * avg4HighLow))
				return null;
			else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && ((original_quotes[i].low - original_quotes[breakOutBar].close ) < 0.3 * avg4HighLow))
				return null;
		}*/
		
		// filter : no diverage
		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) ){
			if ( original_quotes[breakOutBar-2].high - original_quotes[breakOutBar-1].low > 2.8*avgOpenCloseSize )
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) ){
			if ( original_quotes[breakOutBar-1].high - original_quotes[breakOutBar-2].low > 2.8*avgOpenCloseSize )
				return null;
		}*/

		
		// filter, has to close 70% towards the bar
		if (BarUtil.isUpBar(original_quotes[breakOutBar])){
			if (((original_quotes[breakOutBar].high - original_quotes[breakOutBar].close)/ (original_quotes[breakOutBar].high - original_quotes[breakOutBar].low)) > 0.3)  //0.7 ideal
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar])){
			if (((original_quotes[breakOutBar].close - original_quotes[breakOutBar].low)/ (original_quotes[breakOutBar].high - original_quotes[breakOutBar].low)) > 0.3)
				return null;
		}
		
		
		double[] ema20 = Indicator.calculateEMA(original_quotes, 20);
		
		// MA, should not already touch MA 3 times
		if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close > ema20[breakOutBar]){
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsUp(original_quotes, ema20, 0 );
			if (( molL != null ) && ( molL.getNumOfBreakOuts() >=4 )){
				return null;
			}
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close < ema20[breakOutBar]){
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsDown(original_quotes, ema20, 0 );
			if (( molL != null ) && ( molL.getNumOfBreakOuts() >=4 )){
				return null;
			}
		}
		
		// filter : not the enter if too many bars across 20MA
		if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].high > ema20[breakOutBar]){
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(original_quotes, ema20, 2);
			if ( breakOutBar - lastDownPos  >= 12 )
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].low < ema20[breakOutBar]){
			int lastAbovePos = Pattern.findLastPriceConsectiveAboveMA(original_quotes, ema20, 2);
			if ( breakOutBar - lastAbovePos  >= 12 )
				return null;
		}

		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close > ema20[breakOutBar]){
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(original_quotes, ema20, 2);
			if (original_quotes[breakOutBar].close - ema20[lastDownPos] > 15 * avgOpenCloseSize)
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close < ema20[breakOutBar]){
			int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(original_quotes, ema20, 2);
			if (ema20[lastUpPos] - original_quotes[breakOutBar].close > 15 * avgOpenCloseSize)
				return null;
		}*/
		
		// filter : not the enter if too many bars above 20MA
		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].low > ema20[breakOutBar]){
			int aboveBar = breakOutBar;
			while (original_quotes[aboveBar-1].low > ema20[aboveBar-1])
				aboveBar--;
			//if ( breakOutBar - aboveBar > 9 )
			//	return null;
			for ( int i = breakOutBar-1; i >= aboveBar; i--){
				ConsectiveBars conUps = Utility.findLastConsectiveUpBars( original_quotes, aboveBar-1, i);
				double largestUp = original_quotes[conUps.getEnd()].close - original_quotes[conUps.getBegin()].open;
				if ( largestUp > 5 * avgOpenCloseSize )
					return null;
			}

		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].high < ema20[breakOutBar]){
			int belowBar = breakOutBar;
			while (original_quotes[belowBar-1].high < ema20[belowBar-1])
				belowBar--;
			//if ( breakOutBar - belowBar > 9 )
			//	return null;
			for ( int i = breakOutBar-1; i >= belowBar; i--){
				ConsectiveBars conDowns = Utility.findLastConsectiveDownBars( original_quotes, belowBar-1, i);
				double largestUp = original_quotes[conDowns.getBegin()].open - original_quotes[conDowns.getEnd()].close;
				if ( largestUp > 5 * avgOpenCloseSize )
					return null;
			}
		}*/
		
		
		BarTrendBarDTO t = new BarTrendBarDTO( BarUtil.isUpBar(original_quotes[breakOutBar])?Constants.DIRECTION_UP:Constants.DIRECTION_DOWN);
		t.triggerPrice = original_quotes[breakOutBar].close;
		//t.triggerBarSize = prevBarSize;
		//t.avg4BarSize = avg4Size;
		return t;
		
	}
	

	
	public 	BarTrendBarDTO detectBreakOut2(){
		int lastbar = original_quotes.length-1;
		int breakOutBar = 0;
		boolean found = false;
		int slimJimStart = 0;
		int slimJimEnd = 0;
		
		double avgOpenCloseSize = BarUtil.averageBarSizeOpenClose(original_quotes);
		double avgBarSize = BarUtil.averageBarSize(original_quotes);

		breakOutBar = lastbar-1;
		for ( int i = breakOutBar -1; i >= breakOutBar -4; i--){
			int maxBar = 6;
			slimJimStart = i-maxBar;
			slimJimEnd = i;
			double avgLast8HighLow = BarUtil.averageBarSize(original_quotes, slimJimStart, slimJimEnd);
			double last8High = Utility.getHigh( original_quotes, slimJimStart, slimJimEnd).high;
			double last8Low = Utility.getLow( original_quotes, slimJimStart, slimJimEnd).low;
	 		double band = last8High - last8Low ;
	 		//System.out.println("BreakoutBar:" + original_quotes[breakOutBar].time + "last8High:"+last8High + "last8Low:" + last8Low + "i:"+i);
	 		if ( band < 2 * avgBarSize ){
				if (BarUtil.isUpBar(original_quotes[breakOutBar]) && 
						(original_quotes[breakOutBar].close - last8High >= avgBarSize)){
					//double avg4Size = BarUtil.averageBarSizeOpenClose(original_quotes, breakOutBar-4, breakOutBar-1);
					//double breakOutBarSize = BarUtil.barSize(original_quotes[breakOutBar]);
					//if (( breakOutBarSize > 3 * avg4Size )) 
						found = true;
						break;
				}
				else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && 
						(last8Low - original_quotes[breakOutBar].close >= avgBarSize)){
					//double avg4Size = BarUtil.averageBarSizeOpenClose(original_quotes, breakOutBar-4, breakOutBar-1);
					//double breakOutBarSize = BarUtil.barSize(original_quotes[breakOutBar]);
					//if (( breakOutBarSize > 3 * avg4Size )) 
						found = true;
						break;
				}
	 		}
		}

		if (!found )
			return null;
		/*
		if ( found == false ){
			breakOutBar = lastbar-1;
			double avg4Size = BarUtil.averageBarSizeOpenClose(original_quotes, breakOutBar-4, breakOutBar-1);
			double breakOutBarSize = BarUtil.barSize(original_quotes[breakOutBar]);
			if (!( breakOutBarSize > 3 * avg4Size )) 
				return null;
		}*/
		
		//if ( BarUtil.barSize(original_quotes[breakOutBar]) < 2 * avgOpenCloseSize )
		//	return null;
		//if ( BarUtil.barSize(original_quotes[breakOutBar]) > 6 * avgOpenCloseSize )
		//	return null;
		
		// filters  
		// filter : has to break out the last 4 bars
		if (BarUtil.isUpBar(original_quotes[breakOutBar])){
			for ( int i = breakOutBar-1; i >= breakOutBar-4; i--)
				if (original_quotes[i].high > original_quotes[breakOutBar].high)
					return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar])){
			for ( int i = lastbar-2; i >= lastbar-5; i--)
				if (original_quotes[i].low < original_quotes[breakOutBar].low)
					return null;
		}
		
		// filter : has to be double size of every bar
		//for ( int i = breakOutBar-1; i >= breakOutBar-4; i--)
		//	if (BarUtil.barSize(original_quotes[i]) > BarUtil.barSize(original_quotes[breakOutBar])/2)
		//		return null;
		
		
		// filter : not the 4th bar at the same direction
		//if (BarUtil.isUpBar(original_quotes[breakOutBar])){
		//	if ( BarUtil.isUpBar(original_quotes[breakOutBar-1]) && BarUtil.isUpBar(original_quotes[breakOutBar-2]) && BarUtil.isUpBar(original_quotes[breakOutBar-3]))
		//		return null;
		//}
		//else if (BarUtil.isDownBar(original_quotes[breakOutBar])){
		//	if ( BarUtil.isDownBar(original_quotes[breakOutBar-1]) && BarUtil.isDownBar(original_quotes[breakOutBar-2]) && BarUtil.isDownBar(original_quotes[breakOutBar-3]))
		//			return null;
		//}

		
		// filter : has to break out double of the average 4 bar's high/low
		/*double avg4HighLow = BarUtil.averageBarSize(original_quotes, breakOutBar-4, breakOutBar-1);
		for ( int i = breakOutBar-1; i >= breakOutBar-4; i--){
			if (BarUtil.isUpBar(original_quotes[breakOutBar]) && ((original_quotes[breakOutBar].close - original_quotes[i].high) < 0.3 * avg4HighLow))
				return null;
			else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && ((original_quotes[i].low - original_quotes[breakOutBar].close ) < 0.3 * avg4HighLow))
				return null;
		}*/
		
		// filter : no diverage
		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) ){
			if ( original_quotes[breakOutBar-2].high - original_quotes[breakOutBar-1].low > 2.8*avgOpenCloseSize )
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) ){
			if ( original_quotes[breakOutBar-1].high - original_quotes[breakOutBar-2].low > 2.8*avgOpenCloseSize )
				return null;
		}*/

		
		// filter, has to close 70% towards the bar
		//if (BarUtil.isUpBar(original_quotes[breakOutBar])){
		//	if (((original_quotes[breakOutBar].high - original_quotes[breakOutBar].close)/ (original_quotes[breakOutBar].high - original_quotes[breakOutBar].low)) > 0.3)  //0.7 ideal
		//		return null;
		//}
		//else if (BarUtil.isDownBar(original_quotes[breakOutBar])){
		//	if (((original_quotes[breakOutBar].close - original_quotes[breakOutBar].low)/ (original_quotes[breakOutBar].high - original_quotes[breakOutBar].low)) > 0.3)
		//		return null;
		//}
		
		
		double[] ema20 = Indicator.calculateEMA(original_quotes, 20);
		
		// this bar or lastbar should from 20MA
		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close > ema20[breakOutBar]){
			if (!((original_quotes[breakOutBar].low < ema20[breakOutBar]) || (original_quotes[breakOutBar-1].low < ema20[breakOutBar-1])))
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close < ema20[breakOutBar]){
			if (!((original_quotes[breakOutBar].high > ema20[breakOutBar]) || (original_quotes[breakOutBar-1].high > ema20[breakOutBar-1])))
				return null;
		}*/
		
		
		// MA, should not already touch MA 3 times
		if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close > ema20[breakOutBar]){
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsUp(original_quotes, ema20, 0 );
			if (( molL != null ) && ( molL.getNumOfBreakOuts() >=4 )){
				return null;
			}
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close < ema20[breakOutBar]){
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsDown(original_quotes, ema20, 0 );
			if (( molL != null ) && ( molL.getNumOfBreakOuts() >=4 )){
				return null;
			}
		}
		
		// filter : not the enter if too many bars across 20MA  
		if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].high > ema20[breakOutBar]){
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(original_quotes, ema20, 2);
			if ( breakOutBar - lastDownPos  >= 12 )
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].low < ema20[breakOutBar]){
			int lastAbovePos = Pattern.findLastPriceConsectiveAboveMA(original_quotes, ema20, 2);
			if ( breakOutBar - lastAbovePos  >= 12 )
				return null;
		}

		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close > ema20[breakOutBar]){
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(original_quotes, ema20, 2);
			if (original_quotes[breakOutBar].close - ema20[lastDownPos] > 15 * avgOpenCloseSize)
				return null;
		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].close < ema20[breakOutBar]){
			int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(original_quotes, ema20, 2);
			if (ema20[lastUpPos] - original_quotes[breakOutBar].close > 15 * avgOpenCloseSize)
				return null;
		}*/
		
		// filter : not the enter if too many bars above 20MA
		/*if (BarUtil.isUpBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].low > ema20[breakOutBar]){
			int aboveBar = breakOutBar;
			while (original_quotes[aboveBar-1].low > ema20[aboveBar-1])
				aboveBar--;
			//if ( breakOutBar - aboveBar > 9 )
			//	return null;
			for ( int i = breakOutBar-1; i >= aboveBar; i--){
				ConsectiveBars conUps = Utility.findLastConsectiveUpBars( original_quotes, aboveBar-1, i);
				double largestUp = original_quotes[conUps.getEnd()].close - original_quotes[conUps.getBegin()].open;
				if ( largestUp > 5 * avgOpenCloseSize )
					return null;
			}

		}
		else if (BarUtil.isDownBar(original_quotes[breakOutBar]) && original_quotes[breakOutBar].high < ema20[breakOutBar]){
			int belowBar = breakOutBar;
			while (original_quotes[belowBar-1].high < ema20[belowBar-1])
				belowBar--;
			//if ( breakOutBar - belowBar > 9 )
			//	return null;
			for ( int i = breakOutBar-1; i >= belowBar; i--){
				ConsectiveBars conDowns = Utility.findLastConsectiveDownBars( original_quotes, belowBar-1, i);
				double largestUp = original_quotes[conDowns.getBegin()].open - original_quotes[conDowns.getEnd()].close;
				if ( largestUp > 5 * avgOpenCloseSize )
					return null;
			}
		}*/
		
		BarTrendBarDTO t = new BarTrendBarDTO( BarUtil.isUpBar(original_quotes[breakOutBar])?Constants.DIRECTION_UP:Constants.DIRECTION_DOWN);
		t.triggerPrice = original_quotes[lastbar].close;
		//t.triggerPrice = original_quotes[breakOutBar].close;
		//t.triggerBarSize = prevBarSize;
		//t.avg4BarSize = avg4Size;
		t.begin = slimJimStart;
		t.end = slimJimEnd;
		return t;
		
	}


	
	

	
	
	
	public int detectBreakOutPullBack(){
		BarTrendBarDTO lastTrend = bars.get(bars.size() - 2);

		int breakOutBeginPos = lastTrend.begin;
		int breakOutEndPos = lastTrend.end;
		
		double breakOut = Math.abs(original_quotes[breakOutBeginPos].open - original_quotes[breakOutEndPos].close);
		int breakOutSize = breakOutEndPos - breakOutBeginPos + 1;
		
		if (( breakOutSize == 1 ) && ( breakOut > 3 * avgBarSizeOpenClose )){
		}

		
		return null;
	}

	
	
	
	
	
	private double getHigh( BarTrendBarDTO btdd ){
		double high = 0;
		for ( int i = btdd.begin; i <= btdd.end; i++){
			if ( original_quotes[i].high > high )
				high = original_quotes[i].high;
		}
		return high;
	}
	
	private double getLow( BarTrendBarDTO btdd ){
		double low = 999;
		for ( int i = btdd.begin; i <= btdd.end; i++){
			if ( original_quotes[i].low < low )
				low = original_quotes[i].low;
		}
		return low;
	}
	
	private double getGreen( BarTrendBarDTO btd ){
		return original_quotes[btd.end].close - original_quotes[btd.begin].open;
	}
	
	private double getRed( BarTrendBarDTO btd ){
		return original_quotes[btd.begin].open - original_quotes[btd.end].close;
	}
	
	private double getOpenCloseSize( BarTrendBarDTO btd ){
		return Math.abs(original_quotes[btd.begin].open - original_quotes[btd.end].close);
	}
	
}
