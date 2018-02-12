package tao.trading.strategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;


public class SlimJimBreakoutTradeManager extends TradeManager
{
	public int NUM_OF_SLIM_JIM_BAR = 10;

	public SlimJimBreakoutTradeManager(EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super( client, contract, symbolIndex,logger );
		/*
		logFile = "c:/trade_logs/slimjimbreakout.log";

		try
		{
			// Create an appending file handler
			//boolean append = true;
			//FileHandler handler = new FileHandler(logFile, append);
			FileHandler handler = new FileHandler(logFile);

			// Add to the desired logger
			logger = Logger.getLogger("SlimJimBreakoutTradeManager");
			logger.addHandler(handler);
		
		} 
		catch (IOException e)
		{
		}
*/
	}

	
	public void trackTradeExit(QuoteData data)//, double[] sma20, double[] sma50, double[] sma200)
	{
		/*
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (data.low < trade.target)
			{
				logger.info("Trade " + trade.orderId + " target hit, exist");
				closeTrade(trade);
			}
		} else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (data.high < trade.target)
			{
				logger.info("Trade " + trade.orderId + " target hit, exist");
				closeTrade(trade);
			}
		}*/
		
		trade.trackQuotes.add(data);
		Object[] quotes = trade.trackQuotes.toArray();
		logger.info("exitQuoteTrigger=" + trade.exitQuoteTrigger);

		if ( trade.action.equals( Constants.ACTION_BUY))
		{
			if ( trade.exitQuoteTrigger > 0 )
			{
				// looking for a pull back 
				int size = quotes.length;
				if ( size < 3 )
					return;
				
				double lowerHigh = 999;
				int lowHighPos = -1;
				for ( int i = 0; i< size-1; i++ )
				{
					if (((QuoteData)quotes[i]).high < lowerHigh )
					{
						lowerHigh = ((QuoteData)quotes[i]).high;
						lowHighPos = i;
					}
				}
				logger.info("lowerHigh =" + lowerHigh);
				
				if (lowHighPos != size - 1 - 1 )
				{
					logger.info("lowerHigh found. reduce trigger");
					trade.trackQuotes.clear();
					trade.exitQuoteTrigger--;
				}
			}
			else
			{ 
				int lastbar = quotes.length - 1;
				if ( lastbar < 1 )
					return;
				
				if (((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low)
				{
					closePosition();
				}	
			}
		}
		else if ( trade.action.equals( Constants.ACTION_SELL))
		{
			if ( trade.exitQuoteTrigger > 0 )
			{
				// looking for a pull back 
				int size = quotes.length;
				if ( size < 3 )
					return;
				
				double higherLow = 0;
				int higherLowPos = -1;
				for ( int i = 0; i< size-1; i++ )
				{
					if (((QuoteData)quotes[i]).low > higherLow )
					{
						higherLow = ((QuoteData)quotes[i]).low;
						higherLowPos = i;
					}
				}
				logger.info("higherLow =" + higherLow);
				
				if (higherLowPos != size - 1 - 1 )
				{
					logger.info("higherLow found, reduce trigger");
					trade.trackQuotes.clear();
					trade.exitQuoteTrigger--;
				}
			}
			else
			{ 
				int lastbar = quotes.length - 1;
				if ( lastbar < 1 )
					return;
				
				if (((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low)
				{
					closePosition();
				}	
			}
		}
	}


	public void trackTradeStop(QuoteData quote)
	{
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((trade.stopQuote != null) && (trade.stopQuote.high > trade.stop))
			{
				if (quote.close > trade.stopQuote.high)
				{
					logger.info("TrackTradeStop, close > " + trade.stop);
					/*
					 * // place a reversal order Order order = createMktOrder();
					 * order.m_action = Constants.ACTION_BUY;
					 * order.m_totalQuantity = trade.positionSize2;
					 * System.out.println("Place reversal order:" +
					 * displayOrder(order));
					 * 
					 * trade.status = Constants.STATUS_REVERSAL;
					 * placeMktOrder(trade, order);
					 */
					//logger.info("Position stopped out: " + displayOrder(order));

					placeMktOrder();
					trade.status = Constants.STATUS_STOPPEDOUT;

					trade.reEnter--;
					if (trade.reEnter <= 0)
					{
						trade = null;
						on_pause = 120 * 12;
					}
				}
			}
		} else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((trade.stopQuote != null) && (trade.stopQuote.low < trade.stop))
			{
				if (quote.close < trade.stopQuote.low)
				{
					logger.info("TrackTradeStop, close < " + trade.stop);

					/*
					 * // place a reversal order Order order = createMktOrder();
					 * order.m_action = Constants.ACTION_SELL;
					 * order.m_totalQuantity = trade.positionSize2; trade.status
					 * = Constants.STATUS_REVERSAL;
					 * logger.info("Place reversal order:" +
					 * displayOrder(order));
					 * 
					 * placeMktOrder(trade,order);
					 */
					//logger.info("Position stopped out: " + displayOrder(order));

					placeMktOrder();
					trade.status = Constants.STATUS_STOPPEDOUT;

					trade.reEnter--;
					if (trade.reEnter <= 0)
					{
						trade = null;
						on_pause = 120 * 12;
					}
				}
			}
		}
	}

	public void writeToTradeLog(Trade tt)
	{
		try
		{
			BufferedWriter out = new BufferedWriter(new FileWriter("c:\trade2.log", true));
			out.write(tt.toString() + "\n");
			out.close();
		} catch (IOException e)
		{
		}

	}

	
	public Trade calculateSlimJimBreakout( QuoteData currentData, Vector<QuoteData> qts )
	{
		int size = qts.size();
		Object[] quotes = qts.toArray();

		int lastbar = size -1;
		//System.out.println("lastbar=" + lastbar);
		
		/*double high = 0;
		double low = 999;
		double range = 999;
		/*
		for ( int i = lastbar-1; i > lastbar-1-slimJimNumOfBars; i--)
		{
			if (((QuoteData)quotes[i]).close > high )
				high = ((QuoteData)quotes[i]).close;
			if (((QuoteData)quotes[i]).open > high )
				high = ((QuoteData)quotes[i]).open;
			if (((QuoteData)quotes[i]).close < low )
				low = ((QuoteData)quotes[i]).close;
			if (((QuoteData)quotes[i]).open < low )
				low = ((QuoteData)quotes[i]).open;
		}
		double range = high - low;
		*/
		
		double high = 0;
		double low = 999;
		double range = 999;
		int pos = 0;
		/*
		for ( int i = lastbar; i > lastbar-5; i--)
		{
			//System.out.println("i=" + i);
			double thisHigh = 0;
			double thisLow = 999;
			
			double[] highs = new double[NUM_OF_SLIM_JIM_BAR];
			double[] lows = new double[NUM_OF_SLIM_JIM_BAR];
			int d = 0;
			for ( int j = i; j > i-NUM_OF_SLIM_JIM_BAR ; j--)
			{
				highs[d] = ((QuoteData)quotes[j]).high;
				lows[d++] = ((QuoteData)quotes[j]).low;
			}
			Arrays.sort(highs);
			Arrays.sort(lows);
			
			thisHigh = highs[NUM_OF_SLIM_JIM_BAR-2]; // we skip the highest one
			thisLow = lows[1]; // we skip the lowest one
			
			/*
			for ( int j = i; j > i-NUM_OF_SLIM_JIM_BAR ; j--)
			{
				//System.out.println("j=" + j);
				
				if (((QuoteData)quotes[j]).high > thisHigh )
					thisHigh = ((QuoteData)quotes[j]).high;
				if (((QuoteData)quotes[j]).low < thisLow )
					thisLow = ((QuoteData)quotes[j]).low;
					
				/*
				if (((QuoteData)quotes[j]).close > thisHigh )
					thisHigh = ((QuoteData)quotes[j]).close;
				if (((QuoteData)quotes[j]).open > thisHigh )
					thisHigh = ((QuoteData)quotes[j]).open;
				if (((QuoteData)quotes[j]).close < thisLow )
					thisLow = ((QuoteData)quotes[j]).close;
				if (((QuoteData)quotes[j]).open < thisLow )
					thisLow = ((QuoteData)quotes[j]).open;
				
			}
			
			double thisRange = thisHigh - thisLow;
			if (thisRange < range )
			{
				range = thisRange;
				high = thisHigh;
				low = thisLow;
				pos = i;
			}
		}*/

		
		
		
		// calculate the last 12 bars
		double[] highs = new double[NUM_OF_SLIM_JIM_BAR];
		double[] lows = new double[NUM_OF_SLIM_JIM_BAR];
		int d = 0;
		for ( int i = lastbar; i > lastbar-NUM_OF_SLIM_JIM_BAR; i--)
		{
			highs[d] = ((QuoteData)quotes[i]).high;
			lows[d++] = ((QuoteData)quotes[i]).low;
		}

		Arrays.sort(highs);
		Arrays.sort(lows);
			
		high = highs[NUM_OF_SLIM_JIM_BAR-1]; // we skip the highest one
		low = lows[0]; 			
		range = high - low;
		
		double avgRange = calculateAverageBarSize(qts);
		
		if (range > avgRange*1.3 )	// note the range above is open-close
			return null;
		
		System.out.println(m_contract.m_symbol + "." + m_contract.m_currency + "slim jim is at - " + ((QuoteData)quotes[lastbar]).time );
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + "range is " + range );
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + "Average Range is " + avgRange );
		
		// look to see if the current price has break the channel 40%
		
		// now check each bar does not break out from another bar
		/*
		for ( int i = lastbar -1; i> lastbar -11; i--)
		{
			for ( int j = lastbar -1; j > lastbar - 11; j--)
			{
				if(((((QuoteData)quotes[i]).high >= ((QuoteData)quotes[j]).high ) && (((QuoteData)quotes[i]).low >= ((QuoteData)quotes[j]).high ))
					|| ((((QuoteData)quotes[i]).high <= ((QuoteData)quotes[j]).low ) && (((QuoteData)quotes[i]).low <= ((QuoteData)quotes[j]).low )))
				{
					return null;
				}
			}
		}*/
		
		//logger.info("Slim Jim detected on " + m_contract.m_symbol + "." + m_contract.m_currency + " at " +  ((QuoteData)quotes[pos]).time );
		//logger.info("High=" + high + " Low=" + low + " Range=" + range );

		if (currentData.close > high )
		{
			if ((( currentData.close - high ) > 0 ) && (( currentData.close - high ) < range * 0.1 ))
			{
				System.out.println("Price broke Slim Jim 40% on the upside of " + high + " - buy on " +  m_contract.m_symbol + "." + m_contract.m_currency);
				//enter trade;
				Trade tt = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				tt.action = Constants.ACTION_BUY;
				tt.status = Constants.STATUS_OPEN;
				tt.price = ((QuoteData)quotes[lastbar]).close;
				tt.targetPrice = high + range * 4;
				tt.stop = low;
				tt.POSITION_SIZE = 1;
				tt.trailamount = range; //currentData.close - low;
				//tt.addEntryQuotes((QuoteData)quotes[lastbar]);
				return tt;
			}

		}
		else if (currentData.close < low )
		{
			if ((( low - currentData.close ) > 0 ) && (( low - currentData.close ) < range * 0.1 ))
			{ 
				System.out.println("Break out Slim Jim on the downside of " + low + " sell on " +  m_contract.m_symbol + "." + m_contract.m_currency);
				Trade tt = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				tt.action = Constants.ACTION_SELL;
				tt.status = Constants.STATUS_OPEN;
				tt.price = ((QuoteData)quotes[lastbar]).close;
				tt.targetPrice = low - range * 4;
				tt.stop = high;
				tt.POSITION_SIZE = 1;
				tt.trailamount = range;//high - currentData.close;
				//tt.addEntryQuotes((QuoteData)quotes[lastbar]);
				return tt;
			}
		}

		return null;
		
	}
	
	private double calculateAverageBarSize( Vector<QuoteData> qts )
	{
		int size = qts.size();
		double rangeTotal = 0;
		
		Iterator itr = qts.iterator(); 
		while(itr.hasNext()) {

		    QuoteData data = (QuoteData)itr.next();
		    rangeTotal += data.high - data.low;
		}
		
		return rangeTotal/size;
		
	}

}
