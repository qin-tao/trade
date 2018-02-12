package tao.trading.entry;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.util.Utility;

public class breakHHLL {

	public void entry_break_LL_HH(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar240 = quotes240.length - 1;
		int lastbar60 = quotes60.length - 1;
		int lastbar15 = quotes15.length - 1;
		int startPos60 = 0;
		double[] ema = Indicator.calculateEMA(quotes60, 20);
			
		int detect240 = Utility.findPositionByHour(quotes240, trade.detectTime, Constants.BACK_TO_FRONT );
		
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			// remove if it is lower than previous low
			/*PushHighLow phlL = trade.phl;
			if ( data.low < phlL.pullBack.low ){
				removeTrade();
				return;
			}*/
			/*double avgSize = BarUtil.averageBarSizeOpenClose(quotes240);
			if (BarUtil.isDownBar(quotes240[lastbar240]) && ( BarUtil.barSize(quotes240[lastbar240]) > 3 * avgSize )){
				removeTrade();
				return;
			}*/
			
			startPos60 = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos60 = Utility.getHigh( quotes60, startPos60-4<0?0:startPos60-4, lastbar60-1).pos;
			
			
			PushList pushList = PushSetup.getDown2PushList( quotes60, startPos60, lastbar60 );
			if ((pushList != null ) &&( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				
				if ( data.high > lastPush.pullBack.high ){
					trade.entryPrice = lastPush.pullBack.high;
					if ( quotes15[lastbar15-1].high > trade.entryPrice ){
						removeTrade();
						return;
					}

					warning("Trade entry_HH_LL: Trade buy entered at " + data.time + " " + trade.entryPrice);
					//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
			    	enterMarketPosition(trade.entryPrice);
					return;
				}
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].high;
			if ( data.high > startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].high > trade.entryPrice ){
					removeTrade();
					return;
				}
				warning("Trade entry_HH_LL higher than start price: Trade entered at " + data.time + " " + startPrice);
				//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	enterMarketPosition(trade.entryPrice);
				return;
			}

		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			// remove if it is high than previous low
			/*PushHighLow phlL = trade.phl;
			if ( data.high > phlL.pullBack.high ){
				removeTrade();
				return;
			}*/

			/*
			double avgSize = BarUtil.averageBarSizeOpenClose(quotes240);
			if (BarUtil.isUpBar(quotes240[lastbar240]) && ( BarUtil.barSize(quotes240[lastbar240]) > 3 * avgSize )){
				removeTrade();
				return;
			}*/

			startPos60 = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos60 = Utility.getLow( quotes60, startPos60-4<0?0:startPos60-4, lastbar60-1).pos;
			
			PushList pushList = PushSetup.getUp2PushList( quotes60, startPos60, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
					
				if ( data.low < lastPush.pullBack.low ){
					trade.entryPrice = lastPush.pullBack.low;
					if ( quotes15[lastbar15-1].low < trade.entryPrice ){
						removeTrade();
						return;
					}
					warning("Trade entry_HH_LL: Trade sell entered at " + data.time + " " + trade.entryPrice);
					//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
			    	enterMarketPosition(trade.entryPrice);
					return;
				}
			}

			int startPos240 = Utility.findPositionByHour(quotes240, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			double startPrice = quotes240[startPos240].low;
			if ( data.low < startPrice ){
				trade.entryPrice = startPrice;
				if ( quotes15[lastbar15-1].low < trade.entryPrice ){
					removeTrade();
					return;
				}
				warning("Trade entry_HH_LL sell lower than start price: Trade entered at " + data.time + " " + startPrice);
				//enterMarketPositionMulti(trade.entryPrice,POSITION_SIZE);
		    	enterMarketPosition(trade.entryPrice);
				return;
			}
		}
	}


}
