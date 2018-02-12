package tao.trading.setup;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.QuoteData;
import tao.trading.SetupStatus;
import tao.trading.Trade;
import tao.trading.dao.DiverageDAO;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;

public class BreakHHLL {
	public SetupStatus status;

	public static BreakHHLLDto detect(QuoteData data, QuoteData[] quotes, String action, int pullbackStartPos )
	{
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, 20);
		double triggerPrice;
		
		// check peak high entries && HH_LL entries
		PushList pushListHHLL = null;
		if (Constants.ACTION_BUY.equals(action)){
			pushListHHLL = PushSetup.findPastLowsByX(quotes, pullbackStartPos, lastbar, 2);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				PushHighLow lastPush = pushListHHLL.phls[pushSize - 1];
				
				if (( quotes[lastbar-1].high <= lastPush.pullBack.high ) && ( quotes[lastbar].high > lastPush.pullBack.high )) {
					triggerPrice = lastPush.pullBack.high;
					BreakHHLLDto dto = new BreakHHLLDto(Constants.ACTION_BUY,triggerPrice);
					return dto;
				}
			}
		}
		else if (Constants.ACTION_SELL.equals(action)){
			pushListHHLL = PushSetup.findPastHighsByX(quotes, pullbackStartPos, lastbar, 2);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				PushHighLow lastPush = pushListHHLL.phls[pushSize - 1];
				
				if (( quotes[lastbar-1].low >= lastPush.pullBack.low ) && ( quotes[lastbar].low < lastPush.pullBack.low )) {
					triggerPrice = lastPush.pullBack.low;
					BreakHHLLDto dto = new BreakHHLLDto(Constants.ACTION_SELL,triggerPrice);
					return dto;
				}
			}
		}
		
		// check fall out of bottom entry
		if (Constants.ACTION_BUY.equals(action)){
			if (( quotes[lastbar-1].high <= quotes[pullbackStartPos].high ) && ( quotes[lastbar].high > quotes[pullbackStartPos].high )) {
				triggerPrice = quotes[pullbackStartPos].high;
				BreakHHLLDto dto = new BreakHHLLDto(Constants.ACTION_BUY,triggerPrice);
				return dto;
			}
		}
		else if (Constants.ACTION_SELL.equals(action)){
			if (( quotes[lastbar-1].low >= quotes[pullbackStartPos].low ) && ( quotes[lastbar].low < quotes[pullbackStartPos].low )) {
				triggerPrice = quotes[pullbackStartPos].low;
				BreakHHLLDto dto = new BreakHHLLDto(Constants.ACTION_SELL,triggerPrice);
				return dto;
			}
		}
		
		return null;
	}
}
	


