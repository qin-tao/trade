package tao.trading.strategy;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.Cup;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;


public class SFTradeManager extends TradeManager
{
	protected Indicator indicator = new Indicator();
	int BREAKOUT_DISTANCE;
	int DISTANCE_CUR;
	int DISTANCE_PRE;
	double PULLBACK_FACTOR;
	int RETRY_COUNT = 1;
	double pipSize;
	double pipMinSpread;
	DecimalFormat decf;
	int positionSize;
	int TIME_CONSTRAIN = 40;
	int STOP_FACTOR = 20;
	int STOP_SPACE = 10;
	
	int TRIGGER_DIST = 0;
	
	int STRETCH_DIST;
	int STRETCH_PULLBACK;
	int STRETCH_SETUP_WIDTH = 10;
	int PULLBACK_WIDTH = 3;  // this is considered with in a pull back
	//int STRETCH_SETUP_HEIGHT = 15;
	
	HashMap<String, Integer> tradeHistory = new HashMap<String, Integer>();

	public SFTradeManager(EClientSocket client, Contract contract, int symbolIndex,  Logger logger)
	{
		super( client, contract, symbolIndex, logger );
	}


	
	public void trackTradeEntry(QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] ema200)
	{
		if ( trade.type == Constants.TRADE_STRETCH )
			trackStretchTradeEntry( data, qts, ema20, ema200);
		else if ( trade.type == Constants.TRADE_PULLBACK )
			trackPullbackTradeEntry( data, qts, ema20, ema200);
	}
	
	
	public void trackStretchTradeEntry(QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] ema200)
	{
		// Entry for Stretch Trade is
		// only the first bar makes new high
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		
		// need at least two bars
		if ( lastbar == trade.entryPos )	
			return; 	// no new bar yet

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
			{
				if ( lastbar - trade.entryPos > 2)
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time + " but did not trigger within 2 bars" );
					trade = null;
					return;
				}
				else
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time );
					System.out.println( symbol + " trade triggered at " + data.close + " " + data.time );
					
					trade.stop = getHigh(quotes, trade.entryPos, lastbar);
					logger.info( symbol + " trade stop is " + trade.stop );
					System.out.println( symbol + " trade stop is " + trade.stop );
					trade.price = data.close;
					trade.targetPrice = trade.price - 40 * pipSize;
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);  
					trade.stopId = placeStopOrder( Constants.ACTION_BUY, adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP));
					trade.status = Constants.STATUS_PLACED;
					trade.position = Constants.POSITION_SHORT;
					trade.triggerPos = lastbar;
				}
			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
			{
				if ( lastbar - trade.entryPos > 2)
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time + " but did not trigger within 2 bars" );
					trade = null;
					return;
				}
				else
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time );
					System.out.println( symbol + " trade triggered at " + data.close + " " + data.time );
					
					trade.stop = getLow(quotes, trade.entryPos, lastbar);
					logger.info( symbol + " trade stop is " + trade.stop );
					System.out.println( symbol + " trade stop is " + trade.stop );
					trade.price = data.close;
					trade.targetPrice = trade.price + 40 * pipSize;
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);  
					trade.stopId = placeStopOrder( Constants.ACTION_SELL, adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN));
					trade.status = Constants.STATUS_PLACED;
					trade.position = Constants.POSITION_SHORT;
					trade.triggerPos = lastbar;
				}
			}
		}
	}


	public void trackPullbackTradeEntry(QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] ema200)
	{
		// Entry for pull back trade is 
		// to make sure it is a light pull back, if it is a deep pull back it might not run long
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		
		// need at least two bars
		if ( lastbar == trade.entryPos )	
			return; 	// no new bar yet

		// qualifier 1:  first bar is not too deep
		// qualifier 2:  second bar start to move away
		
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
			{
				/*if ( lastbar - trade.entryPos > 2)
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time + " but did not trigger within 2 bars" );
					trade = null;
					return;
				}
				else*/
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time );
					System.out.println( symbol + " trade triggered at " + data.close + " " + data.time );
					
					trade.stop = getHigh(quotes, trade.entryPos, lastbar);
					logger.info( symbol + " trade stop is " + trade.stop );
					System.out.println( symbol + " trade stop is " + trade.stop );
					trade.price = data.close;
					trade.targetPrice = trade.price - 40 * pipSize;
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);  
					trade.stopId = placeStopOrder( Constants.ACTION_BUY, adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP));
					trade.status = Constants.STATUS_PLACED;
					trade.position = Constants.POSITION_SHORT;
					trade.triggerPos = lastbar;
				}
			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
			{
				/*
				if ( lastbar - trade.entryPos > 2)
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time + " but did not trigger within 2 bars" );
					trade = null;
					return;
				}
				else*/
				{
					logger.info( symbol + " trade triggered at " + data.close + " " + data.time );
					System.out.println( symbol + " trade triggered at " + data.close + " " + data.time );
					
					trade.stop = getLow(quotes, trade.entryPos, lastbar);
					logger.info( symbol + " trade stop is " + trade.stop );
					System.out.println( symbol + " trade stop is " + trade.stop );
					trade.price = data.close;
					trade.targetPrice = trade.price + 40 * pipSize;
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);  
					trade.stopId = placeStopOrder( Constants.ACTION_SELL, adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN));
					trade.status = Constants.STATUS_PLACED;
					trade.position = Constants.POSITION_SHORT;
					trade.triggerPos = lastbar;
				}
			}
		}
	}

	
				
	public void trackTradeTarget(QuoteData data, Vector<QuoteData> quotes, double[] ema20, double[] ema200)
	{
		if ( trade.type == Constants.TRADE_STRETCH )
			trackStretchTraget( data, quotes, ema20, ema200);
		else if ( trade.type == Constants.TRADE_PULLBACK )
			trackPullbackTraget( data, quotes, ema20, ema200);
	}

	public void trackStretchTraget(QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] sma200)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// move stop once it close below 20MA
			if (( trade.status == Constants.STATUS_PLACED) && (((QuoteData)quotes[lastbar]).high < ema20[lastbar])) 
			{
				trade.stop = trade.price;
				cancelOrder( trade.stopId);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, null);
				trade.status = Constants.STATUS_EXITING;
			}
			
			if (data.low < trade.targetPrice)
			{
				logger.info(symbol + " Trade " + trade.orderId + " target hit, existing");
				System.out.println(symbol + " Trade " + trade.orderId + " target hit, existing");
				closePosition();
				trade = null;
			}
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// move stop once it close below 20MA
			if (( trade.status == Constants.STATUS_PLACED) && (((QuoteData)quotes[lastbar]).low > ema20[lastbar])) 
			{
				trade.stop = trade.price;
				cancelOrder( trade.stopId);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, null);
				trade.status = Constants.STATUS_EXITING;
			}
			if (data.high > trade.targetPrice)
			{
				logger.info(symbol + " Trade " + trade.orderId + " target hit, existing");
				System.out.println(symbol + " Trade " + trade.orderId + " target hit, existing");
				closePosition();
				trade = null;

			}
		}
	}
	

	public void trackPullbackTraget(QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] sma200)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (data.low < trade.targetPrice)
			{
				logger.info(symbol + " Trade " + trade.orderId + " target hit, existing");
				System.out.println(symbol + " Trade " + trade.orderId + " target hit, existing");
				closePosition();
				trade = null;
			}
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (data.high > trade.targetPrice)
			{
				logger.info(symbol + " Trade " + trade.orderId + " target hit, existing");
				System.out.println(symbol + " Trade " + trade.orderId + " target hit, existing");
				closePosition();
				trade = null;

			}
		}
	}

	
	
	public void trackTradeExit(Vector<QuoteData> quotes, double[] sma200 )
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
				
					if (distanceTo200MA < 5*pipSize)
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

					if (( trade.exitPullBackSize != 0 ) && ( trade.exitPullBackSize > 8 * pipSize ))
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
	
	boolean verifyTrendDown(Vector<QuoteData> quotes60M, double[] ema60_5, double[] ema60_20)
	{
		Object[] quotes = quotes60M.toArray();
		int last60Mbar = ema60_5.length-1;
		// 5MA needs to be below 20MA to indicate a downtrend
		logger.info(symbol + " 5 SMA on 60 is " + ema60_5[last60Mbar]);
		logger.info(symbol + " 20 SMA on 60 is " + ema60_20[last60Mbar]);
		
		// we are only remove the first 4 bars after crossing over
		if ( ema60_5[last60Mbar] > ema60_20[last60Mbar] )
		{
			// trend is up
			logger.info(symbol + "trend is up ....");

			int pos = 0;
			for ( int i = last60Mbar-1; i >=0; i-- )
			{
				if (ema60_5[i] < ema60_20[i])
				{
					pos = i;
					break;
				}
			}
		
			if ( last60Mbar - pos <= 3)
			{
				logger.info(symbol + "5-20 Cross within first 3 bar of the cross over to the upper side" );
				return false;
			}
			/*
			else
			{
				// this is against the "trend" so we request at least two "down" bar
				// make sure there are at least three higher bar before the last bar
				if ((((QuoteData)quotes[last60Mbar]).low > ((QuoteData)quotes[last60Mbar-1]).low) &&(((QuoteData)quotes[last60Mbar-1]).low > ((QuoteData)quotes[last60Mbar -2]).low))
					return true;
				else
					return false;
			}*/
			
			return true;

		}
		else if ( ema60_5[last60Mbar] <= ema60_20[last60Mbar] )
		{
			return true;
			/*
			// it is in the down trend but let's not try it when it is over bought
			if ((ema60_5[last60Mbar] - ((QuoteData)quotes[last60Mbar]).low  > overSold )  || //= ema60_20[last60Mbar] - ema60_5[last60Mbar] ) ||
					(ema60_5[last60Mbar-1] - ((QuoteData)quotes[last60Mbar-1]).low > overSold )) //= ema60_20[last60Mbar-1] - ema60_5[last60Mbar-1] ))
			{
				logger.info( symbol + " it is at down but it is too oversold, do not enter");
				return false;
			}

			return true;*/
		}
		
		return false;
		
		
		/*
		// we are looking first pull back after 200MA
		int pos = 0;
		for ( int i = last60Mbar-1; i >=0; i-- )
		{
			if (ema60_5[last60Mbar] < ema60_20[last60Mbar])
			{
				pos = i;
				break;
			}
		}

		logger.info(symbol + "5-20 Cross happened at " + ((QuoteData)quotes[pos]).time);

//		while (((QuoteData)quotes[pos]).low <= ((QuoteData)quotes[pos+1]).low)
//			pos++;

//		logger.info(symbol + "After cross over, price goes up to " + ((QuoteData)quotes[pos]).time);
		
		// then it needs to keep going down
		if ( last60Mbar - pos < 5)
			return true;
		else
			return false;
		*/
	}


	
	
	boolean check60CrossOverUp(Vector<QuoteData> quotes60M, double[] ema60_5, double[] ema60_20)
	{
		Object[] quotes = quotes60M.toArray();
		int last60Mbar = ema60_5.length-1;
		
		if ( ema60_5[last60Mbar] < ema60_20[last60Mbar] )
		{
			logger.info(symbol + "60MA is not on the uptrend");
			return false;
		}
		
		// we are looking first pull back after 200MA
		int pos = 0;
		for ( int i = last60Mbar-1; i >=0; i-- )
		{
			if (ema60_5[last60Mbar] < ema60_20[last60Mbar])
			{
				pos = i;
				break;
			}
		}

		logger.info(symbol + "5-20 Cross happened at " + ((QuoteData)quotes[pos]).time);

		if ( last60Mbar - pos < 3)
			return true;
		else
			return false;
		
	}

	
	boolean check60CrossOverDown(Vector<QuoteData> quotes60M, double[] ema60_5, double[] ema60_20)
	{
		Object[] quotes = quotes60M.toArray();
		int last60Mbar = ema60_5.length-1;
		
		if ( ema60_5[last60Mbar] > ema60_20[last60Mbar] )
		{
			logger.info(symbol + "60MA is not on down uptrend");
			return false;
		}
		
		// we are looking first pull back after 200MA
		int pos = 0;
		for ( int i = last60Mbar-1; i >=0; i-- )
		{
			if (ema60_5[last60Mbar] > ema60_20[last60Mbar])
			{
				pos = i;
				break;
			}
		}

		logger.info(symbol + "5-20 Cross happened at " + ((QuoteData)quotes[pos]).time);

		logger.info(symbol + "After cross over, price goes down to " + ((QuoteData)quotes[pos]).time);
		
		// then it needs to keep going down
		if ( last60Mbar - pos < 3)
			return true;
		else
			return false;
		
	}

	/*
	public void checkStopTriggered(int orderId)
	{
		if ((trade != null ) && (trade.stopId == orderId ))
		{
			/*
			logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " order stopped out");
			
			Integer num = tradeHistory.get(trade.origin);
			if ( num == null )
				tradeHistory.put(trade.origin, new Integer(1));
			else
				tradeHistory.put(trade.origin, num + 1);
			
			trade = null;
		}
	}*/

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	double calculatePullBackReq(double distanceto200ma )
	{
		//double pips = distanceto200ma / pipSize;
		
		/*
		Integer tradeNum = tradeHistory.get(tradeOrigin);

		if ( tradeNum != null )
		{
			// already stopped before
			return pipSize * 10;
		}
		else
		{
			if ( pips < 30 )
				return distanceto200ma/2;
			
			return pipSize * 12;
			
		}*/
		
		/*
		if ( pips < 20 )
			return distanceto200ma * 0.4;
		else if ( pips < 30 )
			return distanceto200ma * 0.3;
		else
			return distanceto200ma * 0.25;
		*/
		
		return PULLBACK_FACTOR * pipSize;
			
	}
	
	public boolean checkTradeTriggerred(String pos)
	{
			Integer num = tradeHistory.get(pos);
			if ( num == null )
			{
				tradeHistory.put(pos, new Integer(1));
				return false;
			}
			else
			{
				tradeHistory.put(pos, num + 1);
				return true;
			}
	}

	
	int checkDirection(Vector<QuoteData> quotes60M )
	{
		double[] ema200 = indicator.calculateEMA(quotes60M, 200);
		double[] ema20 = indicator.calculateEMA(quotes60M, 20);

		int size = quotes60M.size();
		Object[] quotes = quotes60M.toArray();
		int lastbar = size -1;

		if (( ((QuoteData)quotes[lastbar]).low < ema20[lastbar]) && ( ema20[lastbar] < ema200[lastbar]))
			return Constants.DIRECTION_DOWN;
		else if (( ((QuoteData)quotes[lastbar]).high > ema20[lastbar]) && ( ema20[lastbar] > ema200[lastbar]))
			return Constants.DIRECTION_UP;
		else
			return Constants.DIRECTION_UNKNOWN;

	}
	
	/*
	public Trade calculateBreakoutUp( QuoteData data, Vector<QuoteData> qts, double[] ema200)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).high < ema200[lastbar] )
			return null; // wrong side
		
		// looking where the 200MA breakout starts
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i]).low < ema200[i])
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
		 return null;
		
		logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		
		// Find where the first breakout is
		int pos_breakout = 0;
		for ( int i = pos; i < lastbar; i++)
		{
			if ((((QuoteData)quotes[i]).high - ema200[i]) >  pipSize*BREAKOUT_DISTANCE)
			{
				pos_breakout = i;
				break;
			}
		}
		
		if ( pos_breakout == 0)
			return null;

		logger.info(symbol + " breakout happened at " + ((QuoteData)quotes[pos_breakout]).time );
		
		//continue the breakout until hit a pull backfind a pull back
		while ((pos_breakout < lastbar) && (((QuoteData)quotes[pos_breakout+1]).low >= ((QuoteData)quotes[pos_breakout]).low))
			pos_breakout++;
		
		if ( lastbar == pos_breakout+1 )
		{
			// first pull back - trade found
			if (mostBar(pos, quotes, ema200) != 1 )
			{
				System.out.println("Trade detected BUY for " + symbol + " at " + data.time );
				logger.info("Trade detected BUY for " + symbol + " at " + data.time );
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.action = Constants.ACTION_BUY;
				trade.positionSize = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_SELL, trade.positionSize );
			
				return trade;
			}
			else
			{
				System.out.println("Potential Trade detected BUY for " + symbol + " at " + data.time + " but against trend" );
				logger.info("Potential Trade detected BUY for " + symbol + " at " + data.time + " but against trend" );
			}
		}
		
		return null;
		
	}
	
	public Trade calculateBreakoutDown( QuoteData data, Vector<QuoteData> qts, double[] ema200)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).low > ema200[lastbar] )
			return null; // wrong side
		
		// looking where the 200MA breakout starts
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i]).high > ema200[i])
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
		 return null;
		
		logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		
		// Find where the first breakout is
		int pos_breakout = 0;
		for ( int i = pos; i < lastbar; i++)
		{
			if (ema200[i] - ((QuoteData)quotes[i]).low >  pipSize*BREAKOUT_DISTANCE)
			{
				pos_breakout = i;
				break;
			}
		}
		
		if ( pos_breakout == 0)
			return null;

		logger.info(symbol + " breakout happened at " + ((QuoteData)quotes[pos_breakout]).time );

		//continue the breakout until hit a pull backfind a pull back
		while ((pos_breakout < lastbar) && (((QuoteData)quotes[pos_breakout+1]).high <= ((QuoteData)quotes[pos_breakout]).high))
			pos_breakout++;
		
		if ( lastbar == pos_breakout+1 )
		{
			if (mostBar(pos, quotes, ema200) != -1 )
			{
				// first pull back - trade found
				System.out.println("Trade detected SELL for " + symbol + " at " + data.time );
				logger.info("Trade detected SELL for " + symbol + " at " + data.time );
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.action = Constants.ACTION_SELL;
				trade.positionSize = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
			else
			{
				System.out.println("Trade detected SELL for " + symbol + " at " + data.time + " but against trend, good for reversal");
				logger.info("Trade detected SELL for " + symbol + " at " + data.time + " but against trend, good for reversal");
			}
		}
		
		return null;
		
	}*/

		
	/*
	
	protected  void adjustOpenOrder()
	{
		if (( trade != null ) && ( trade.status.equals(Constants.STATUS_OPEN)))
		{
			if ( trade.stopMarketOrderId != 0 )
				cancelOrder(trade.stopMarketOrderId);
			
			if ( Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopMarketOrderId = tradeManager.placeStopMarketOrder(Constants.ACTION_BUY, quotes.lastElement().high);
			}
			else if ( Constants.ACTION_SELL.equals(trade.action))
			{
				trade.stopMarketOrderId = tradeManager.placeStopMarketOrder(Constants.ACTION_SELL, quotes.lastElement().low);
			}
		}
		
*/

	int mostBar(int pos, Object[] quotes, double[] ema200)
	{
		int numOfBars = 40;
		
		// most bars should be on
		double up = 0;
		double down = 0;
		for ( int i = pos - numOfBars; i <= pos; i++)
		{
			if (((QuoteData)quotes[i]).low > ema200[i])
				up++;
			else if (((QuoteData)quotes[i]).high < ema200[i])
				down++;
		}
		
		if ( up/numOfBars > 0.6 )
			return 1;
		else if ( down/numOfBars > 0.6)
			return -1;
		else
			return 0;
			
	}

	/*
	int detectTrend(int pos, Object[] quotes, double[] ema200)
	{
		double[] breakouts = new double[20];
		int index = 0;
		
		double high = 0;
		double low = 999;
		int prevDir = 0;
		int prevCount = 0;
		for ( int i = pos; i > 0; i--)
		{
			
			if ( ((QuoteData)quotes[i]).low > ema200[i])
			{
				// high
				if (( prevDir == -1 ) && ( prevCount > 2))
				{
					breakouts[index++] = low;
					high = 0;
					low = 999;
					prevDir = 0;
					prevCount = 0;
				}
				
				if (((QuoteData)quotes[i]).high > high )
				{
					high = ((QuoteData)quotes[i]).high;
					prevDir = 1;
					prevCount++;
				}
			}
			else if ( ((QuoteData)quotes[i]).low > ema200[i])
			{
				// low
				if (( prevDir == 1 ) && ( prevCount > 2))
				{
					breakouts[index++] = high;
					high = 0;
					low = 999;
					prevDir = 0;
					prevCount = 0;
				}
				
				if (((QuoteData)quotes[i]).high > high )
				{
					high = ((QuoteData)quotes[i]).high;
				}
				// high
				if (((QuoteData)quotes[i]).low < low )
				{
					low = ((QuoteData)quotes[i]).low;
					prevDir = 1;
					prevCount++;
				}
			}
		}
	}*/
	/*
	
	public Trade calculateBreakoutUp( QuoteData data, Vector<QuoteData> qts, double[] ema200, double[] ema20)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).high < ema200[lastbar] )
			return null; // wrong side
		
		// looking where the 200MA breakout starts
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i]).low < ema200[i])
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
		 return null;
		
		logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		
		// Find where the first breakout is
		int pos_breakout = 0;
		for ( int i = pos; i < lastbar; i++)
		{
			if ((((QuoteData)quotes[i]).high - ema200[i]) >  pipSize*BREAKOUT_DISTANCE)
			{
				pos_breakout = i;
				break;
			}
		}
		
		if ( pos_breakout == 0)
			return null;

		logger.info(symbol + " breakout happened at " + ((QuoteData)quotes[pos_breakout]).time + " at " +  (((QuoteData)quotes[pos_breakout]).high - ema200[pos_breakout])/pipSize);
		
		// wait for a pull back to 20MA
		while ((pos_breakout <= lastbar) && (((QuoteData)quotes[pos_breakout]).low > ema20[pos_breakout]))
			pos_breakout++;
		
		if ((pos_breakout == lastbar) && (((QuoteData)quotes[pos_breakout]).low <= ema20[pos_breakout]))
		{
			System.out.println("Trade detected BUY for " + symbol + " at " + data.time );
			logger.info("Trade detected BUY for " + symbol + " at " + data.time );
			trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
			trade.action = Constants.ACTION_BUY;
			trade.positionSize = positionSize;
			trade.entryPos = lastbar;
			trade.status = Constants.STATUS_OPEN;

			return trade;
		}
		
		return null;
		
	}
	
	
	
	public Trade calculateBreakoutDown( QuoteData data, Vector<QuoteData> qts, double[] ema200, double[] ema20)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).low > ema200[lastbar] )
			return null; // wrong side
		
		// looking where the 200MA breakout starts
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i]).high > ema200[i])
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
		 return null;
		
		//logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		
		// Find where the first breakout is
		int pos_breakout = 0;
		for ( int i = pos; i < lastbar; i++)
		{
			if (ema200[i] - ((QuoteData)quotes[i]).low >  pipSize*BREAKOUT_DISTANCE)
			{
				pos_breakout = i;
				break;
			}
		}
		
		if ( pos_breakout == 0)
			return null;

		logger.info(symbol + " breakout happened at " + ((QuoteData)quotes[pos_breakout]).time +  (ema200[pos_breakout]- ((QuoteData)quotes[pos_breakout]).low)/pipSize);

		while ((pos_breakout <= lastbar) && (((QuoteData)quotes[pos_breakout]).high < ema20[pos_breakout]))
			pos_breakout++;
		
		if ((pos_breakout == lastbar) && (((QuoteData)quotes[pos_breakout]).high >= ema20[pos_breakout]))
		{
			// first pull back - trade found
			System.out.println("Trade detected SELL for " + symbol + " at " + data.time );
			logger.info("Trade detected SELL for " + symbol + " at " + data.time );
			trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
			trade.action = Constants.ACTION_SELL;
			trade.positionSize = positionSize;
			trade.entryPos = lastbar;
			trade.status = Constants.STATUS_OPEN;
			
			// throw in a "test water" reversal order
			//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
			return trade;
		}
		
		return null;
		
	}
*/

	
	
	public Trade calculateTopReversal( QuoteData data, Vector<QuoteData> qts, double[] ema20, int dist, int pullback )
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).low > ema20[lastbar] )
			return null; // wrong side
		
		// looking where the 20MA breakout starts
		// to consider to be other side, one has to close there 2+ bars
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if ((((QuoteData)quotes[i-1]).high < ema20[i-1]) && (((QuoteData)quotes[i-2]).high < ema20[i-2])
				&&(((QuoteData)quotes[i-3]).high < ema20[i-3]) && (((QuoteData)quotes[i-4]).high < ema20[i-4]))		
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
			return null;
		
		//logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
