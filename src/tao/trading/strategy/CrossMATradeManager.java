package tao.trading.strategy;

import java.text.DecimalFormat;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;
import com.sun.corba.se.impl.orbutil.closure.Constant;


public class CrossMATradeManager extends TradeManager
{
	int RETRY_COUNT = 1;
	int TRADE_SIZE = 2;
	int DISTANCE_FACTOR = 5;
	int DISTANCE_FACTOR_PREV = 5;
	double PULLBACK_FACTOR = 0.5;
	int PAST_PERIOD = 640;	// 6 hours
	int DISTANCE_FACT = 5;
	
	public CrossMATradeManager(EClientSocket client, Contract contract, int symbolIndex,  Logger logger)
	{
		super( client, contract, symbolIndex, logger );
	}


	public Trade calculate200MACrossOver( Vector<QuoteData> qts, double[] ema200, double avgBarSize)
	{
		int size = qts.size();
		Object[] quotes = qts.toArray();

		// at least is distance from 200ma
		int lastbar = size -1;
		
		if (((((QuoteData)quotes[lastbar-1])).high < ema200[lastbar-1]) &&
			((((QuoteData)quotes[lastbar-2])).high < ema200[lastbar-2]) &&
			((((QuoteData)quotes[lastbar-3])).high < ema200[lastbar-3]) &&
			((((QuoteData)quotes[lastbar-4])).high < ema200[lastbar-4]) &&
			((((QuoteData)quotes[lastbar-5])).high < ema200[lastbar-5]))
		{
			if ((((QuoteData)quotes[lastbar])).high >= ema200[lastbar])
			{
				/*
				// it has to come from a distance
				int pos = 0;
				double maxdistance = 0;
				for ( int i = lastbar-1; i >=0; i-- )
				{
					if ( ema200[i] - ((QuoteData)quotes[i]).low > maxdistance )
						maxdistance = ema200[i] - ((QuoteData)quotes[i]).low;
					if (((QuoteData)quotes[i]).high > ema200[i])
						break;
				}

				if ( maxdistance > DISTANCE_FACT * avgBarSize )*/
				{
					System.out.println("Trade Found SELL for " + symbol + " at " + ((QuoteData)quotes[lastbar]).time);
					logger.info("Trade Found SELL for " + symbol + " at " + ((QuoteData)quotes[lastbar]).time);
					Trade tt = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
					tt.action = Constants.ACTION_SELL;
					tt.status = Constants.STATUS_OPEN;
					tt.reEnter = 0;
					tt.POSITION_SIZE = TRADE_SIZE;
					tt.addEntryQuotes((QuoteData)quotes[lastbar]);
					System.out.println("Trade placed:" + tt.toString());
					return tt;
				}
			}
		}

		if (((((QuoteData)quotes[lastbar-1])).low > ema200[lastbar-1]) &&
			((((QuoteData)quotes[lastbar-2])).low > ema200[lastbar-2]) &&
			((((QuoteData)quotes[lastbar-3])).low > ema200[lastbar-3]) &&
			((((QuoteData)quotes[lastbar-4])).low > ema200[lastbar-4]) &&
			((((QuoteData)quotes[lastbar-5])).low > ema200[lastbar-5]))
		{
			if ((((QuoteData)quotes[lastbar])).low <= ema200[lastbar])
			{
				// it has to come from a distance
				/*
				int pos = 0;
				double maxdistance = 0;
				for ( int i = lastbar-1; i >=0; i-- )
				{
					if (((QuoteData)quotes[i]).high - ema200[i] > maxdistance )
						maxdistance = ((QuoteData)quotes[i]).high - ema200[i];
					if (((QuoteData)quotes[i]).low < ema200[i])
						break;
				}

				if ( maxdistance > DISTANCE_FACT * avgBarSize )*/
				{
					System.out.println("Trade Found SELL for " + symbol + " at " + ((QuoteData)quotes[lastbar]).time);
					logger.info("Trade Found SELL for " + symbol + " at " + ((QuoteData)quotes[lastbar]).time);
					Trade tt = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
					tt.action = Constants.ACTION_BUY;
					tt.status = Constants.STATUS_OPEN;
					tt.reEnter = 0;
					tt.POSITION_SIZE = TRADE_SIZE;
					tt.addEntryQuotes((QuoteData)quotes[lastbar]);
					System.out.println("Trade placed:" + tt.toString());
					return tt;
				}
			}
		}

		return null;
	}



	
	public void trackTradeEntry(QuoteData quote, double[] sma200, double avgBarSize)
	{
		Vector<QuoteData> entryQuote = trade.entryQuotes;
		int smalastbar = sma200.length -1;
		
		if ((entryQuote == null) || ( entryQuote.size() < 2 ))
		{
			logger.info("Not enough entry data for trade"); 
			return;
		}
		
		int entryQuoteSize = entryQuote.size();
		QuoteData lastQuote = (QuoteData) entryQuote.elementAt(entryQuoteSize-1);
		QuoteData priLastQuote = (QuoteData) entryQuote.elementAt(entryQuoteSize-2);

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((lastQuote.low < priLastQuote.low) && (lastQuote.high < priLastQuote.high))
			{
				if ( quote.close - sma200[smalastbar] > 3 * avgBarSize)
				{
					logger.info("Cross above went too far, abandon the trade");
					trade = null;
					return;
				}
				else
				{	
					logger.info(symbol + " order triggered at " + lastQuote.time + " price " + lastQuote.close);
					trade.stop = getHigh(entryQuote);
					trade.price = quote.close;
				
					if ( trade.stop - trade.price > 2 * avgBarSize )
					{
						logger.info("Trade's stop is too large, abandon the trade)");
						trade = null;
						return;
					}
					else
					{	
						placeMktOrder();
						trade.status = Constants.STATUS_PLACED;
						trade.position = Constants.POSITION_SHORT;
						trade.trackQuotes.clear();
					}
				}
			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((lastQuote.high > priLastQuote.high) && (lastQuote.low > priLastQuote.low))
			{
				if ( sma200[smalastbar]- quote.close  > 3 * avgBarSize)
				{
					logger.info("Cross above went too far, abandon the trade");
					trade = null;
					return;
				}
				else
				{
					logger.info(symbol + " order triggered at " + lastQuote.time + " price " + lastQuote.close);
					trade.stop = getLow(entryQuote);
					trade.price = quote.close;
					if ( trade.price - trade.stop > 2 * avgBarSize )
					{
						logger.info("Trade's stop is too large, abandon the trade)");
						trade = null;
						return;
					}
					else
					{	
						placeMktOrder();
						trade.status = Constants.STATUS_PLACED;
						trade.position = Constants.POSITION_LONG;
						trade.trackQuotes.clear();
					}
				}
			}
		}
	}


