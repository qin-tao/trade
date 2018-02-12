package tao.trading.entry;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.strategy.util.BarUtil;

public class Confirmation {

	public void trackEntryUsingConfirmation(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		double[] ema240_20 = Indicator.calculateEMA(quotes240, 20);
		//QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		//int lastbar60 = quotes60.length - 1;
		//double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		//PushHighLow lastPush=null;
		//PushHighLow prevPush=null;
		
		ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;
		//double[] ema = Indicator.calculateEMA(quotes240, returnToMA.getMa());

		int touchPoint = returnToMA.getEndPos() + 1;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (((lastbar240-1) >= touchPoint) && (quotes240[lastbar240-1].low > ema240_20[lastbar240-1])){
				removeTrade();
				return;
			}
			
			if (BarUtil.isDownBar(quotes240[lastbar240-1]) && ( data.low < quotes240[lastbar240-1].low )){
				enterMarketPositionMulti(quotes240[lastbar240-1].low,POSITION_SIZE);
				cancelLimits();
				return;
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (((lastbar240-1) >= touchPoint) && (quotes240[lastbar240-1].high < ema240_20[lastbar240-1])){
				removeTrade();
				return;
			}
			
			if (BarUtil.isUpBar(quotes240[lastbar240-1]) && ( data.high > quotes240[lastbar240-1].high )){
				enterMarketPositionMulti(quotes240[lastbar240-1].high,POSITION_SIZE);
				cancelLimits();
				return;
			}
		}
		

		return;
	}


}