//		int STRETCH_DIST;
		//int STRETCH_PULLBACK;

		Cup cup = Pattern.upCup(qts, ema20, pos, STRETCH_DIST*pipSize, STRETCH_PULLBACK * pipSize, 9999*pipSize);  // min 40 pips pull back
		
		if ( cup != null )
		{	
			// it needs at least one big move
			if ( verify20MAMovesUp( quotes, ema20, pos, lastbar, 10 ) == true)
			{
				System.out.println("Trade detected SELL for " + symbol + " at " + data.time );
				logger.info("Trade detected SELL for " + symbol + " at " + data.time );
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
		}
		
		return null;
	}

	
	public Trade calculateTopReversal( QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] ema200, int dist, int pullback )
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).low > ema200[lastbar] )
			return null; // wrong side
		
		// looking where the 200MA breakout starts
		// to consider to be other side, one has to close there 2+ bars
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i-1]).high < ema200[i-1])  
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
			return null;
		
		//logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		// we are looking for "big" pull backs, the bigger the pull back, the safer, it is
		Cup cup = Pattern.upCup(qts, ema20, pos, STRETCH_DIST*pipSize, STRETCH_PULLBACK * pipSize, 9999*pipSize);  // min 40 pips pull back
		
		if ( cup != null )
		{	
			// it needs at least one big move
			//if ( verify20MAMovesUp( quotes, ema20, pos, lastbar, 10 ) == true)
			{
				System.out.println("Stretch trade detected SELL for " + symbol + " at " + data.time );
				logger.info("Stretch trade detected SELL for " + symbol + " at " + data.time );
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
		}
		
		return null;
	}

	
	
	
	public Trade calculateDownReversal( QuoteData data, Vector<QuoteData> qts, double[] ema20, int dist, int pullback )
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).high > ema20[lastbar] )
			return null; // wrong side
		
		// looking where the 20MA breakout starts
		// to consider to be other side, one has to close there 2+ bars
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if ((((QuoteData)quotes[i-1]).low > ema20[i-1]) && (((QuoteData)quotes[i-2]).low < ema20[i-2])
			&& (((QuoteData)quotes[i-3]).low < ema20[i-3]) && (((QuoteData)quotes[i-4]).low < ema20[i-4])) 
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
			return null;
		
		//logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
