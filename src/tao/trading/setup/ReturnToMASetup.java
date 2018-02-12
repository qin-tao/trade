package tao.trading.setup;

import java.util.Date;
import java.util.Vector;

import tao.trading.Constants;
import tao.trading.EmailSender;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.SetupStatus;
import tao.trading.Trade;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;


/*********************************************************************************************************
 * 
 * 
 * 	PULL BACK TO MA SETUPS
 * 
 * 
 * 
 **********************************************************************************************************/
public class ReturnToMASetup
{
	
	public static ReturnToMASetupDto detect(QuoteData data, QuoteData[] quotes ){

		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		if (downPos > upPos){ 
			// check if touches MA
			boolean touchMA = false;
			int touchPos = 0;
			for ( int i = downPos; i <= lastbar; i++){
				if (quotes[i].high > ema20[i]){
					touchMA = true;
					touchPos = i;
				}
			}
			
			if ( touchMA ){
				ReturnToMASetupDto setup = new ReturnToMASetupDto();
				setup.action = Constants.ACTION_SELL;
				if ( touchPos == lastbar -1 )
					setup.status = SetupStatus.TRIGGER;
				else
					setup.status = SetupStatus.DURING;
					
				int startL = downPos;
				while (( quotes[startL].high < ema20[startL]) && (startL > 0 ))
					startL--;
	
				int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
				if (prevConsectiveUpPos == Constants.NOT_FOUND)
					prevConsectiveUpPos = 0;
	
				int pullBackStart = Utility.getLow( quotes, prevConsectiveUpPos, downPos).pos;
				
				setup.pullBackStartPos = pullBackStart;
				setup.pullBackStartTime = quotes[pullBackStart].time;
				return setup;
			}
		}
		else if (upPos > downPos){
			// check if touches MA
			boolean touchMA = false;
			int touchPos = 0;
			for ( int i = upPos; i <= lastbar; i++){
				if (quotes[i].low < ema20[i]){
					touchMA = true;
					touchPos = i;
				}
			}

			if ( touchMA ){
				ReturnToMASetupDto setup = new ReturnToMASetupDto();
				setup.action = Constants.ACTION_BUY;
				if ( touchPos == lastbar -1 )
					setup.status = SetupStatus.TRIGGER;
				else
					setup.status = SetupStatus.DURING;

				int startL = upPos;
				while (( quotes[startL].low > ema20[startL]) && ( startL> 0 ))
					startL--;
	
				int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
				if (prevConsectiveDownPos == Constants.NOT_FOUND)
					prevConsectiveDownPos = 0;
				
				int pullBackStart = Utility.getHigh( quotes, prevConsectiveDownPos, upPos).pos;
				
				setup.pullBackStartPos = pullBackStart;
				setup.pullBackStartTime = quotes[pullBackStart].time;
				return setup;
			}
		}	
		
		return null;
	
	}

	
	
	
	
	
	
	
	// TO BE DELETED
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/****************************************************************************
	 * 
	 * This is to detect when a price pull back and touch the Moving average	
	 * @param quotes
	 * @return
	 *****************************************************************************/
	public static ReturnToMADAO detectReturnTo20MA(QuoteData[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		if ((downPos > upPos) && ( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema20[lastbar]))
		{
			int startL = downPos;
			while (( quotes[startL].high < ema20[startL]) && (startL > 0 ))
				startL--;

			int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
			if (prevConsectiveUpPos == Constants.NOT_FOUND)
				prevConsectiveUpPos = 0;

			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pullBackStart = Utility.getLow( quotes, startL, downPos).pos;
			
			return new ReturnToMADAO( Constants.DIRECTION_DOWN, ema20[lastbar], pullBackStart);

		}
		else if ((upPos > downPos) && ( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema20[lastbar]))
		{

			//System.out.println("check buy at " + quotes[lastbar].time);
			int startL = upPos;
			while (( quotes[startL].low > ema20[startL]) && ( startL> 0 ))
				startL--;

			int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
			if (prevConsectiveDownPos == Constants.NOT_FOUND)
				prevConsectiveDownPos = 0;
			
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pullBackStart = Utility.getHigh( quotes, startL, upPos).pos;
			
			return new ReturnToMADAO( Constants.DIRECTION_UP, ema20[lastbar], pullBackStart);
		
		}	
		
		return null;
	}

	
	
	
	/****************************************************************************
	 * 
	 * This is to detect when a price pull back and touch the Moving average	
	 * @param quotes
	 * @return
	 *****************************************************************************/
	public static ReturnToMADAO detect(QuoteData[] quotes, int ma)
	{
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, ma);
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, 2);

		if ((downPos > upPos) && ( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema[lastbar]))
		{
			int startL = downPos;
			while (( quotes[startL].high < ema[startL]) && (startL > 0 ))
				startL--;

			int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, startL, 2);
			if (prevConsectiveUpPos == Constants.NOT_FOUND)
				prevConsectiveUpPos = 0;

			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, startL, 2);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pullBackStart = Utility.getLow( quotes, startL, downPos).pos;
			
			return new ReturnToMADAO( Constants.DIRECTION_DOWN, startL, downPos, pullBackStart, ma);

		}
		else if ((upPos > downPos) && ( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema[lastbar]))
		{
			int startL = upPos;
			while (( quotes[startL].low > ema[startL]) && ( startL> 0 ))
				startL--;

			int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, startL, 2);
			if (prevConsectiveDownPos == Constants.NOT_FOUND)
				prevConsectiveDownPos = 0;
			
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, startL, 2);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pullBackStart = Utility.getHigh( quotes, startL, upPos).pos;
			
			return new ReturnToMADAO( Constants.DIRECTION_UP, startL, upPos, pullBackStart, ma);
		
		}	
		
		return null;
	}


	
	public static ReturnToMADAO detect2(QuoteData[] quotes, int ma)
	{
		int lastbar = quotes.length - 1;
		double[] ema = Indicator.calculateEMA(quotes, ma);
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema, 2);

		if ((downPos > upPos) && ( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema[lastbar]))
		{
			for ( int i = downPos+1; i <= lastbar; i++)
			{
				if ( quotes[i].high >= ema[i])
				{
					int startL = downPos;
					while (( quotes[startL].high < ema[startL]) && (startL > 0 ))
						startL--;

					int pullBackStart = Utility.getLow( quotes, startL, downPos).pos;
					
					return new ReturnToMADAO( Constants.DIRECTION_DOWN, ema[lastbar], pullBackStart);
				}
			}
		}
		else if ((upPos > downPos) && ( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema[lastbar]))
		{
			for ( int i = upPos+1; i <= lastbar; i++)
			{
				if ( quotes[i].low <= ema[i])
				{
					//System.out.println("check buy at " + quotes[lastbar].time);
					int startL = upPos;
					while (( quotes[startL].low > ema[startL]) && ( startL> 0 ))
						startL--;

					int pullBackStart = Utility.getHigh( quotes, startL, upPos).pos;
					
					return new ReturnToMADAO( Constants.DIRECTION_UP, ema[lastbar], pullBackStart);
				}
			}
		
		}	
		
		return null;
	}


	
	

}