	public void trackTradeTarget(QuoteData data, double[] sma200, Vector<QuoteData> quotes5M, double avgBarSize)
	{
		int smaSize = sma200.length;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (data.close > trade.stop)
			{
				logger.info("TrackTradeStop, close > " + trade.stop);

				Order order = createOrder();
				order.m_action = Constants.ACTION_BUY;
				order.m_totalQuantity = trade.POSITION_SIZE;
				order.m_orderType = "MKT";
				logger.info(symbol + " position stopped out: " + displayOrder(order));
				System.out.println(symbol + " position stopped out: " + displayOrder(order));

				placeMktOrder(order);
				trade.status = Constants.STATUS_STOPPEDOUT;

				trade.reEnter--;
				if (trade.reEnter < 0)
				{
					logger.info(symbol + " maximum retry reached, trade closed" );
					System.out.println(symbol + " maximum retry reached, trade closed" );
					this.trade = null;
				}
				else
				{
					logger.info(symbol + " " + trade.reEnter + " retry remaining" );
					trade.entryQuotes5M.removeAllElements();
					trade.entryQuotes5M.add(quotes5M.lastElement());
				}
			}
			else if (( data.close < 2*trade.price - trade.stop ) && ( trade.reach2FixedStop == false ))
			{
				// hit breakeven point, take half profit
				logger.info(symbol + " break even point hit - take half profit at " + data.close);
				System.out.println(symbol + " break even point hit - take half profit at " + data.close);
				closeTrade(TRADE_SIZE/2);
				trade.reach2FixedStop = true;
			}
			else if ((sma200[smaSize-1] - data.low > 10*avgBarSize ) || ( trade.price - data.low > 20*avgBarSize))
			{
				System.out.println(symbol + " Trade " + trade.orderId + " target hit, existing");
				trade.status = Constants.STATUS_EXITING;
				trade.exitQuotes.clear();
				trade.exitQuotes.add(quotes5M.lastElement());
				double stopPrice = quotes5M.lastElement().high;
				logger.info("Move stops to the last 5 minute bar:"+stopPrice);
				trade.stop = stopPrice;
				return;
			}
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (data.close < trade.stop)
			{
				logger.info("TrackTradeStop, close < " + trade.stop);

				Order order = createOrder();
				order.m_action = Constants.ACTION_SELL;
				order.m_totalQuantity = trade.POSITION_SIZE;
				order.m_orderType = "MKT";
				logger.info(symbol + " position stopped out: " + displayOrder(order));
				System.out.println(symbol + " position stopped out: " + displayOrder(order));

				placeMktOrder(order);
				trade.status = Constants.STATUS_STOPPEDOUT;

				trade.reEnter--;
				if (trade.reEnter < 0)
				{
					logger.info(symbol + " maximum retry reached, trade closed" );
					System.out.println(symbol + " maximum retry reached, trade closed" );
					this.trade = null;
				}
				else
				{
					logger.info(symbol + " " + trade.reEnter + " retry remaining" );
					trade.entryQuotes5M.removeAllElements();
					trade.entryQuotes5M.add(quotes5M.lastElement());
				}
			}
			else if (( data.close > 2*trade.price - trade.stop ) && ( trade.reach2FixedStop == false ))
			{
				// hit breakeven point, take half profit
				logger.info(symbol + " break even point hit - take half profit at " + data.close);
				System.out.println(symbol + " break even point hit - take half profit at " + data.close);
				closeTrade(TRADE_SIZE/2);
				trade.reach2FixedStop = true;
			}
			else if ((data.high - sma200[smaSize-1] > 10*avgBarSize ) || ( data.high - trade.price > 20* avgBarSize))
			{
				System.out.println(symbol + " Trade " + trade.orderId + " target hit, existing");
				//closeTrade();
				trade.status = Constants.STATUS_EXITING;
				trade.exitQuotes.clear();
				trade.exitQuotes.add(quotes5M.lastElement());
				double stopPrice = quotes5M.lastElement().low;
				logger.info("Move stops to the last 5 minute bar:"+stopPrice);
				trade.stop = stopPrice;
				return;
			}

		}
	}
	
	
	public void trackTradeExit(Vector<QuoteData> quotes, double[] sma200, double avgBarSize )
	{
		Vector<QuoteData> exitQuote = trade.exitQuotes;
		
		int exitQuoteSize = exitQuote.size();
		if ( exitQuoteSize < 2 )
			return;
		
		QuoteData lastQuote = (QuoteData) exitQuote.elementAt(exitQuoteSize-1);
		QuoteData priLastQuote = (QuoteData) exitQuote.elementAt(exitQuoteSize-2);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.exitStatus == Constants.EXIT_STATUS_RUN )
			{
				if (( lastQuote.high > priLastQuote.high ) && ( lastQuote.low > priLastQuote.low))
				{
					// this is a pull back
					int size200MA = sma200.length;
					double distanceTo200MA = sma200[size200MA-1] - quotes.lastElement().high;
				
					if (distanceTo200MA < 5*avgBarSize)
					{
						logger.info("price start to pull back within 5 times of avgBarsize, position should be closed");
						closePosition();
						return;
					}
					
					if ( lastQuote.high > trade.stop)
					{
						logger.info("pull back hit the stop, position closed");
						closePosition();
						return;
					}

					if (( trade.exitPullBackSize != 0 ) && ( trade.exitPullBackSize > 8 * avgBarSize ))
					{
						logger.info("last pull back size of " + trade.exitPullBackSize + " is too big, close position");
						closePosition();
						return;
						
					}
					
					trade.exitStatus = Constants.EXIT_STATUS_PULLBACK;
					trade.exitPullBackStart = priLastQuote.low;
				}
			}
			else if ( trade.exitStatus == Constants.EXIT_STATUS_PULLBACK )
			{
				if ( lastQuote.high > trade.stop)
				{
					logger.info("pull back hit the stop, position closed");
					closePosition();
					return;
				}
				if (( lastQuote.low < priLastQuote.low ) && ( lastQuote.high < priLastQuote.high ))
				{
					logger.info("pullback completed, move the stop to "+priLastQuote.high);
					trade.exitPullBackEnd = priLastQuote.high;
					trade.exitPullBackSize = trade.exitPullBackEnd - trade.exitPullBackStart;
					logger.info("pullback size is " + trade.exitPullBackSize );
					trade.exitStatus = Constants.EXIT_STATUS_RUN;
					trade.stop = priLastQuote.high;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			logger.info("exit strategy for buy is not implemented, exit position");
			closePosition();
			return;
		}
	}
	
	
	


	
}