//	int STRETCH_DIST;
		//int STRETCH_PULLBACK;

		Cup cup = Pattern.downCup(qts, ema20, pos, STRETCH_DIST*pipSize, STRETCH_PULLBACK * pipSize, 9999*pipSize);  // min 40 pips pull back
		
		if ( cup != null )
		{	
			// it needs at least one big move
			if ( verify20MAMovesDown( quotes, ema20, pos, lastbar, 10 ) == true)
			{
				System.out.println("Trade detected BUY for " + symbol + " at " + data.time );
				logger.info("Trade detected BUY for " + symbol + " at " + data.time );
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
		}
		
		return null;
	}

	
	public Trade calculateDownReversal( QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] ema200, int dist, int pullback )
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (((QuoteData)quotes[lastbar]).high > ema200[lastbar] )
			return null; // wrong side
		
		// looking where the 20MA breakout starts
		// to consider to be other side, one has to close there 2+ bars
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i-1]).low > ema200[i-1]) 
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
			return null;
		
		//logger.info(symbol + " 200MA crossing happened at " + ((QuoteData)quotes[pos]).time );
//	int STRETCH_DIST;
		//int STRETCH_PULLBACK;

		// we are looking for big pull backs, the bigger the pull back, the safer it is
		Cup cup = Pattern.downCup(qts, ema20, pos, STRETCH_DIST*pipSize, STRETCH_PULLBACK * pipSize, 9999*pipSize);  // min 40 pips pull back
		
		if ( cup != null )
		{	
			// it needs at least one big move
			//if ( verify20MAMovesDown( quotes, ema20, pos, lastbar, 10 ) == true)
			{
				System.out.println("Stretch trade detected BUY for " + symbol + " at " + data.time );
				logger.info("Stretch trade detected BUY for " + symbol + " at " + data.time );
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
		}
		
		return null;
	}



	
	
	
	boolean	verify20MAMovesUp( Object[] quotes, double[] ema20, int start, int end, int consec)
	{
		for ( int i = start; i <= end - consec; i++ )
		{
			boolean touch = false;
			for ( int j = i; j < i + consec; j++)
			{
				if (((QuoteData)quotes[j]).low <= ema20[j])
					touch = true;
			}
			if ( touch == false) 
				return true;
		}
		
		return false;
	}
	boolean	verify20MAMovesDown( Object[] quotes, double[] ema20, int start, int end, int consec)
	{
		for ( int i = start; i <= end - consec; i++ )
		{
			boolean touch = false;
			for ( int j = i; j < i + consec; j++)
			{
				if (((QuoteData)quotes[j]).high >= ema20[j])
					touch = true;
			}
			if ( touch == false) 
				return true;
		}
		
		return false;
	}

	

	
	
	
	
	
	
	
	
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	
	
	
	public Trade checking20MASetups( QuoteData data, Vector<QuoteData> qts, double[] ema20, double[] ema200 )
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		
		if ( Math.abs((((QuoteData)quotes[lastbar]).close - ema200[lastbar])) > TRIGGER_DIST * pipSize )
		{	
			// check for 2 types of shorts
			// 1.top of the 200MA, with extention
			// 2.First pullback after it finishes below 20MA  this is if it missed the first
		
			if (((QuoteData)quotes[lastbar]).low > ema20[lastbar] )
				return check20MAStretchUp(data, quotes, ema20, ema200);
			else if (((QuoteData)quotes[lastbar]).high < ema20[lastbar] )
				return check20MAStretchDown(data, quotes, ema20, ema200);
				//return calculateDownReversal( data, qts, ema20, 0, 0 );
			else 
				return check20MAPullBack(data, quotes, ema20, ema200);
		}
		
		return null;
	}
		

	
	// this is to check the stretch top on a 20MA
	public Trade check20MAStretchUp( QuoteData data, Object[] quotes, double[] ema20, double[] ema200 )
	{
		int lastbar = quotes.length-1;

		// looking where the 20MA breakout starts
		// to consider to be other side, one has to close there 5+ bars
		int pos = findPriceCross20MAUp(quotes, ema20);
		
		if (pos == -1)
			return null;
		
		logger.info(symbol + " 20MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		Cup cup = Pattern.upCup20MA(quotes, ema20, pos, STRETCH_DIST*pipSize, STRETCH_DIST * pipSize, STRETCH_DIST * pipSize);  
		
		if ( cup != null )
		{	
			logger.info(symbol + " up cup detected at " + data.time + "pull back:" + ((QuoteData)quotes[cup.pullBackPos]).time + " pullback size:" + cup.pullBackSize/pipSize + " previous High:" + cup.lastHighLow);
			
			// calculate sochastic
			
			double[] so = Indicator.calculateStochastics( quotes, 14 );
			
			if (so[lastbar] < so[cup.lastHighLowPos])
			// it needs at least one big move
			//if ( verify20MAStretchUpSequence( quotes, ema20, ema200, pos, lastbar ) == true)
			{
				System.out.println("Stretch Trade detected SELL for " + symbol + " at " + data.time + "lastHigh is " + ((QuoteData)quotes[cup.lastHighLowPos]).time + " " + cup.lastHighLow + " s:" + so[cup.lastHighLowPos] + " s" + so[lastbar]);
				logger.info("Stretch Trade detected SELL for " + symbol + " at " + data.time + "lastHigh is " + ((QuoteData)quotes[cup.lastHighLowPos]).time + " " + cup.lastHighLow + " s:" + so[cup.lastHighLowPos] + " s" + so[lastbar]);
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.type = Constants.TRADE_STRETCH;
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
		}
		
		return null;
	}
	

	// this is to check the stretch top on a 20MA
	public Trade check20MAStretchDown( QuoteData data, Object[] quotes, double[] ema20, double[] ema200 )
	{
		int lastbar = quotes.length-1;

		// looking where the 20MA breakout starts
		// to consider to be other side, one has to close there 5+ bars
		int pos = findPriceCross20MADown(quotes, ema20);
		
		if (pos == -1)
			return null;
		
		logger.info(symbol + " 20MA crossing happened at " + ((QuoteData)quotes[pos]).time );
		Cup cup = Pattern.downCup20MA(quotes, ema20, pos, STRETCH_DIST*pipSize, STRETCH_DIST*pipSize * pipSize, STRETCH_DIST * pipSize);  
		
		if ( cup != null )
		{	
			logger.info(symbol + " down cup detected at " + data.time + "pull back:" + ((QuoteData)quotes[cup.pullBackPos]).time + " pullback size:" + cup.pullBackSize/pipSize + " previous High:" + cup.lastHighLow);

			double[] so = Indicator.calculateStochastics( quotes, 14 );
			
			if (so[lastbar] > so[cup.lastHighLowPos])
			// it needs at least one big move
			//if ( verify20MAStretchDownSequence( quotes, ema20, ema200, pos, lastbar ) == true)
			{
				System.out.println("Stretch Trade detected BUY for " + symbol + " at " + data.time + "lastHigh is " + ((QuoteData)quotes[cup.lastHighLowPos]).time + " " + cup.lastHighLow + " s:" + so[cup.lastHighLowPos] + " s" + so[lastbar]);
				logger.info("Stretch Trade detected BUY for " + symbol + " at " + data.time + "lastHigh is " + ((QuoteData)quotes[cup.lastHighLowPos]).time + " " + cup.lastHighLow + " s:" + so[cup.lastHighLowPos] + " s" + so[lastbar]);
				trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
				trade.type = Constants.TRADE_STRETCH;
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
			
				// throw in a "test water" reversal order
				//trade.orderId = placeMktOrder( Constants.ACTION_BUY, trade.positionSize );
			
				return trade;
			}
		}
		
		return null;
	}

	
	
	public Trade check20MAPullBack( QuoteData data, Object[] quotes, double[] ema20, double[] ema200 )
	{
		int lastbar = quotes.length-1;

		if ((((QuoteData) quotes[lastbar-1]).low > ema20[lastbar-1]) && (((QuoteData) quotes[lastbar-2]).low > ema20[lastbar-2]))
		{	
			// this is a pull back from upside;
			// exam whether this is the first or second pull back
				
			if ( ifFirstSecondPullBackUp(quotes, ema20)== true)
			{
				// this is a pull back
				//if (verifyTrade( quotes, ema20, ema200 ) == true )
				System.out.println("Pullback Trade Found BUY for " + symbol + " at " + data.time );
				logger.info("Pullback Trade Found BUY for " + symbol + " at " + data.time );
				trade = new Trade(symbol);
				trade.type = Constants.TRADE_PULLBACK;
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = positionSize;
				trade.entryPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
				//trade.addEntryQuotes(lastQuote);
					
				// calculate Target
				return trade;
			}
			else
			{
				//System.out.println("Pullback Trade Found BUY for " + symbol + " at " + data.time + " but more than 3 times pull back" );
				logger.info("Pullback Trade Found BUY for " + symbol + " at " + " but more than 3 times pull back" );
			}
		}
		if ((((QuoteData) quotes[lastbar-1]).high < ema20[lastbar-1]) && (((QuoteData) quotes[lastbar-2]).high < ema20[lastbar-2]))
		{	
			// this is a pull back from downside
			if ( ifFirstSecondPullBackDown(quotes, ema20 )== true)
			//if (verifyTrade( quotes, ema20, ema200 ) == true )
			{
				System.out.println("Pulback Trade Found SELL for " + symbol + " at " + data.time );
				logger.info("Pullback Trade Found SELL for " + symbol + " at " + data.time );
				trade = new Trade(symbol);
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = positionSize;
				trade.triggerPos = lastbar;
				trade.status = Constants.STATUS_OPEN;
				//trade.addEntryQuotes(lastQuote);
					
				// calculate Target
				return trade;
			}
			else
			{
				//System.out.println("Pullback Trade Found SELL for " + symbol + " at " + data.time + " but more than 3 times pull back" );
				logger.info("Pullback Trade Found SELL for " + symbol + " at " + " but more than 3 times pull back" );
					
			}
		}
		
		return null;

	
	}
	
	
	
	int findPriceCross20MAUp(Object[] quotes, double[] ema20)
	{
		int lastbar = quotes.length -1;
		
		T:for ( int i = lastbar-1; i >=0; i-- )
		{
			// needs to have 10 bar in the opposite trend to avoid noise
			for ( int j = i; j > i-10; j--)
			{
				if (((QuoteData)quotes[j]).high >= ema20[j])
					continue T;
			}
			return i;
		}
		
		return -1;
	}

	int findPriceCross20MADown(Object[] quotes, double[] ema20)
	{
		int lastbar = quotes.length -1;
		
		// needs to have 10 bar in the opposite trend to avoid noise
		T:for ( int i = lastbar-1; i >=0; i-- )
		{
			for ( int j = i; j > i-10; j--)
			{
				if (((QuoteData)quotes[j]).low <= ema20[j])
					continue T;
			}
			return i;
		}
		
		return -1;
	}

	
	boolean ifFirstSecondPullBackUp(Object[] quotes, double[] ema20 )
	{
		int lastbar = quotes.length-1;
		int pos = findPriceCross20MAUp(quotes, ema20);
		logger.info(symbol + " 20MA cross up at " + ((QuoteData)quotes[pos]).time);
		
		Vector breakouts = find20MABreakoutUps(quotes, ema20, pos);
		
		int sum = 0;
		Iterator it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			if ( bo.width > PULLBACK_WIDTH)
				sum++;
		}

		logger.info(symbol + sum + " break outs up found");
		
		if ( sum > 2 )
			return false;
		else
			return true;
			
	}

	
	boolean ifFirstSecondPullBackDown(Object[] quotes, double[] ema20 )
	{
		int lastbar = quotes.length-1;
		int pos = findPriceCross20MADown(quotes, ema20);
		logger.info(symbol + " 20MA cross down at " + ((QuoteData)quotes[pos]).time);

		Vector breakouts = find20MABreakoutDowns(quotes, ema20, pos);
		
		int sum = 0;
		Iterator it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			if ( bo.width > PULLBACK_WIDTH)
				sum++;
		}
		
		logger.info(symbol + sum + " break outs down found");

		if ( sum > 2 )
			return false;
		else
			return true;
			
	}

	
	boolean	verify20MAStretchUpSequence( Object[] quotes, double[] ema20, double[] ema200, int start, int end)
	{
		// has to wait for 1 pull back if meets either width or heigh
		// no need to wait for 1 pull back if meets both width and height
		Vector<BreakOut> breakouts = find20MABreakoutUps(quotes, ema20, start);
		
		/*BreakOut last = (BreakOut)breakouts.lastElement();
		if ( last.below > 1 * pipSize )
		{
			logger.info(symbol + " last breakout up is no less than 1 pip in pull back");
			return false;		// it is prefered to have 		
		}*/
		
		
		/*
		Iterator it = breaks.iterator();
		while(it.hasNext()) {

		    Break element = (Break)it.next();
		    logger.info("Stretches: " + element.width + " " + element.height);
		    if ( element.width <= 3 )		// remove the breakouts that last less than 3 bars
		    {
		    	it.remove();
			    logger.info("Above stretch removed");
		    }
		} */
		
		int size = breakouts.size();
		logger.info(symbol + " " + size + " breakouts up");
		
		// it needs to be at least two stretches
		if ( size >= 2 )
		{
			// for width to qualify, it needs to be 
			for (int i = 0; i < size -1; i++ )
			{
				BreakOut b = breakouts.elementAt(i);
				if ( b.width > STRETCH_SETUP_WIDTH ) 
				{
					return true;
				}
			}
		}

		logger.info( symbol + " no breakouts up is more than " + STRETCH_SETUP_WIDTH + "wide");
		return false;
	}
	
	boolean	verify20MAStretchDownSequence( Object[] quotes, double[] ema20, double[] ema200, int start, int end)
	{
		// has to wait for 1 pull back if meets either width or heigh
		// no need to wait for 1 pull back if meets both width and height
		Vector<BreakOut> breakouts = find20MABreakoutDowns(quotes, ema20, start);
		
		/*BreakOut last = (BreakOut)breakouts.lastElement();
		if ( last.below > 1 * pipSize )
		{
			logger.info(symbol + " last breakout down is no less than 1 pip in pull back");
			return false;		// it is prefered to have
		}*/
		
		
		/*
		Iterator it = breaks.iterator();
		while(it.hasNext()) {

		    Break element = (Break)it.next();
		    logger.info("Stretches: " + element.width + " " + element.height);
		    if ( element.width <= 3 )		// remove the breakouts that last less than 3 bars
		    {
		    	it.remove();
			    logger.info("Above stretch removed");
		    }
		} */
		
		int size = breakouts.size();

		logger.info(symbol + size + " breakouts down");
		
		// it needs to be at least two stretches
		if ( size >= 2 )
		{
			// for width to qualify, it needs to be 
			for (int i = 0; i < size -1; i++ )
			{
				BreakOut b = breakouts.elementAt(i);
				if ( b.width > STRETCH_SETUP_WIDTH ) 
				{
					return true;
				}
			}
		}

		logger.info( symbol + " no breakouts down is more than " + STRETCH_SETUP_WIDTH + "wide");
		return false;
	}

	
	
    Vector<BreakOut> find20MABreakoutUps(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
    	
		Vector<BreakOut> breakouts = new Vector();
		int i = start;
		while ( i < lastbar ) 
		{
			double b = 0;
			while ((i <= lastbar) &&((QuoteData)quotes[i]).low <= ema20[i])
			{
				if ((((QuoteData)quotes[i]).low - ema20[i]) < b)
					b = ((QuoteData)quotes[i]).low - ema20[i];
				i++;
			}

			int w = 0;
			double h = 0;
			while ((i <= lastbar) &&((QuoteData)quotes[i]).low > ema20[i])
			{
				w++;
				if ((((QuoteData)quotes[i]).low - ema20[i] ) > h )
					h = ((QuoteData)quotes[i]).low - ema20[i];

				i++;
			}

			BreakOut bo = new BreakOut(b, w, h);
			
			breakouts.add(bo);	
			
		}
		
		return breakouts;

    }

    Vector<BreakOut> find20MABreakoutDowns(Object[] quotes, double[] ema20, int start)
    {
    	int lastbar = quotes.length-1;
    	
		Vector<BreakOut> breakouts = new Vector();
		int i = start;
		while ( i < lastbar ) 
		{
			double b = 0;
			while ((i <= lastbar) && (((QuoteData)quotes[i]).high >= ema20[i]))
			{
				if (( ema20[i] - ((QuoteData)quotes[i]).high) < b)
					b = ( ema20[i] - ((QuoteData)quotes[i]).high);
				i++;
			}

			int w = 0;
			double h = 0;
			while ((i <= lastbar) && ((QuoteData)quotes[i]).high < ema20[i])
			{
				w++;
				if (( ema20[i] - ((QuoteData)quotes[i]).high ) > h )
					h = ema20[i] - ((QuoteData)quotes[i]).high;

				i++;
			}

			BreakOut bo = new BreakOut(b, w, h);
			
			breakouts.add(bo);	
			
		}
		
		return breakouts;

    }


}
