package tao.trading.strategy;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.Cup;
import tao.trading.Indicator;
import tao.trading.Pattern;
import tao.trading.Peak;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.TradeOrder;
import com.ib.client.TradeRecord;

import java.util.Arrays;



public class SF108TradeManager extends TradeManager2 implements Serializable
{
	HashMap<String, QuoteData> qts = new HashMap<String, QuoteData>(1000);
	boolean CNT= false; 
	int TARGET_PROFIT_SIZE;
	boolean oneMinutChart = false;
	int POSITION_SIZE;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	boolean test = false;
	boolean larger_timeframe_restriction = false;
	boolean reverse_trade = false;// true
	boolean rsc_trade = false;
	int QUICK_PROFIT = 25;
	int LOCKIN_PROFIT = 25;
	int SHALLOW_PROFIT_REVERSAL = 40;
	protected SF108TradeManager largerTimeFrameTraderManager;
	boolean timeUp = false;
	
	public SF108TradeManager()
	{
		super();
	}
	
	public SF108TradeManager(String account, EClientSocket client, Contract contract, int symbolIndex,  Logger logger)
	{
		super( account, client, contract, symbolIndex, logger );
	}

	public Object[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
	    Arrays.sort(quotes);
	    return quotes;
	}

	public boolean inTradingTime( int hour, int min )
	{
		//return true;
	
		if ( timeUp )
			return false;
		
		int minute = hour * 60 + min;
		if (( minute >= 90) && (minute <= 420))//600
									return true;
								else
									return false;		
		/*
		switch ( TIME_ZONE)
		{
			case 23:  	if (( minute >= 90) && (minute <= 420))//600
							return true;
						else
							return false;
			
			case 12:  	if ((minute >= 1080) || (minute <= 420))//600
							return true;
						else
							return false;
			
			default:	return false;

		}*/
	}

	
	///////////////////////////////////////////////////////////
	//
	//  Check Stop
	//
	///////////////////////////////////////////////////////////
	public void checkOrderFilled(int orderId, int filled)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length -1;
		
		if ( trade != null )
		{	
			/*
			if ( orderId == trade.orderId  )
			{
				trade.remainingPositionSize += filled;
				trade.status = Constants.STATUS_PLACED;
				trade.orderPlaced = false;
			}*/
			if ( orderId == trade.stopId )
			{
				logger.warning(symbol + " " + CHART + " minute " + filled + " stopped out " );
				trade.stoppedOutPos = ((QuoteData)quotes[lastbar]).pos;
				trade.stopId = 0;
					
				cancelTarget();

				processAfterHitStopLogic_c();

			}
			else if ( orderId == trade.targetId )  
			{
				logger.warning(symbol + " " + CHART + " minute target filled, " + filled + " closed @ " + trade.targetPrice);
				trade.targetReached = true;
				trade.remainingPositionSize -= filled;
				trade.targetId = 0;
					
				cancelOrder( trade.stopId );
				logger.warning(symbol + " " + CHART + " remainning position is " + trade.remainingPositionSize);
				if ( trade.remainingPositionSize > 0 )
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else  	
				{
					logger.warning(symbol + " " + CHART + " remove trade");
					trade = null;
				}
				
			}
			else if (( orderId == trade.takeProfit1Id ) || ( orderId == trade.takeProfit2Id )) 
			{
				logger.warning(symbol + CHART + " take profit filled ");
				trade.targetReached = true;
				trade.remainingPositionSize -= filled;
					
				cancelOrder( trade.stopId );
				if ( trade.remainingPositionSize > 0 )
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
			}
		}
	}
	
	
	public void checkStopTarget(QuoteData data)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (( trade.stop != 0 ) && (data.high > trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out @ data.high:" + data.high + " " + data.time );
				trade.stoppedOutPos = lastbar;
				AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				//placeMktOrder( Constants.ACTION_BUY, trade.remainingPositionSize);
				trade.preStopPrice = trade.stop;
				//trade.stop = 0;
				trade.status = Constants.STATUS_STOPPEDOUT;
				
				cancelTarget();

				processAfterHitStopLogic_c();
				return;
			}
		
			// check target;
			if ((trade.targetPrice != 0) && ( data.low < trade.targetPrice ))
			{
				logger.info(symbol + " target hit, close " + trade.targetPositionSize + " @ " + trade.targetPrice);
				AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.targetPositionSize, trade.targetPrice);
				//closePositionByMarket( trade.targetPositionSize, trade.targetPrice);
				
				trade.remainingPositionSize -= trade.targetPositionSize;
				if ( trade.remainingPositionSize <= 0 )
				{
					tradeHistory.add(trade);
					this.trade = null;
				}
				else
				{
					trade.targetReached = true;
					trade.targetPrice = 0;
				}
			}
			
			/*
			if ((trade != null )&&(trade.takeProfit2Price != 0) && ( data.low < trade.takeProfit2Price ))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PositionSize + " @ " + trade.takeProfit2Price);
				AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.takeProfit2PositionSize, trade.takeProfit2Price );
				closePositionByMarket( trade.targetPositionSize, trade.takeProfit2Price );
				// should have everything closed
			}*/
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (( trade.stop != 0 ) && (data.low < trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out data low:" + data.low + " " + data.time );
				trade.stoppedOutPos = lastbar;
				AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				//placeMktOrder( Constants.ACTION_SELL, trade.remainingPositionSize);
				trade.preStopPrice = trade.stop;
				//trade.stop = 0;
				trade.status = Constants.STATUS_STOPPEDOUT;
				
				cancelTarget();

				processAfterHitStopLogic_c();
				return;
			}
				
			if ((trade.targetPrice != 0) && ( data.high > trade.targetPrice ))
			{
				logger.info(symbol + " target hit, close " + trade.targetPositionSize + " @ " + trade.targetPrice);
				AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.targetPositionSize, trade.targetPrice);
				//this.closePositionByMarket( trade.targetPositionSize, trade.targetPrice);
				trade.remainingPositionSize -= trade.targetPositionSize;
				if ( trade.remainingPositionSize <= 0 )
				{
					tradeHistory.add(trade);
					this.trade = null;
				}
				else
				{
					trade.targetReached = true;
					trade.targetPrice = 0;
				}
			}

			/*
			if ((trade != null )&&(trade.takeProfit2Price != 0) && ( data.high > trade.takeProfit2Price ))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PositionSize + " @ " + trade.takeProfit2Price);
				AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.takeProfit2PositionSize, trade.takeProfit2Price);
				closePositionByMarket( trade.targetPositionSize, trade.takeProfit2Price);
				// should have everything closed
			}*/

		}
	}

	public void cancelTarget()
	{
		if ( MODE == Constants.REAL_MODE )	
		{
			if (trade.targetId != 0 )
			{
				cancelOrder( trade.targetId);
				trade.targetId = 0;
			}
		}
		else if ( MODE == Constants.TEST_MODE )
		{
			trade.targetPrice = 0;
			trade.targetPositionSize = 0;
		}
	}

	public void cancelStop()
	{
		if ( MODE == Constants.REAL_MODE )	
		{
			if (trade.stopId != 0 )
			{
				cancelOrder( trade.stopId);
				trade.stopId = 0;
			}
		}
		else if ( MODE == Constants.TEST_MODE )
		{
			trade.stop = 0;
		}
	}
	

	
	
	void processAfterHitStopLogic_c()
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length -1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// reverse criteria 1:  no big pull backs during the trigger, > lastPullBack
			// reverse criteria 2:  shallow pull back
			if((( trade.type == Constants.TRADE_RST ) || ( trade.type == Constants.TRADE_RSC )) /*&& ( trade.entryResets == 0 )*/&& ( reverse_trade == true ))
			{
				QuoteData lowAfterEntry = Utility.getLow( quotes, trade.entryPos, lastbar);
				//QuoteData highAfterDetect = Utility.getHigh( quotes, trade.detectPos, lastbar);
				
				// need to have a low that is higher for not breaking the trend
				// if sochastic is < 80, shall we re-enter instead of reverse?
				//double[] k = Indicator.calculateStochastics( quotes, 14 );

				if (( lowAfterEntry.low > ((QuoteData)quotes[trade.pullBackPos]).low))
				//if ( k[lastbar] > 80 )
				{
					trade.type = Constants.TRADE_CNT;
					trade.action = Constants.ACTION_BUY;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.lowHighAfterEntry = lowAfterEntry;
						
					logger.warning(symbol + " " + CHART + " place market sell reversal order");
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE );
					trade.price = trade.stop;
					trade.entryPrice = trade.price;
					trade.status = Constants.STATUS_PLACED;
					trade.remainingPositionSize=trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData)quotes[lastbar]).time;
					trade.entryPos = lastbar;
					trade.profitTakeStartPos = lowAfterEntry.pos;

					if ( MODE == Constants.TEST_MODE )
						AddTradeOpenRecord( trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.entryPrice);

					trade.stop = lowAfterEntry.low;
					if (( trade.entryPrice - trade.stop ) > FIXED_STOP * PIP_SIZE )
						trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
					
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
					
					logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

					// calculate and place quick profit target order
					/*
					trade.targetPrice = trade.price + QUICK_PROFIT * PIP_SIZE;
					trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
					trade.targetPositionSize = trade.positionSize/2;
					logger.warning(symbol + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					*/
					return;
				}
				else
				{
					logger.warning(symbol + " " + CHART + " stopped out but does not look like reversal, reversal trade not placed");
					//trade.reEnter--;
					//if ( trade.reEnter >= 0 )
					//	return;
				}
			}

		}
		else if (Constants.ACTION_BUY.equals(trade.action)) 
		{
			// reverse criteria 1:  no big pull backs during the trigger, > lastPullBack
			// reverse criteria 2:  shallow pull back
			if((( trade.type == Constants.TRADE_RST ) || ( trade.type == Constants.TRADE_RSC ))/* &&  ( trade.entryResets == 0 )*/ && ( reverse_trade == true ))
			//		int pushs = findNumLows( quotes, trade.pullBackPos, trade.entryPos, 3 );
			{
				QuoteData highAfterEntry = Utility.getHigh( quotes, trade.entryPos, lastbar);
				//QuoteData lowAfterDetect = Utility.getLow( quotes, trade.detectPos, lastbar);

				//double[] k = Indicator.calculateStochastics( quotes, 14 );
					
				if (( highAfterEntry.high < ((QuoteData)quotes[trade.pullBackPos]).high))
					//&& ( highAfterEntry.high - lowAfterDetect.low < 15* PIP_SIZE))
				//if ( k[lastbar] < 20)
				{
					trade.type = Constants.TRADE_CNT;
					trade.action = Constants.ACTION_SELL;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.lowHighAfterEntry = highAfterEntry;
						
					logger.warning(symbol + " " + CHART + " place market reverse sell order");
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE );
					trade.price = trade.stop;
					trade.entryPrice = trade.price;
					trade.status = Constants.STATUS_PLACED;
					trade.remainingPositionSize=trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData)quotes[lastbar]).time;
					trade.entryPos = lastbar;
					trade.profitTakeStartPos = highAfterEntry.pos;
					
					if ( MODE == Constants.TEST_MODE )
						AddTradeOpenRecord( trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.entryPrice);

					trade.stop = highAfterEntry.high;
					if ((trade.stop -  trade.entryPrice ) > FIXED_STOP * PIP_SIZE )
						trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;
					
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);

					logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

					// calculate and place target order for quick profits
					/*trade.targetPrice = trade.price - QUICK_PROFIT * PIP_SIZE;
					trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
					trade.targetPositionSize = trade.positionSize/2;
					logger.warning(symbol + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					*/
					return;
				}
				else
				{
					logger.warning(symbol + " " + CHART + " stopped out but does not look like reversal, reversal trade not placed");
					//trade.reEnter--;
					//if ( trade.reEnter >= 0 )
					//	return;
				}
			}
		}

		tradeHistory.add(trade);
	    this.trade = null;
	
	}

	

	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	//	Entry Setups
	//
	//
	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public Trade checkHigherHighSetup( QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS )
	{
		return checkRSTSetup( data, minCrossBar, quotesL, quotesS );
	}
	

	public Trade checkRSTSetup( QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS )
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		int SecondWidth = 2;
		int FirstWidth = 1;
		int BufferWidth = 5;
		
		//int upPos = Pattern.findLastMAConsectiveUp(ema20, ema50, 10);
		//int downPos = Pattern.findLastMAConsectiveDown(ema20, ema50, 10);
		
		if (((QuoteData)quotes[lastbar]).low > ema20[lastbar] )
		{
			int crossUp = Pattern.findPriceCross20MAUp(quotes, ema20, 3);
			int crossDown = Pattern.findPriceCross20MADown(quotes, ema20, crossUp, 3);
			
			if ((crossUp == Constants.NOT_FOUND ) || (crossDown == Constants.NOT_FOUND))
				return null;
					
			int maCrossOverPos = Utility.getLow(quotes, crossDown, crossUp).pos;
			
			if ( maCrossOverPos == Constants.NOT_FOUND )
				return null;

			PushHighLow phl_cur = findLastNHigh( quotes, maCrossOverPos, lastbar, SecondWidth );
			if ( phl_cur == null )
				return null;
			
			int preHigh = phl_cur.prePos;
			for ( int i = preHigh; i > maCrossOverPos - BufferWidth; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotes, maCrossOverPos - BufferWidth, i, FirstWidth );
				if ( phl_pre != null )
				{
					if (( phl_cur.pullBack.low < phl_pre.pullBack.low) && (((QuoteData)quotes[phl_cur.curPos]).high > ((QuoteData)quotes[phl_pre.curPos]).high ))       
					{	
						// last check large data chart
						trade = new Trade(symbol);
						double entryQualifyPrice = ((QuoteData)quotes[phl_cur.prePos]).high;
						
						if (ema20L[lastbarL] > ema50L[lastbarL])
						{
							//int maCrossOverPosL = Pattern.findLastMACrossUp( ema20L, ema50L, 20 );
							//if ( maCrossOverPosL == Constants.NOT_FOUND )
							//	maCrossOverPosL = lastbarL-16;
							//double high = Utility.getHigh(quotesL, maCrossOverPosL, lastbarL).high;
							double high = Utility.getHigh(quotesL, lastbarL-16, lastbarL).high;
							if ( high > entryQualifyPrice )
								entryQualifyPrice = high;
						}
						
						logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );
						logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " + ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" + phl_cur.pullBack ); 
						logger.warning(symbol + " " + CHART + "last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@" + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotes[phl_pre.curPos]).time + "@" + ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 
						logger.warning(symbol + " " + CHART + " entry Qualify Price is " + entryQualifyPrice );

						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.detectPrice = ((QuoteData)quotes[phl_pre.curPos]).high;
						trade.detectPos = lastbar;
						trade.prePos = phl_cur.prePos;
						trade.pullBackPos = phl_cur.pullBack.pos;
						trade.status = Constants.STATUS_OPEN;
						trade.entryQualifyPrice = entryQualifyPrice;
						trade.entryQualifyPricePos = phl_cur.curPos;//lastbarS;//;
						trade.reEnter = 1;
						return trade;
					}
					return null;
				}
			}
		}
		else if (((QuoteData)quotes[lastbar]).high < ema20[lastbar] )
		{
			int crossDown = Pattern.findPriceCross20MADown(quotes, ema20, 3);
			int crossUp = Pattern.findPriceCross20MAUp(quotes, ema20, crossDown, 3);
			
			if ((crossUp == Constants.NOT_FOUND ) || (crossDown == Constants.NOT_FOUND))
				return null;
					
			int maCrossOverPos = Utility.getHigh(quotes, crossUp, crossDown).pos;
			
			if ( maCrossOverPos == Constants.NOT_FOUND )
				return null;

			PushHighLow phl_cur = findLastNLow( quotes, maCrossOverPos, lastbar, SecondWidth );
			if ( phl_cur == null )
				return null;
	
			int preLow = phl_cur.prePos;
			for ( int i = preLow; i > maCrossOverPos - BufferWidth; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotes, maCrossOverPos - BufferWidth, i, FirstWidth );
				if ( phl_pre != null )
				{
					if (( phl_cur.pullBack.high > phl_pre.pullBack.high) && (((QuoteData)quotes[phl_cur.curPos]).low < ((QuoteData)quotes[phl_pre.curPos]).low ))
					{	
						trade = new Trade(symbol);
						double entryQualifyPrice = ((QuoteData)quotes[phl_cur.prePos]).low;

						if (ema20L[lastbarL] < ema50L[lastbarL])
						{
							//int maCrossOverPosL = Pattern.findLastMACrossDown( ema20L, ema50L, 20 );
							//if ( maCrossOverPosL == Constants.NOT_FOUND )
							//	maCrossOverPosL = lastbarL-16;
							//double low = Utility.getLow(quotesL, maCrossOverPosL, lastbarL).low;
							double low = Utility.getLow(quotesL, lastbarL-16, lastbarL).low;
							if ( low < entryQualifyPrice )
								entryQualifyPrice = low;
						}

						logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );
						logger.warning(symbol + " " + CHART + " this low between " + ((QuoteData)quotes[phl_cur.prePos]).time + "-" + ((QuoteData)quotes[phl_cur.curPos]).time + " pullback@" + phl_cur.pullBack ); 
						logger.warning(symbol + " " + CHART + " last low between " + ((QuoteData)quotes[phl_pre.prePos]).time + "-" + ((QuoteData)quotes[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 
						logger.warning(symbol + " " + CHART + " entry Qualify Price is " + entryQualifyPrice );

						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.detectPrice = ((QuoteData)quotes[phl_pre.curPos]).low;
						trade.detectPos = lastbar;
						trade.prePos = phl_cur.prePos;
						trade.pullBackPos = phl_cur.pullBack.pos;
						trade.status = Constants.STATUS_OPEN;
						trade.entryQualifyPrice = ((QuoteData)quotes[phl_cur.prePos]).low;
						trade.entryQualifyPricePos = phl_cur.curPos;//lastbarS;//phl_cur.curPos;
						trade.reEnter = 1;
						return trade;
					}
					return null;
				}
			}
		}

		return null;
		
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	//   Trade Entry
	//
	//
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackTradeEntry(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		if ( Constants.TRADE_RST.equals(trade.type))
			trackRSTTradeEntry(data, quotesS, quotesL);
		else if ( Constants.TRADE_RSC.equals(trade.type))
			trackRSCTradeEntry(data, quotesL);
			//trackReveralTradeEntry(data, quotesM, quotesL);
		//else if ( Constants.TRADE_CNT.equals(trade.type))
		//	trackPullBackTradeEntry(data, qtsS, qtsL);
	}
	
	
	
	public void trackReveralTradeEntry(QuoteData data, Object[] quotesM, Object[] quotesL)
	{
		//logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarM = quotesM.length -1;
		int lastbarL = quotesL.length -1;
		
	/*	
		int detectPos = Utility.getQuotePositionByMinute( quotes, trade.detectTime);
		
		if ( lastbar - detectPos > 45 )
		{
			logger.warning(symbol + " " + CHART + " takes too long to fill, trade cancelled");
			trade = null;
			return;
		}*/

		//if ( lastbar == detectPos )	
		//	return; 	// no new bar yet

		// qualifier 1:  first bar is not too deep
		// qualifier 2:  second bar start to move away
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if (data.high > trade.entryQualifyPrice)
				trade.entryQualifyCount++;
			if (trade.entryQualifyCount > 0 && (((QuoteData)quotesM[lastbarM]).low < ((QuoteData)quotesM[lastbarM-1]).low) && (((QuoteData)quotesM[lastbarM]).high < ((QuoteData)quotesM[lastbarM-1]).high))
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.close + " " + data.time);
				logger.warning(symbol + " " + CHART + " place market sell order at " + ((QuoteData)quotes[lastbar]).time);
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = data.close;
				trade.entryPrice = data.close;
				//trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize=trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData)quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
				if ( MODE == Constants.TEST_MODE )
					AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

				// calculate and place stop order
				//long oca = new Date().getTime();
				//trade.oca = oca;
				trade.stop = data.close + 2* FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

				// calculate and place target order
			//	trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
			//	trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
			//	trade.targetPositionSize = trade.positionSize/2;
			//	logger.warning(symbol + " " + CHART + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
				//logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
			//	trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
				/*
				trade.targetPrice2 = trade.price - 2*TARGET_PROFIT_SIZE * PIP_SIZE;
				trade.targetPrice2 = adjustPrice( trade.targetPrice2, Constants.ADJUST_TYPE_DOWN);
				trade.targetPositionSize2 = trade.positionSize/2;
				//System.out.println("adjusted target2 is " + trade.targetPrice2);
				//trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPositionSize2, new Long(oca).toString());
				*/
				return;

			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if (data.low < trade.entryQualifyPrice)
				trade.entryQualifyCount++;
			if ((trade.entryQualifyCount > 0) && (((QuoteData)quotesM[lastbarM]).high > ((QuoteData)quotesM[lastbarM-1]).high))// && (((QuoteData)quotesM[lastbarM]).low > ((QuoteData)quotesM[lastbarM-1]).low))
			{
				logger.warning(symbol + " " + CHART + " place market buy order at " + + data.close + " " + data.time);
				logger.warning(symbol + " " + CHART + " place market buy order at " + ((QuoteData)quotes[lastbar]).time);
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = data.close;
				trade.entryPrice = data.close;
				//trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
				trade.status = Constants.STATUS_PLACED;
				trade.position = Constants.POSITION_LONG;
				trade.remainingPositionSize=trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData)quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
				if ( MODE == Constants.TEST_MODE )
					AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);
	
				// calculate and place stop order
				//long oca = new Date().getTime();
				//trade.oca = oca;
				trade.stop = data.close - 2* FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
					
				// calculate and place target order
		//		trade.targetPrice = trade.price + TARGET_PROFIT_SIZE * PIP_SIZE;
			//	trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
			//	trade.targetPositionSize = trade.positionSize/2;
			//	logger.warning(symbol + " " + CHART + " place limit target sell order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
				//logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
			//	trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
				/*
				trade.targetPrice2 = trade.price + 2* TARGET_PROFIT_SIZE * PIP_SIZE;
				trade.targetPrice2 = adjustPrice( trade.targetPrice2, Constants.ADJUST_TYPE_UP);
				trade.targetPositionSize2 = trade.positionSize/2;
				//System.out.println("adjusted target2 is " + trade.targetPrice2);
				//trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPositionSize2, new Long(oca).toString());
				 */
				return;

			}
		}
	}


	
	
	public void trackReveralTradeEntry(QuoteData data, Object[] quotesL)
	{
		//logger.info(symbol + " new reversal entry" + trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarL = quotesL.length -1;
		
		if ( lastbar > trade.triggerPos + 24 )
		{
			logger.info(symbol + " " + CHART + " minute missed the boat");
			trade = null;
			return;
		}
		
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			for ( int i = trade.triggerPos; i <= lastbar; i++)
			{
				if (((QuoteData)quotes[lastbar]).high > trade.entryQualifyPrice )
				{
					trade.entryQualifyPriceTouched = true;
					break;
				}
			}
			if ( trade.entryQualifyPriceTouched == false )
				return;

			if ( lastbar == trade.triggerPos ) // first bar
			{
				return; 
			}
			else if ( lastbar <= trade.triggerPos + 2) // the second bar
			{
				if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
					enterSellPosition(data, lastbar, lastbarL);
			}
			else
			{
				PushHighLow phl_cur = findLastNHigh( quotes, trade.triggerPos, lastbar, 1 );
				if ( phl_cur != null )
					enterSellPosition(data, lastbar, lastbarL);
				
				//if ((((QuoteData)quotes[lastbar-1]).high < ((QuoteData)quotes[lastbar-2]).high))
				//	enterSellPosition(data, lastbar, lastbarL);
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			for ( int i = trade.triggerPos; i <= lastbar; i++)
			{
				if (((QuoteData)quotes[lastbar]).low < trade.entryQualifyPrice )
				{
					trade.entryQualifyPriceTouched = true;
					break;
				}
			}
			if ( trade.entryQualifyPriceTouched == false )
				return;

			if ( lastbar == trade.triggerPos ) // first bar
			{
				return; 
			}
			else if ( lastbar <= trade.triggerPos + 2) // the second bar
			{
				if ((((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high))
					enterBuyPosition(data, lastbar, lastbarL);
			}
			else
			{
				PushHighLow phl_cur = findLastNLow( quotes, trade.triggerPos, lastbar, 1 );
				if ( phl_cur != null )
					enterBuyPosition(data, lastbar, lastbarL);

				//if ((((QuoteData)quotes[lastbar-1]).low > ((QuoteData)quotes[lastbar-2]).low))
				//	enterBuyPosition(data, lastbar, lastbarL);
			}
		}
	}
	

	void enterSellPosition(QuoteData data, int lastbar, int lastbarL )
	{
		logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
		trade.price = data.close;
		trade.entryPrice = data.close;
		//trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize=trade.POSITION_SIZE;
		trade.entryTime = data.time;
		trade.entryPos = lastbar;
		trade.entryPosL = lastbarL;
		//trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
		if ( MODE == Constants.TEST_MODE )
			AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

		trade.stop = data.close + 2* FIXED_STOP * PIP_SIZE;
		trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

		return;

	}
	
	void enterBuyPosition(QuoteData data, int lastbar, int lastbarL )
	{
		logger.warning(symbol + " " + CHART + " place market buy order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
		trade.price = data.close;
		trade.entryPrice = data.close;
		//trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.position = Constants.POSITION_LONG;
		trade.remainingPositionSize=trade.POSITION_SIZE;
		trade.entryTime = data.time;
		trade.entryPos = lastbar;
		trade.entryPosL = lastbarL;
		//trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
		if ( MODE == Constants.TEST_MODE )
			AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

		// calculate and place stop order
		//long oca = new Date().getTime();
		//trade.oca = oca;
		trade.stop = data.close - 2* FIXED_STOP * PIP_SIZE;
		trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public void trackReveralTradeEntry_bak2(QuoteData data, Object[] quotesL)
	{
		//logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarL = quotesL.length -1;
		
		//int pullBackPosStart = Utility.getQuotePositionByMinute( quotes, ((QuoteData)quotesL[trade.pullBackPos]).time);
		//int pullBackPosEnd = Utility.getQuotePositionByMinute( quotes, ((QuoteData)quotesL[trade.pullBackPos+1]).time);

		//int detectPos = trade.detectPos;
		//Utility.getQuotePositionByMinute( quotes, trade.detectTime);

		
		//if ( lastbar == detectPos )	
		//	return; 	// no new bar yet


		
		// qualifier 1:  first bar is not too deep
		// qualifier 2:  second bar start to move away
		if (trade.action.equals(Constants.ACTION_SELL))
		{
		//	int pullBackPosS = Utility.getLow( quotes, pullBackPosStart, pullBackPosEnd).pos; 
			//logger.warning("pull back is at " + ((QuoteData)quotes[pullBackPosS]).time); 

		//	pullBackPosS = trade.pullBackPos;
		//  for ( int i = lastbar; i > pullBackPosS; i--)
		//	{
			//	PushHighLow phl = findLastNHigh( quotes, pullBackPosS, i, 2 );
			//	if ( phl != null )
				{
			//		if ( data.low < phl.pullBack.low )
					{
						//logger.warning("this high is btween " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.curPos]).time); 
						//logger.warning("this high pullback is " + ((QuoteData)quotes[phl.pullBack.pos]).time + "@" + phl.pullBack.low ); 
						//logger.warning("data.low < " + phl.pullBack.low ); 
						// place order
						logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
						logger.warning(symbol + " " + CHART + " place market sell order at " + ((QuoteData)quotes[lastbar]).time);
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = data.close;
						trade.entryPrice = data.close;
						//trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize=trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData)quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
						if ( MODE == Constants.TEST_MODE )
							AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);
		
						// calculate and place stop order
						//long oca = new Date().getTime();
						//trade.oca = oca;
						trade.stop = data.close + 14 * PIP_SIZE;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
		
						// calculate and place target order
						//trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
						//trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
						//trade.targetPositionSize = trade.positionSize/2;
						//logger.warning(symbol + " " + CHART + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
						//logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
						//trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						/*
						trade.targetPrice2 = trade.price - 2*TARGET_PROFIT_SIZE * PIP_SIZE;
						trade.targetPrice2 = adjustPrice( trade.targetPrice2, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize2 = trade.positionSize/2;
						//System.out.println("adjusted target2 is " + trade.targetPrice2);
						//trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPositionSize2, new Long(oca).toString());
						*/
						return;
					}
				//}
			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			//int pullBackPosS = Utility.getHigh( quotes, pullBackPosStart, pullBackPosEnd).pos; 
			//logger.warning("pull back is at " + ((QuoteData)quotes[pullBackPosS]).time); 
			//pullBackPosS = trade.pullBackPos;

			//for ( int i = lastbar; i > pullBackPosS; i--)
			//{
				//System.out.println("i=" + i);
				//PushHighLow phl = findLastNLow( quotes, pullBackPosS, i, 2 );
				//if ( phl != null )
				{
					//System.out.println("phl != null");
					//if ( data.high > phl.pullBack.high )
					{
						//logger.warning("this low is btween " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.curPos]).time); 
						//logger.warning("this low pullback is " + ((QuoteData)quotes[phl.pullBack.pos]).time + "@" + phl.pullBack.high ); 
						//logger.warning("data.high > " + phl.pullBack.high ); 

						logger.warning(symbol + " " + CHART + " place market buy order");
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = data.close;
						trade.entryPrice = data.close;
						//trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
						trade.status = Constants.STATUS_PLACED;
						trade.position = Constants.POSITION_LONG;
						trade.remainingPositionSize=trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData)quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
						if ( MODE == Constants.TEST_MODE )
							AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);
			
						// calculate and place stop order
						//long oca = new Date().getTime();
						//trade.oca = oca;
						trade.stop = data.close - 14 * PIP_SIZE;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
							
						// calculate and place target order
						//trade.targetPrice = trade.price + TARGET_PROFIT_SIZE * PIP_SIZE;
						//trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
						//trade.targetPositionSize = trade.positionSize/2;
						//logger.warning(symbol + " " + CHART + " place limit target sell order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
						//logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
						//trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						/*
						trade.targetPrice2 = trade.price + 2* TARGET_PROFIT_SIZE * PIP_SIZE;
						trade.targetPrice2 = adjustPrice( trade.targetPrice2, Constants.ADJUST_TYPE_UP);
						trade.targetPositionSize2 = trade.positionSize/2;
						//System.out.println("adjusted target2 is " + trade.targetPrice2);
						//trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPositionSize2, new Long(oca).toString());
						 */
						return;
						
					}
				//}
			}
		}
	}

	
	
	
	
	
	
	
	
	
	public void trackRSTTradeEntry(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		//logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarL = quotesL.length -1;
		
		//int detectPos = Utility.getQuotePositionByMinute( quotes, trade.detectTime);

		//if ( lastbar == detectPos )	
		//	return; 	// no new bar yet

		// qualifier 1:  first bar is not too deep
		// qualifier 2:  second bar start to move away
		/*
		if (( MODE == Constants.REAL_MODE ) && ( trade.orderPlaced == true ))
		{
			if ( lastbar - trade.entryPos > 20 )
			{
				logger.warning(symbol + " " + CHART + "minute took too long to fill, order cancelled");
				cancelOrder( trade.orderId);
				cancelOrder( trade.stopId);
				trade = null;
			}
			return;
		}*/
		
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
			{
				// check how far it went after pass the detect position
				boolean reachedEntryQualifyPrice = false;
				int reachEntryQualifyPricePos = 0;
				for ( int i = trade.entryQualifyPricePos+1; i <= lastbar; i++ )
				{
					if (((QuoteData)quotes[i]).high > trade.entryQualifyPrice )
					{
						reachedEntryQualifyPrice = true;
						reachEntryQualifyPricePos = i;
						break;
					}
				}
			
				if ( reachedEntryQualifyPrice == false )
					return;

				// analysis how it is break the entryQualifyPrice
				// 1.  break out too large  bar > 3
				if (( lastbar - reachEntryQualifyPricePos >= 3 ) && (trade.entryResets < 1 ))
				{
					if ( rsc_trade == false ) 
					{
						resetHighEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
						trade.entryResets++;
					}
					else
					{	
						trade.type = Constants.TRADE_RSC;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.detectPos = lastbar;
					}
					
					return;
				}
			
				/*
				// 2.  breakout too lar size > 10 pips
				double breakOutHigh = Utility.getHigh( quotes, trade.detectPos, lastbar).high;
				if ((breakOutHigh - trade.entryQualifyPrice) > 10 * PIP_SIZE ) 
				{
					resetHighEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
					trade.entryResets++;
					return;
				}*/
				
				// 3.  pull back too small
				/*if ( trade.entryQualifyPricePos > 0 )
				{
					QuoteData pullback = Utility.getHigh(quotes, reachEntryQualifyPricePos, lastbar);
					if (pullback.high - trade.entryQualifyPrice < Utility.getAverage(quotes, lastbar-20, lastbar)) 
					{
						resetHighEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
						return;
					}
				}*/
				
				
				// close it if reached 10 pips below the trigger priceDD  
				if ((trade.detectPrice - ((QuoteData)quotes[lastbar]).close) > FIXED_STOP * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " missed the boat at " + ((QuoteData)quotes[lastbar]).time);
					trade = null;
					return;
				}
				
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData)quotes[lastbar-1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData)quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
				trade.profitTakeStartPos = lastbar-1;

				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				
				trade.remainingPositionSize=trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getHigh(quotes, trade.detectPos, lastbar).high;
				trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

				// calculate and place target order
				//trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
				//trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
				//trade.targetPositionSize = trade.positionSize/2;
				//logger.warning(symbol + " " + CHART + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
				//logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
				//trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
				return;

			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
			{
				boolean reachedEntryQualifyPrice = false;
				int reachEntryQualifyPricePos = 0;
				for ( int i = trade.entryQualifyPricePos+1; i <= lastbar; i++ )
				{
					if (((QuoteData)quotes[i]).low < trade.entryQualifyPrice )
					{
						reachedEntryQualifyPrice = true;
						reachEntryQualifyPricePos = i;
						break;
					}
				}
			
				if ( reachedEntryQualifyPrice == false )
					return;

				if (( lastbar - reachEntryQualifyPricePos >= 3 ) && ( trade.entryResets < 1 ))
				{
					if ( rsc_trade == false )
					{
						logger.warning(symbol + " " + CHART + " reset entry at " + ((QuoteData)quotes[lastbar]).time);
						resetLowEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
						trade.entryResets++;
					}
					else
					{	
						// TEST: ADDED FOR RSC
						trade.type = Constants.TRADE_RSC;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.detectPos = lastbar;
					}

					return;
				}

				/*
				double breakOutLow = Utility.getLow( quotes, trade.detectPos, lastbar).low;
				if ((trade.entryQualifyPrice - breakOutLow) > 10 * PIP_SIZE ) 
				{
					resetLowEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
					trade.entryResets++;
					return;
				}*/

				if (((QuoteData)quotes[lastbar]).close - trade.detectPrice > FIXED_STOP * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " missed the boat at " + ((QuoteData)quotes[lastbar]).time);
					trade = null;
					return;
				}

				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = ((QuoteData)quotes[lastbar-1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData)quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
				trade.profitTakeStartPos = lastbar-1;

				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
	
				trade.remainingPositionSize=trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getLow(quotes, trade.detectPos, lastbar).low;
				trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
					
				// calculate and place target order
				//trade.targetPrice = trade.price + TARGET_PROFIT_SIZE * PIP_SIZE;
				//trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
				//trade.targetPositionSize = trade.positionSize/2;
				//logger.warning(symbol + " " + CHART + " place limit target sell order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
				//logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
				return;

			}
		}
	}

	

	public void trackRSCTradeEntry(QuoteData data, Object[] quotesL)
	{
		//logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarL = quotesL.length -1;
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData)quotes[lastbar-1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData)quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;

				if ( MODE == Constants.REAL_MODE )
				{
					//trade.price = adjustPrice( trade.price, Constants.ADJUST_TYPE_UP);
					//trade.orderId = placeLmtOrder(Constants.ACTION_SELL, trade.price, trade.positionSize, null);
					//trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				}
				else if ( MODE == Constants.TEST_MODE )
				{
					AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				}
				
				trade.remainingPositionSize=trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getHigh(quotes, trade.detectPos, lastbar).high;
				trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

				return;

			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
			{
				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = ((QuoteData)quotes[lastbar-1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData)quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;

				if ( MODE == Constants.REAL_MODE )
				{
					//trade.price = adjustPrice( trade.price, Constants.ADJUST_TYPE_DOWN);
					//trade.orderId = placeLmtOrder(Constants.ACTION_BUY, trade.price, trade.positionSize, null);
					//trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				}
				else if ( MODE == Constants.TEST_MODE )
				{
					AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
				}
	
				trade.remainingPositionSize=trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getLow(quotes, trade.detectPos, lastbar).low;
				trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
					
				return;

			}
		}
	}


	
	
	
	
	
	
	
	
	
	
	public void trackReveralTradeEntry2(QuoteData data, Vector<QuoteData> qtsS, Vector<QuoteData> qtsL)
	{
		logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = qtsS.toArray();
		int lastbar = quotes.length-1;

		int detectPos = Utility.getQuotePositionByMinute( quotes, trade.detectTime);

		// need at least two bars
		if ( lastbar == detectPos )	
		{
			logger.info(symbol + " no movement yet" );
			return; 	// no new bar yet
		}

		// qualifier 1:  first bar is not too deep
		// qualifier 2:  second bar start to move away

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
			{
				// check it has reached preQualifyPrice,
				if ( !trade.status.equals( Constants.STATUS_STOPPEDOUT))  // stop out does not apply
				{
					if ( lastbar - trade.pullBackPos > 44 )
					{
						trade = null;
						return;
					}
					
					int i = lastbar;
					while ( i > trade.pullBackPos )
					{
						PushHighLow first = findLastNLow( quotes, trade.pullBackPos, i--, 3);
						if ( first != null )
						{
							int j = first.prePos;
							while ( j > trade.pullBackPos)
							{
								PushHighLow second = findLastNLow( quotes, trade.pullBackPos, j--, 3);
								if ( second != null )
								{
									// place order
									trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
									trade.price = data.close;
									trade.entryPrice = data.close;
									trade.status = Constants.STATUS_PLACED;
									trade.remainingPositionSize=trade.POSITION_SIZE;
									trade.entryTime = ((QuoteData)quotes[lastbar]).time;
									trade.entryPos = lastbar;
									trade.entryTimeL = ((QuoteData)qtsL.lastElement()).time;
									if ( MODE == Constants.TEST_MODE )
										AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

									// calculate and place stop order
									//long oca = new Date().getTime();
									//trade.oca = oca;
									trade.stop = data.close + FIXED_STOP * PIP_SIZE;
									trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
									logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
									trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

									// calculate and place target order
									trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
									trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
									trade.targetPositionSize = trade.POSITION_SIZE/2;
									logger.warning(symbol + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
									trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
									return;

								}
							}
						}
					}
				}
			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
			{
				if ( !trade.status.equals( Constants.STATUS_STOPPEDOUT))  // stop out does not apply
				{
					if ( lastbar - trade.pullBackPos > 44 )
					{
						trade = null;
						return;
					}
					
					int i = lastbar;
					while ( i > trade.pullBackPos )
					{
						PushHighLow first = findLastNLow( quotes, trade.pullBackPos+1, i--, 2);
						if ( first != null )
						{
							int j = first.prePos;
							while ( j > trade.pullBackPos)
							{
								PushHighLow second = findLastNLow( quotes, trade.pullBackPos+1, j--, 2);
								if ( second != null )
								{
									trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
									trade.price = data.close;
									trade.entryPrice = data.close;
									trade.status = Constants.STATUS_PLACED;
									trade.position = Constants.POSITION_LONG;
									trade.remainingPositionSize=trade.POSITION_SIZE;
									trade.entryTime = ((QuoteData)quotes[lastbar]).time;
									trade.entryPos = lastbar;
									trade.entryTimeL = ((QuoteData)qtsL.lastElement()).time;
									if ( MODE == Constants.TEST_MODE )
										AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

									// calculate and place stop order
									//long oca = new Date().getTime();
									//trade.oca = oca;
									trade.stop = data.close - FIXED_STOP * PIP_SIZE;
									trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
									logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
									trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
									
									// calculate and place target order
									trade.targetPrice = trade.price + TARGET_PROFIT_SIZE * PIP_SIZE;
									trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
									trade.targetPositionSize = trade.POSITION_SIZE/2;
									logger.warning(symbol + " place limit target sell order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
									trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
									return;

								}
							}
						}
					}

					
				//	if ( counting123( quotes,trade.pullBackPos, lastbar, Constants.DIRECTION_UP ) < 3 )
				//		return;
					
				}

			}
		}
	}



	
	public int counting123( Object[] quotes, int begin, int end, int direction )
	{
		int pos = begin+1;
		int count = 0;
		
		if ( Constants.DIRECTION_UP == direction )
		{
			while (true )
			{	
				//while (( pos <= end ) && (((QuoteData) quotes[pos]).high >= ((QuoteData) quotes[pos-1]).high))
				//	pos++;
				while (( pos <= end ) && 
						!((((QuoteData) quotes[pos]).high < ((QuoteData) quotes[pos-1]).high) && (((QuoteData) quotes[pos]).low < ((QuoteData) quotes[pos-1]).low)))
					pos++;
				
				logger.info(symbol + " count high: " + ((QuoteData) quotes[pos-1]).high + " at " + ((QuoteData) quotes[pos-1]).time );
				count++;
			
				if ( pos > end )
					return count;
				
				// now pos is smaller than pos-1
				double lastHigh = ((QuoteData) quotes[pos-1]).high;
				while (( pos <= end ) && (((QuoteData) quotes[pos]).high <= lastHigh))
					pos++;

				if ( pos > end )
					return count;
				
				pos++;
				// now pos is > lastHigh
			}
		}
		else if ( Constants.DIRECTION_DOWN == direction )
		{
			while (true )
			{	
				//while (( pos <= end ) && (((QuoteData) quotes[pos]).low <= ((QuoteData) quotes[pos-1]).low))
				//	pos++;
				while (( pos <= end ) && 
					!((((QuoteData) quotes[pos]).high > ((QuoteData) quotes[pos-1]).high ) && (((QuoteData) quotes[pos]).low > ((QuoteData) quotes[pos-1]).low)))
					pos++;
				
				logger.info(symbol + " count low: " + ((QuoteData) quotes[pos-1]).low + " at " + ((QuoteData) quotes[pos-1]).time );
				count++;
			
				if ( pos > end )
					return count;
				
				// now pos is smaller than pos-1
				double lastLow = ((QuoteData) quotes[pos-1]).low;
				while (( pos <= end ) && (((QuoteData) quotes[pos]).low >= lastLow ))
					pos++;

				if ( pos > end )
					return count;
				
				pos++;

				// now pos is < lastLow
			}
		}
		
		return Constants.NOT_FOUND;
				
	
	}
	

	
	
	
	
	
	
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	//   Trade Target
	//
	//
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/*
	public void trackReversalTarget(QuoteData data, Vector<QuoteData> qtsM, Vector<QuoteData> qtsL)
	{
		logger.info(symbol + " track trade entry " + trade.detectTime);
		double[] ema20M = Indicator.calculateEMA(qtsM, 20);
		Object[] quotesM = qtsM.toArray();
		int lastbarM = quotesM.length-1;

		double[] ema20L = Indicator.calculateEMA(qtsL, 20);
		double[] ema40L = Indicator.calculateEMA(qtsL, 40);
		Object[] quotesL = qtsL.toArray();
		int lastbarL = quotesL.length-1;
		
		logger.info("trade detectionTime is " + trade.detectTime);
		logger.info("trade entryTime is " + trade.entryTime);
		int detectPos = Utility.getQuotePositionByMinute( quotesM, trade.detectTime);
		int entryPos = Utility.getQuotePositionByMinute( quotesM, trade.entryTime);
		logger.info("trade detection position is " + detectPos);
		logger.info("trade entryTime position is " + entryPos);

		double avgSizeM = Utility.getAverage(quotesM);

		// exit at extrem, reversal signal
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//Vector<BreakOut> breakouts = Pattern.findMABreakoutUps(quotesM, ema20M, detectPos);

			//////////////////////////////////
			// first part, take initial profit
			//////////////////////////////////
			
			logger.info(symbol + " track trade - check taking partial profit");
			if(( trade.initProfitTaken == false ) &&
					(((QuoteData)quotesM[lastbarM]).high > ((QuoteData)quotesM[lastbarM-1]).high)) 
			{
				logger.info(symbol + " track trade - check cup");

				// take profit if there is large diverage
				Cup cup = Pattern.downCup(symbol, quotesM, ema20M, entryPos, lastbarM, 0, 0, avgSizeM * 3, false);  
			
				if (( cup != null ) && ( cup.pullBackWidth <=12 )) 
				{
					System.out.println(symbol + " 3x down cup detected at " + data.time + " exit trade");
					logger.info(symbol + " 3x down cup detected at " + data.time + " exit trade");
					
					takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L ));
					return;
				}	
			
				logger.info(symbol + " track trade - check partical profit");
				// take some profit if there is a windfall
				double maxBar = 0;
				int pos = lastbarM-1;
				while (((QuoteData)quotesM[pos-1]).high < ((QuoteData)quotesM[pos]).high )
				{
					if (((QuoteData)quotesM[pos]).open - ((QuoteData)quotesM[pos]).close > maxBar)
						maxBar = ((QuoteData)quotesM[pos]).open - ((QuoteData)quotesM[pos]).close;
					pos--;
				}
				
				if (( maxBar > 3 * avgSizeM ) && ( trade.takeProfit.get(pos) == null ))
				{
					logger.info(symbol + " windfall profit detect at " + data.time  );
					System.out.println(symbol + " windfall profit detect at " + data.time );
					takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L ));
					return;
				}
				
				logger.info(symbol + " track trade - check 50 pips profit");
				// take some profit if it hits 50 pips
				if ( inProfit(data) > 50 * PIP_SIZE ) //&& (trade.takeProfit.containsValue(0) == false))
				{
					System.out.println(symbol + " default profit detect at " + data.time );
					takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L ));
					return;
				}
			}
			
			///////////////////////////////////////////
			// second part, see if we can move the stop
			///////////////////////////////////////////
			logger.info(symbol + " track trade - check moving stops");
			if(( trade.initProfitTaken == true ) &&
			 ((((QuoteData)quotesL[lastbarL-1]).high < ema20L[lastbarL-1]) && (((QuoteData)quotesL[lastbarL-2]).high >= ema20L[lastbarL-2])))
			{
				// find the last close below 20MA
				int begin = lastbarL-2;
				while (((QuoteData)quotesL[begin]).high < ema20L[begin])
					begin--;
				
				double lastHigh = Utility.getHigh(quotesL, begin, lastbarL-1).high;
				if ( trade.stop != lastHigh)
				{
					trade.stop = lastHigh;
					logger.info(symbol + " move stop to " + trade.stop);
				
					if ( trade.stopId != 0 )
						cancelOrder(trade.stopId);

					trade.stopId = placeStopOrder( Constants.ACTION_BUY, adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize, null);
				}
			}
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//logger.info(symbol + " to move stop ");
			// adjust inital stop and perhaps take initial profit
			/*
			// check if no at a potential reversal point
			Vector<Wave> waves = Pattern.upWave( symbol, quotesM, entryPos, 2*avgSizeM );

			Iterator it = waves.iterator();
			
			logger.info("Wave test: ");
			while ( it.hasNext())
			{
				Wave wave = (Wave)it.next();
				logger.info("push: " + wave.push + " pullback: " + wave.pullback);
			}*/
			
			// take profit if there is large diverage
		/*	logger.info(symbol + " track trade - check taking partial profit");
			if(( trade.initProfitTaken == false ) &&
			 (((QuoteData)quotesM[lastbarM]).low < ((QuoteData)quotesM[lastbarM-1]).low))
			{
				logger.info(symbol + " track trade - check cup");
				Cup cup = Pattern.upCup(symbol, quotesM, ema20M, entryPos, lastbarM, 0, 0, avgSizeM * 3, false);  
		
				if (( cup != null ) && ( cup.pullBackWidth <=12 )) 
				{	
					System.out.println(symbol + " 3x up cup detected at " + data.time + " exit trade");
					logger.info(symbol + " 3x up cup detected at " + data.time + " exit trade");
					takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L ));
					return;
				}	
		
				// take some profit if there is a windfall
				logger.info(symbol + " track trade - check partical profit");
				double maxBar = 0;
				int pos = lastbarM-1;
				while (((QuoteData)quotesM[pos-1]).high < ((QuoteData)quotesM[pos]).high )
				{
					if (((QuoteData)quotesM[pos]).open - ((QuoteData)quotesM[pos]).close > maxBar)
						maxBar = ((QuoteData)quotesM[pos]).open - ((QuoteData)quotesM[pos]).close;
					pos--;
				}
			
				if (( maxBar > 3 * avgSizeM ) && ( trade.takeProfit.get(pos) == null ))
				{
		/*			logger.info(symbol + " windfall profit detect at " + data.time /*+ " take profit on " + windFallProfitSize*/   /*);
		/*			System.out.println(symbol + " windfall profit detect at " + data.time /*+ " take profit on " + windFallProfitSize*/ /*);
		/*			takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L ));
					return;
				}
			
				// take some profit if it hits 50 pips
				logger.info(symbol + " track trade - check 50 pips profit");
				if (( inProfit(data) > 50 * PIP_SIZE ) && (trade.takeProfit.containsValue(0) == false))
				{
					logger.info(symbol + " default profit detect at " + data.time );
					System.out.println(symbol + " default profit detect at " + data.time );
					takeProfit(calculateTrendStrength(quotesL, ema20L, ema40L ));
					return;
				}
			}

				///////////////////////////////////////////
				// second part, see if we can move the stop
				///////////////////////////////////////////
			logger.info(symbol + " track trade - check moving stops");
			if(( trade.initProfitTaken == true ) &&
			 ((((QuoteData)quotesL[lastbarL-1]).low > ema20L[lastbarL-1]) && (((QuoteData)quotesL[lastbarL-2]).low <= ema20L[lastbarL-2])))
			{
				// find the last close above 20MA
				int begin = lastbarL-2;
				while (((QuoteData)quotesL[begin]).low < ema20L[begin])
					begin--;
				
				double lastLow = Utility.getLow(quotesL, begin, lastbarL-1).low;
				if ( trade.stop != lastLow)
				{
					trade.stop = lastLow;
					logger.info(symbol + " move stop to " + trade.stop);
				
					if ( trade.stopId != 0 )
						cancelOrder(trade.stopId);

					trade.stopId = placeStopOrder( Constants.ACTION_SELL, adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize, null);
				}
			}
		}
	}*/
	
	

	public void trackReversalTarget2(QuoteData data, Vector<QuoteData> qts)
	{
		logger.info(symbol + " track trade entry " + trade.detectTime);
		double[] ema20 = Indicator.calculateEMA(qts, 20);
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//Will be looking for reversal on the other side;

			///////////////////////////////////////////
			// second part, see if we can move the stop
			///////////////////////////////////////////
			logger.info(symbol + " track trade - check moving stops");
			if((((QuoteData)quotes[lastbar-1]).high < ema20[lastbar-1]) && (((QuoteData)quotes[lastbar-2]).high >= ema20[lastbar-2]))
			{
				// find the last close below 20MA
				int begin = lastbar-2;
				while (((QuoteData)quotes[begin]).high < ema20[begin])
					begin--;
				
				double lastHigh = Utility.getHigh(quotes, begin, lastbar-1).high + 5 * PIP_SIZE; 
				if ( trade.stop != lastHigh)
				{
					trade.stop = lastHigh;
					logger.info(symbol + " move stop to " + trade.stop);
				
					if ( trade.stopId != 0 )
						cancelOrder(trade.stopId);

					trade.stopId = placeStopOrder( Constants.ACTION_BUY, adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize, null);
				}
			}
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			///////////////////////////////////////////
			// second part, see if we can move the stop
			///////////////////////////////////////////
			if((((QuoteData)quotes[lastbar-1]).low > ema20[lastbar-1]) && (((QuoteData)quotes[lastbar-2]).low <= ema20[lastbar-2]))
			{
				// find the last close above 20MA
				int begin = lastbar-2;
				while (((QuoteData)quotes[begin]).low < ema20[begin])
					begin--;
				
				double lastLow = Utility.getLow(quotes, begin, lastbar-1).low - 5 * PIP_SIZE;
				if ( trade.stop != lastLow)
				{
					trade.stop = lastLow;
					logger.info(symbol + " move stop to " + trade.stop);
				
					if ( trade.stopId != 0 )
						cancelOrder(trade.stopId);

					trade.stopId = placeStopOrder( Constants.ACTION_SELL, adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize, null);
				}
			}
		}
	}
	

	
	
	
	
	
	
	public void trackTradeTarget(QuoteData data, Object[] quotesS, Object[] quotesL )
	{
		if ( MODE == Constants.TEST_MODE )
			checkStopTarget(data);
		
		if ( trade != null )
			//exit123_3_c( data );
			exit123_3_c_1m_works_but_no_diffience( data, quotesS, quotesL);

	}
	
	
	public void takeInitialProfit(QuoteData data, int ti, Vector<QuoteData> qts)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		if ( trade.initProfitTaken == false )
		{
			// check first return > risk
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
				{
					if (( trade.entryPrice - data.close ) > (1.5 * trade.risk ))
					{
						logger.info(symbol + " " + ti + " minutes take initial profit");
						System.out.println(symbol + " " + ti + " minutes take initial profit");
						closePositionByMarket((int) (0.25 * trade.POSITION_SIZE), data.close);
						AdjustStopTargetOrders( (new Date()).toString());
						trade.initProfitTaken = true;
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low))
				{
					if ((  data.close - trade.entryPrice ) > (1.5 * trade.risk ))
					{
						logger.info(symbol + " " + ti + " minutes take initial profit");
						System.out.println(symbol + " " + ti + " minutes take initial profit");
						closePositionByMarket((int) (0.25 * trade.POSITION_SIZE), data.close);
						AdjustStopTargetOrders( (new Date()).toString());
						trade.initProfitTaken = true;
					}
				}
			}
	
		}
		
	}

	
	
	
	
	


	
	public void exit123_3_c_1m_works_but_no_diffience(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarS = quotesS.length -1;

		/*
		// if quick profit target set, cancel target if it has not bee a quick profit
		if (Constants.TRADE_CNT.equals(trade.type) && ( lastbar - trade.entryPos > 8 ) && ( trade.targetReached == false))
		{
			trade.targetPrice = 0;
			trade.targetPositionSize = 0;
			
			if ( MODE == Constants.REAL_MODE)
				cancelOrder( trade.targetId);
		}*/


		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.profitTake2Triggered != true )
			{	
				if ( trade.entryPos + 2 > lastbar )
					return;
	
				int top;
				
				if (Constants.TRADE_RST.equals(trade.type))
					top = Utility.getHigh( quotes, trade.entryPos, lastbar ).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					top = trade.lowHighAfterEntry.pos;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}
	
	
				for ( int i = lastbar; i > top; i--)
				{
					PushHighLow phl = findLastNLow( quotes, top, i, 1 );
					if ( phl != null )
					{
						if ( i == lastbar )
						{
							// take partical profit if the pull back is great than 25pips
							if (( trade.profitTake2 ==false )&& (phl.pullBack.high - (((QuoteData)quotes[phl.prePos]).low ) > LOCKIN_PROFIT * PIP_SIZE ))
							{
								trade.profitTake2Triggered = true;
								trade.profitTake2 = true;
								break;
							}
							
							/*  this limits the profit, might not want this
							for ( int j = i-1; j > trade.profitTakeStartPos; j--)
							{
								PushHighLow phl_pre = findLastNLow( quotes, trade.profitTakeStartPos, j, 2 );
								if ( phl_pre != null )
								{
									double low = ((QuoteData)quotes[phl_pre.prePos]).low;
									
									QuoteData afterLow = Utility.getLow( quotes, phl_pre.curPos, i-1);
									if ( low - afterLow.low  < 2.5 * PIP_SIZE )
									{
										trade.profitTake2Triggered = true;
										break;
									}
								}
								
							}*/
	
						}

						// take profit if it breaks higher-high/lower-low
						if ( data.high > phl.pullBack.high )
						{
							int lastTradeEntryPos = trade.entryPos;
							double lastTradEentryPrice = trade.entryPrice;
							String lastTradeType = trade.type;
							logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);

							AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
			
							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST))
							{
								int lastbarL = quotesL.length - 1;
								double[] ema20L = Indicator.calculateEMA(quotesL, 20);
								double[] ema50L = Indicator.calculateEMA(quotesL, 50);
	
								if (ema20L[lastbarL] > ema50L[lastbarL])
								{
									QuoteData lowAfterEntry = Utility.getLow( quotes, lastTradeEntryPos, lastbar);
									if (  lastTradEentryPrice - lowAfterEntry.low  < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
									{
										trade = new Trade(symbol);
										trade.type = Constants.TRADE_CNT;
										trade.action = Constants.ACTION_BUY;
										trade.POSITION_SIZE = POSITION_SIZE;
										trade.lowHighAfterEntry = lowAfterEntry;
											
										logger.warning(symbol + " " + CHART + " place market reverse buy order");
										trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE );
										trade.price = data.close;//phl.pullBack.high;
										trade.entryPrice = trade.price;
										trade.status = Constants.STATUS_PLACED;
										trade.remainingPositionSize=trade.POSITION_SIZE;
										trade.entryTime = data.time;//((QuoteData)quotes[lastbar]).time;
										trade.entryPos = lastbar;
										if ( MODE == Constants.TEST_MODE )
											AddTradeOpenRecord( trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.entryPrice);
			
										trade.stop = lowAfterEntry.low;
										if (( trade.entryPrice - trade.stop ) > FIXED_STOP * PIP_SIZE )
											trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
										
										trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
										
										logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
										trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
									}
								}
							}
							return;
						}
					}
				}
			}
			
			if ( trade.profitTake2Triggered == true )
			{
				if ((((QuoteData)quotesS[lastbarS]).high > ((QuoteData)quotesS[lastbarS-1]).high))
				{	
					if ( MODE == Constants.TEST_MODE)
						AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
					
					closePositionByMarket( trade.remainingPositionSize, data.close);
					//trade.profitTake2 = true;
					//trade.profitTake2Triggered = false;
	
					// large pull back, lock in profit
					/*
					trade.targetPrice = data.close;  // TODO: this could be better
					trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_DOWN);
					
					trade.targetPositionSize = trade.remainingPositionSize;
	
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					trade.profitTake2 = true;
					trade.profitTake2Triggered = false;*/
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.profitTake2Triggered != true )
			{	
				if ( trade.entryPos + 2 > lastbar )
					return;
	
				int bottom;
				if (Constants.TRADE_RST.equals(trade.type))
					bottom = Utility.getLow( quotes, trade.entryPos, lastbar ).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					bottom = trade.lowHighAfterEntry.pos;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}
				
				for ( int i = lastbar; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh( quotes, bottom, i, 1 );
					if ( phl != null )
					{
						//logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).high + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).high + " pullback@" + phl.pullBack ); 
	
						if ( i == lastbar )
						{
							if (( trade.profitTake2 ==false )&&((((QuoteData)quotes[phl.prePos]).high - phl.pullBack.low ) > 25 * PIP_SIZE ))
							{
								trade.profitTake2Triggered = true;
								trade.profitTake2 = true;
								break;
							}
	
	
							/* take quick profit, this limits the profit
							for ( int j = i-1; j > bottom; j--)
							{
								PushHighLow phl_pre = findLastNHigh( quotes, bottom, j, 2 );
								if ( phl_pre != null )
								{
									double high = ((QuoteData)quotes[phl_pre.prePos]).high;
									
									QuoteData afterHigh = Utility.getHigh( quotes, phl_pre.curPos, i-1);
									if ( afterHigh.high - high  < 2.5 * PIP_SIZE )
									{
										logger.warning(symbol + " " + CHART + " minute last high was " + ((QuoteData)quotes[phl_pre.prePos]).time + " after high was " + ((QuoteData)quotes[afterHigh.pos]).time + " difference is less than 2.5 pips, take profit");
										trade.profitTake2Triggered = true;
										break;
									}
								}
								
							}*/
						}
	
						if ( data.low < phl.pullBack.low )
						{
							int lastTradeEntryPos = trade.entryPos;
							double lastTradEentryPrice = trade.entryPrice;
							String lastTradeType = trade.type;
							//double lastPullbackHigh = ((QuoteData)quotes[trade.pullBackPos]).high;
							logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);
							if ( MODE == Constants.TEST_MODE)
								AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
	
							closePositionByMarket( trade.remainingPositionSize, data.close);
				
							/*
							else if ( MODE == Constants.REAL_MODE)
							{	
								trade.targetPrice = phl.pullBack.low;
								trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
								trade.targetPositionSize = trade.remainingPositionSize;
		
								trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							*/
						
							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST))
							{
								// enter a reverse trade only if following the biger trend
								int lastbarL = quotesL.length - 1;
								double[] ema20L = Indicator.calculateEMA(quotesL, 20);
								double[] ema50L = Indicator.calculateEMA(quotesL, 50);
	
								if (ema20L[lastbarL] < ema50L[lastbarL])
								{
									QuoteData highAfterEntry = Utility.getHigh( quotes, lastTradeEntryPos, lastbar);
									if ( highAfterEntry.high - lastTradEentryPrice < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
									{
										trade = new Trade(symbol);
										trade.type = Constants.TRADE_CNT;
										trade.action = Constants.ACTION_SELL;
										trade.POSITION_SIZE = POSITION_SIZE;
										trade.lowHighAfterEntry = highAfterEntry;
											
										logger.warning(symbol + " " + CHART + " place market reverse sell order");
										trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE );
										trade.price = data.close;//phl.pullBack.low;
										trade.entryPrice = trade.price;
										trade.status = Constants.STATUS_PLACED;
										trade.remainingPositionSize=trade.POSITION_SIZE;
										trade.entryTime = data.time;//((QuoteData)quotes[lastbar]).time;
										trade.entryPos = lastbar;
										if ( MODE == Constants.TEST_MODE )
											AddTradeOpenRecord( trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.entryPrice);
			
										trade.stop = highAfterEntry.high;
										if ((trade.stop -  trade.entryPrice ) > FIXED_STOP * PIP_SIZE )
											trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;
										
										trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
			
										logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
										trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
									}
								}
							}
							return;
						}
					}
				}
			}

			if ( trade.profitTake2Triggered == true )
			{
				if ((((QuoteData)quotesS[lastbarS]).low < ((QuoteData)quotesS[lastbarS-1]).low))
				{
					if ( MODE == Constants.TEST_MODE)
						AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
					
					closePositionByMarket( trade.remainingPositionSize, data.close);
					//trade.profitTake2 = true;
					//trade.profitTake2Triggered = false;
	
					/*
					trade.targetPrice = data.close;
					trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
					
					trade.targetPositionSize = trade.remainingPositionSize;
	
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					trade.profitTake2 = true;
					trade.profitTake2Triggered = false;*/
					return;
				}
			}
		}
	}


	
	
	
	
	
	
	
	public void exit123_3_c(QuoteData data)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;

		/*
		// if quick profit target set, cancel target if it has not bee a quick profit
		if (Constants.TRADE_CNT.equals(trade.type) && ( lastbar - trade.entryPos > 8 ) && ( trade.targetReached == false))
		{
			trade.targetPrice = 0;
			trade.targetPositionSize = 0;
			
			if ( MODE == Constants.REAL_MODE)
				cancelOrder( trade.targetId);
		}*/

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.entryPos + 2 > lastbar )
				return;

			int top;
			
			if (Constants.TRADE_RST.equals(trade.type) || Constants.TRADE_RSC.equals(trade.type) )
				top = Utility.getHigh( quotes, trade.entryPos, lastbar ).pos;
			else if (Constants.TRADE_CNT.equals(trade.type))
				top = trade.lowHighAfterEntry.pos;
			else
			{
				logger.severe(symbol + " " + CHART + " no trade status set!");
				return;
			}

			
			for ( int i = lastbar; i > top; i--)
			{
				PushHighLow phl = findLastNLow( quotes, top, i, 1 );
				if ( phl != null )
				{
					if ( i == lastbar )
					{
						if (( trade.profitTake2 ==false )&& (phl.pullBack.high - (((QuoteData)quotes[phl.prePos]).low ) > LOCKIN_PROFIT * PIP_SIZE ))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							// large pull back, lock in profit
							trade.targetPrice = data.close;  // TODO: this could be better
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_DOWN);
							
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}
							
					}
					
					//logger.warning(symbol + " exit low detected at  " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).low + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).low + " pullback:" + ((QuoteData)quotes[phl.pullBack.pos]).time +"@" + phl.pullBack.high );
					/* not worth fixing
					if ( phl.pullBack.pos == phl.prePos )
						phl.pullBack = Utility.getHigh(quotes, phl.prePos+1, phl.curPos);*/
					if (!trade.profitTake1 && ( data.high > phl.pullBack.high ))
					{
						int lastTradeEntryPos = trade.entryPos;
						double lastTradEentryPrice = trade.entryPrice;
						String lastTradeType = trade.type;
						logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);

						if ( MODE == Constants.TEST_MODE)
							AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
						
						/*
						if ( MODE == Constants.TEST_MODE)
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
						}
						else if ( MODE == Constants.REAL_MODE)
						{	
							trade.targetPrice = phl.pullBack.high;
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_DOWN);
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake1 = true;
						}*/
						
						// check to see if it worth reversal
						if (lastTradeType.equals(Constants.TRADE_RST) && (phl.curPos - phl.prePos > 2) )
						{
							QuoteData lowAfterEntry = Utility.getLow( quotes, lastTradeEntryPos, lastbar);
							
							if (  lastTradEentryPrice - lowAfterEntry.low  < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
							{
								trade = new Trade(symbol);
								trade.type = Constants.TRADE_CNT;
								trade.action = Constants.ACTION_BUY;
								trade.POSITION_SIZE = POSITION_SIZE;
								trade.lowHighAfterEntry = lowAfterEntry;
									
								logger.warning(symbol + " " + CHART + " place market reverse buy order");
								trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE );
								trade.price = phl.pullBack.high;
								trade.entryPrice = trade.price;
								trade.status = Constants.STATUS_PLACED;
								trade.remainingPositionSize=trade.POSITION_SIZE;
								trade.entryTime = ((QuoteData)quotes[lastbar]).time;
								trade.entryPos = lastbar;
								if ( MODE == Constants.TEST_MODE )
									AddTradeOpenRecord( trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.entryPrice);
	
								trade.stop = lowAfterEntry.low;
								if (( trade.entryPrice - trade.stop ) > FIXED_STOP * PIP_SIZE )
									trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
								
								trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
								
								logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
								trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
							}
						}
						
						return;
						
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.entryPos + 2 > lastbar )
				return;

			int bottom;
			if (Constants.TRADE_RST.equals(trade.type) || Constants.TRADE_RSC.equals(trade.type))
				bottom = Utility.getLow( quotes, trade.entryPos, lastbar ).pos;
			else if (Constants.TRADE_CNT.equals(trade.type))
				bottom = trade.lowHighAfterEntry.pos;
			else
			{
				logger.severe(symbol + " " + CHART + " no trade status set!");
				return;
			}

			for ( int i = lastbar; i > bottom; i--)
			{
				PushHighLow phl = findLastNHigh( quotes, bottom, i, 1 );
				if ( phl != null )
				{
					//logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).high + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).high + " pullback@" + phl.pullBack ); 

					if ( i == lastbar )
					{
						if (( trade.profitTake2 == false )&&((((QuoteData)quotes[phl.prePos]).high - phl.pullBack.low ) > 25 * PIP_SIZE ))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							trade.targetPrice = data.close;
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
							
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}
							
					}

					//logger.warning(symbol + " exit highs detected at  " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).high + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).high + " pullback:" + ((QuoteData)quotes[phl.pullBack.pos]).time +"@" + phl.pullBack.low ); 
					/* not worth fixing
					if ( phl.pullBack.pos == phl.prePos )
						phl.pullBack = Utility.getLow(quotes, phl.prePos+1, phl.curPos);*/
					if (!trade.profitTake1 && ( data.low < phl.pullBack.low ))
					{
						int lastTradeEntryPos = trade.entryPos;
						double lastTradEentryPrice = trade.entryPrice;
						String lastTradeType = trade.type;
						logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);
						
						if ( MODE == Constants.TEST_MODE)
							AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);

						/*
						if ( MODE == Constants.TEST_MODE)
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
						}
						else if ( MODE == Constants.REAL_MODE)
						{	
							trade.targetPrice = phl.pullBack.low;
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
							trade.targetPositionSize = trade.remainingPositionSize;
	
							//trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.targetId = placeMktOrder(Constants.ACTION_SELL, trade.targetPositionSize);
							trade.profitTake1 = true;
						}*/

						// check to see if it worth reversal
						if (lastTradeType.equals(Constants.TRADE_RST) && (phl.curPos - phl.prePos > 2))
						{
							QuoteData highAfterEntry = Utility.getHigh( quotes, lastTradeEntryPos, lastbar);
							
							if ( highAfterEntry.high - lastTradEentryPrice < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
							{
								trade = new Trade(symbol);
								trade.type = Constants.TRADE_CNT;
								trade.action = Constants.ACTION_SELL;
								trade.POSITION_SIZE = POSITION_SIZE;
								trade.lowHighAfterEntry = highAfterEntry;
									
								logger.warning(symbol + " " + CHART + " place market reverse sell order");
								trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE );
								trade.price = phl.pullBack.low;
								trade.entryPrice = trade.price;
								trade.status = Constants.STATUS_PLACED;
								trade.remainingPositionSize=trade.POSITION_SIZE;
								trade.entryTime = ((QuoteData)quotes[lastbar]).time;
								trade.entryPos = lastbar;
								if ( MODE == Constants.TEST_MODE )
									AddTradeOpenRecord( trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.entryPrice);
	
								trade.stop = highAfterEntry.high;
								if ((trade.stop -  trade.entryPrice ) > FIXED_STOP * PIP_SIZE )
									trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;
								
								trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
	
								logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
								trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
							}
						}
						
						return;
					}
				}
			}
		}
	}



	
	
	public void exit123_3_c_bak(QuoteData data)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;

		/*
		// if quick profit target set, cancel target if it has not bee a quick profit
		if (Constants.TRADE_CNT.equals(trade.type) && ( lastbar - trade.entryPos > 8 ) && ( trade.targetReached == false))
		{
			trade.targetPrice = 0;
			trade.targetPositionSize = 0;
			
			if ( MODE == Constants.REAL_MODE)
				cancelOrder( trade.targetId);
		}*/

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.entryPos + 2 > lastbar )
				return;

			int top;
			
			if (Constants.TRADE_RST.equals(trade.type))
				top = Utility.getHigh( quotes, trade.entryPos, lastbar ).pos;
			else if (Constants.TRADE_CNT.equals(trade.type))
				top = trade.lowHighAfterEntry.pos;
			else
			{
				logger.severe(symbol + " " + CHART + " no trade status set!");
				return;
			}

			
			for ( int i = lastbar; i > top; i--)
			{
				PushHighLow phl = findLastNLow( quotes, top, i, 1 );
				if ( phl != null )
				{
					if ( i == lastbar )
					{
						if (( trade.profitTake2 ==false )&& (phl.pullBack.high - (((QuoteData)quotes[phl.prePos]).low ) > LOCKIN_PROFIT * PIP_SIZE ))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							// large pull back, lock in profit
							trade.targetPrice = data.close;  // TODO: this could be better
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_DOWN);
							
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}
							
					}
					
					//logger.warning(symbol + " exit low detected at  " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).low + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).low + " pullback:" + ((QuoteData)quotes[phl.pullBack.pos]).time +"@" + phl.pullBack.high );
					/* not worth fixing
					if ( phl.pullBack.pos == phl.prePos )
						phl.pullBack = Utility.getHigh(quotes, phl.prePos+1, phl.curPos);*/
					if (!trade.profitTake1 && ( data.high > phl.pullBack.high ))
					{
						logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);
						if ( MODE == Constants.TEST_MODE)
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
							return;
						}
						else if ( MODE == Constants.REAL_MODE)
						{	
							trade.targetPrice = phl.pullBack.high;
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_DOWN);
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake1 = true;
							return;
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.entryPos + 2 > lastbar )
					return;

			int bottom;
			if (Constants.TRADE_RST.equals(trade.type))
				bottom = Utility.getLow( quotes, trade.entryPos, lastbar ).pos;
			else if (Constants.TRADE_CNT.equals(trade.type))
				bottom = trade.lowHighAfterEntry.pos;
			else
			{
				logger.severe(symbol + " " + CHART + " no trade status set!");
				return;
			}

			for ( int i = lastbar; i > bottom; i--)
			{
				PushHighLow phl = findLastNHigh( quotes, bottom, i, 1 );
				if ( phl != null )
				{
					//logger.warning(symbol + " " + CHART + "this high between " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).high + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).high + " pullback@" + phl.pullBack ); 

					if ( i == lastbar )
					{
						if (( trade.profitTake2 ==false )&&((((QuoteData)quotes[phl.prePos]).high - phl.pullBack.low ) > 25 * PIP_SIZE ))
						{
							logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
							trade.targetPrice = data.close;
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
							
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake2 = true;
							return;
						}
							
					}

					//logger.warning(symbol + " exit highs detected at  " + ((QuoteData)quotes[phl.prePos]).time + "@" + ((QuoteData)quotes[phl.prePos]).high + "  -  " + ((QuoteData)quotes[phl.curPos]).time + "@" + ((QuoteData)quotes[phl.curPos]).high + " pullback:" + ((QuoteData)quotes[phl.pullBack.pos]).time +"@" + phl.pullBack.low ); 
					/* not worth fixing
					if ( phl.pullBack.pos == phl.prePos )
						phl.pullBack = Utility.getLow(quotes, phl.prePos+1, phl.curPos);*/
					if (!trade.profitTake1 && ( data.low < phl.pullBack.low ))
					{
						logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);
						if ( MODE == Constants.TEST_MODE)
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
							return;
						}
						else if ( MODE == Constants.REAL_MODE)
						{	
							trade.targetPrice = phl.pullBack.low;
							trade.targetPrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
							trade.targetPositionSize = trade.remainingPositionSize;
	
							trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
							trade.profitTake1 = true;
							return;
						}
					}
				}
			}
		}
	}


	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public static PushHighLow findLastNHigh20( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastHigh = Utility.getHigh(quotes, lastbar - 20, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
		if (( lastbar - lastHigh.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos,lastbar);
			phl.pullBack = Utility.getLow( quotes, lastHigh.pos, lastbar);
			return phl;
		}
		else
			return null;
		
	}

	
	public static PushHighLow findLastNLow20( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastLow = Utility.getLow(quotes, lastbar -20, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
		if (( lastbar - lastLow.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos,lastbar);
			phl.pullBack = Utility.getHigh( quotes, lastLow.pos, lastbar);
			return phl;
		}
		else
			return null;
		
	}


	public static PushHighLow findLastNHigh( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
		if (( lastbar - lastHigh.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos,lastbar);
			phl.pullBack = Utility.getLow( quotes, lastHigh.pos, lastbar); 
			return phl;
		}
		else
			return null;
		
	}

	
	public static PushHighLow findLastNLow( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
		if (( lastbar - lastLow.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos,lastbar);
			phl.pullBack = Utility.getHigh( quotes, lastLow.pos, lastbar); 
			return phl;
		}
		else
			return null;
		
	}

	public static PushHighLow findLastNHigh2( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).high < lastHigh.high )
			return null;  // not the highest
		
		if (( lastbar - lastHigh.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos,lastbar);
			phl.pullBack = Utility.getLow( quotes, lastHigh.pos+1, lastbar);  // we do not want to the first bar to be the pull back
			return phl;
		}
		else
			return null;
		
	}

	
	public static PushHighLow findLastNLow2( Object[] quotes, int start, int lastbar, int n )
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar-1);

		if (((QuoteData)quotes[lastbar]).low > lastLow.low )
			return null;  // not the lowest
		
		if (( lastbar - lastLow.pos ) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos,lastbar);
			phl.pullBack = Utility.getHigh( quotes, lastLow.pos+1, lastbar); // we do not want to the first bar to be the pull back
			return phl;
		}
		else
			return null;
		
	}

	

	void resetHighEntryQualifyPrice( Trade trade, Object[] quotes, int reachEntryQualifyPricePos, int lastbar )
	{
		QuoteData qdata = Utility.getHigh(quotes, reachEntryQualifyPricePos, lastbar);
		trade.entryQualifyPrice = qdata.high;
		trade.entryQualifyPricePos = lastbar;
		trade.entryQualifyCount++;
		
	}

	void resetLowEntryQualifyPrice( Trade trade, Object[] quotes, int reachEntryQualifyPricePos, int lastbar )
	{
		QuoteData qdata = Utility.getLow(quotes, reachEntryQualifyPricePos, lastbar);
		trade.entryQualifyPrice = qdata.low;
		trade.entryQualifyPricePos = lastbar;
		trade.entryQualifyCount++;
		
	}

	
	int findNumLows20 ( Object[] quotes, int begin, int end, int pullbackgap )
	{
		int count = 0;
		while ( end > begin )
		{
			PushHighLow phl = findLastNLow20( quotes, begin, end, pullbackgap );
			if ( phl != null )
			{	
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}
		
		return count;
	}
		
	int findNumHighs20 ( Object[] quotes, int begin, int end, int pullbackgap )
	{
		int count = 0;
		while ( end > begin )
		{
			PushHighLow phl = findLastNHigh20( quotes, begin, end, pullbackgap );
			if ( phl != null )
			{	
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}
		
		return count;
	}

	
	int findNumLows ( Object[] quotes, int begin, int end, int pullbackgap )
	{
		int count = 0;
		while ( end > begin )
		{
			PushHighLow phl = findLastNLow( quotes, begin, end, pullbackgap );
			if ( phl != null )
			{	
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}
		
		return count;
	}
		
	int findNumHighs ( Object[] quotes, int begin, int end, int pullbackgap )
	{
		int count = 0;
		while ( end > begin )
		{
			PushHighLow phl = findLastNHigh( quotes, begin, end, pullbackgap );
			if ( phl != null )
			{	
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}
		
		return count;
	}

	private int checkTradeHistoy( String action )
	{
		int count= 0;
		Iterator it = tradeHistory.iterator();
		while ( it.hasNext())
		{
			Trade t = (Trade) it.next();
			if ( t.action.equals(action))
				count++;
		}
		
		return count;
	}

	
	public QuoteData findLastPullBackLow ( Object[] quotes, int begin, int end )
	{
		int pos = end;
		
		for ( int i = end; i > begin; i-- )
		{
			if (((QuoteData)quotes[pos-1]).low > ((QuoteData)quotes[pos]).low)
			{
				return Utility.getLow(quotes, pos, end );
			}
		}
		
		return null;
	}

	public QuoteData findLastPullBackHigh ( Object[] quotes, int begin, int end )
	{
		int pos = end;
		
		for ( int i = end; i > begin; i-- )
		{
			if (((QuoteData)quotes[pos-1]).high < ((QuoteData)quotes[pos]).low)
			{
				return Utility.getHigh(quotes, pos, end );
			}
		}
		
		return null;
	}



	private boolean checkLargeTimeFrameForShort(Object[] quotesL)
	{
		if (larger_timeframe_restriction == true )
		{
			double[] ema20L = Indicator.calculateEMA(quotesL, 20);
			double[] ema50L = Indicator.calculateEMA(quotesL, 50);

			/*
			int lastbarL = quotesL.length - 1;
			if (ema20L[lastbarL]< ema50L[lastbarL])
			{
				logger.info(symbol + " " + CHART + " minute large chart incorrect position");
				return null;
			}
			
			int lastCrossUp = Pattern.findLastMACrossUp( ema20L, ema50L, 5 );
			if ( lastbarL - lastCrossUp < 50 )
			{
				logger.info(symbol + " " + CHART + " minute large chart cross up too soon");
				return null ;
			}*/

			int consectUp = Pattern.findLastMAConsectiveUp( ema20L, ema50L, 30 );
			int consectDown = Pattern.findLastMAConsectiveDown( ema20L, ema50L, 30 );
			if ( consectUp != Constants.NOT_FOUND)
				logger.info(symbol + " " + CHART + " consective up position is " + ((QuoteData)quotesL[consectUp]).time);
			if ( consectDown != Constants.NOT_FOUND)
				logger.info(symbol + " " + CHART + " consective down position is " + ((QuoteData)quotesL[consectDown]).time);

			if ( consectDown < consectUp )
			{
				logger.info(symbol + " " + CHART + " consective up is more recent, trend is up, disqualify");
				return false;
			}
			
			/*
			double[] ema20L = Indicator.calculateEMA(quotesL, 20);
			int lastbarL = ema20L.length - 1;
			if (ema20L[lastbarL] < ema20L[lastbarL-8])
				return true;
			else
				return false;*/
		}

		return true;   // not checking large time frame
	}

	
	private boolean checkLargeTimeFrameForLong(Object[] quotesL)
	{
		if (larger_timeframe_restriction == true )
		{
			double[] ema20L = Indicator.calculateEMA(quotesL, 20);
			double[] ema50L = Indicator.calculateEMA(quotesL, 50);

			/*
			int lastbarL = quotesL.length - 1;
			
			if (ema20L[lastbarL]> ema50L[lastbarL])
			{
				logger.info(symbol + " " + CHART + " minute large chart down incorrect position");
				return null;
			}
			
			int lastCrossDown = Pattern.findLastMACrossDown( ema20L, ema50L, 5 );
			if ( lastbarL - lastCrossDown < 50 )
			{
				logger.info(symbol + " " + CHART + " minute large chart cross down too soon");
				return null ;
			}*/
			
			int consectUp = Pattern.findLastMAConsectiveUp( ema20L, ema50L, 30 );
			int consectDown = Pattern.findLastMAConsectiveDown( ema20L, ema50L, 30 );
			if ( consectUp != Constants.NOT_FOUND)
				logger.info(symbol + " " + CHART + " consective up position is " + ((QuoteData)quotesL[consectUp]).time);
			if ( consectDown != Constants.NOT_FOUND)
				logger.info(symbol + " " + CHART + " consective down position is " + ((QuoteData)quotesL[consectDown]).time);
			// needs to be consective up to make it down trend
			if ( consectDown > consectUp )
			{
				logger.info(symbol + " " + CHART + " consective down is more recent, trend is down, disqualify");
				return false;
			}
			
			/*
			double[] ema20L = Indicator.calculateEMA(quotesL, 20);
			int lastbarL = ema20L.length - 1;
			if (ema20L[lastbarL] > ema20L[lastbarL-8])
				return true;
			else
				return false;*/
		}

		return true;   // not checking large time frame
	}

	
	private boolean findTrendlineBreakToUp( QuoteData data, Object[] quotes)
	{
		int lastbar = quotes.length -1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		// if there is a ma cross over, then the cross direction is the new direction
		int lastConsectiveUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 72);
		int lastConsectiveDown = Pattern.findLastMAConsectiveDown(ema20, ema50, 72);
		
		// make sure I dont get caught up on a major reversal
		if (lastConsectiveDown > lastConsectiveUp )
		{
			// the last trend is down
			if ( data.close > ema20[lastbar])
			{	
				for ( int i = lastbar; i > 0; i--)
				{
					if ( ((QuoteData)quotes[i]).low > ema20[i])
					{
						int pos = i;
						while(((QuoteData)quotes[pos]).low < ema20[pos])
							pos--;
						
						int pos2 = pos;
						while (((QuoteData)quotes[pos2]).low > ema20[pos])
							pos2--;
						
						// find the last highest point
						QuoteData lastHigh = Utility.getHigh(quotes, pos2, pos);
						if ( data.close > lastHigh.high)
							return true;
					}
				}
			}
		}

		return false;
		
	}

	
	private boolean findTrendlineUpBreakDown( QuoteData data, Object[] quotes)
	{
		int lastbar = quotes.length -1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		// if there is a ma cross over, then the cross direction is the new direction
		int lastConsectiveUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 72);
		int lastConsectiveDown = Pattern.findLastMAConsectiveDown(ema20, ema50, 72);
		
		// make sure I dont get caught up on a major reversal
		if (lastConsectiveUp > lastConsectiveDown )
		{
			logger.warning(symbol + " " + CHART + " larger timeframe check - up trend, check for break down") ;
			// the last trend is up
			if ( data.close < ema20[lastbar])
			{	
				logger.warning(symbol + " " + CHART + " larger timeframe check - close below 20MA") ;
				// the last bar is down, so to verify not to caught up a downside 1-2-3 break
				for ( int i = lastbar; i > 0; i--)
				{
					if ( ((QuoteData)quotes[i]).low < ema20[i])
					{
						int pos = i;
						while(((QuoteData)quotes[pos]).low < ema20[pos])
							pos--;
						
						int pos2 = pos;
						while (((QuoteData)quotes[pos2]).low > ema20[pos])
							pos2--;

						logger.warning(symbol + " " + CHART + " larger timeframe check - last low is between close below 20MA") ;
						
						// find the last highest point
						QuoteData lastHigh = Utility.getHigh(quotes, pos2, pos);
						if ( data.close > lastHigh.high)
							return true;
					}
				}
			}
		}

		return false;
		
	}


	
	//private Vector<QuoteData> findTrendlineDownBreakingToUpSide( Object[] quotes, int begin, int end)
	private void findTrendlineDownBreakingToUpSide( Object[] quotes, int begin, int end)
	{
	    /*
		Reversal_123 reversal123 = new Reversal_123( symbol, logger );

		int lastbar = quotes.length -1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		Reversal123 r = reversal123.calculateUp123_20MA( quotes, ema20);
		if ( r != null )
		{	
			logger.info(symbol + " reversal 123 UP start at " + ((QuoteData) quotes[r.startpos]).time);
			logger.info(symbol + " reversal 123 UP support at " + ((QuoteData) quotes[r.supportpos]).time);
			logger.info(symbol + " reversal 123 UP break at " + ((QuoteData) quotes[r.breakpos]).time);

		}
		
		return true;*/

		/*
		Vector peaks = new Vector();
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int pos = end;
		while ( pos > begin )
		{
			int peakbegin = pos;
			int peakend = pos;

			while (peakbegin > begin + 1)
			{
				if ((((QuoteData) quotes[peakbegin]).high > ema20[peakbegin]) &&
				  (((QuoteData) quotes[peakbegin-1]).high < ema20[peakbegin-1]))
				{
					// turning point to down
					peakbegin = peakbegin -1;
					QuoteData p = Utility.getHigh(quotes, peakbegin, peakend);
					peaks.add(p);
					break;
				}
				peakbegin--;
			}
				
			pos = peakbegin -1;
			
		}
			
		return peaks;
			*/
		int lastbar = quotes.length -1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		
		TrendLine reversal_123 = new TrendLine(symbol, logger);
		
		/*
		for ( int i = lastbar-20; i < lastbar - 2; i++)
		{
			Reversal123 r1 = reversal_123.calculateDown123_20MA_2(quotes, i, ema20, ema50);
			Reversal123 r2 = reversal_123.calculateDown123_20MA_2(quotes, i+1, ema20, ema50);
			Reversal123 r3 = reversal_123.calculateDown123_20MA_2(quotes, i+2, ema20, ema50);

			if (( r1.a == r2.a ) && ( r2.a != r3.a ))
			{
			//	logger.info( symbol + " " + CHART + " down trend line - start point:" + ((QuoteData)quotes[r.startpos]).time + " a:" + r.a);
				logger.info( symbol + " " + CHART + " down trend line break detect at " + ((QuoteData)quotes[i+2]).time );
			}
		}*/
		Reversal123 r1 = reversal_123.calculateDown123_20MA_2(quotes, lastbar, ema20, ema50);
		if ( r1 != null )
		{
			for ( int i = lastbar-1; i > lastbar - 50; i--)
		{
			Reversal123 r0 = reversal_123.calculateDown123_20MA_2(quotes, i, ema20, ema50);
			if (r0 != null )
			//if ( r1 != null )
			//logger.info( symbol + " " + CHART + " a" + "=" + r1.a + " " + ((QuoteData)quotes[i]).time);
			if ( r1.a != r0.a )
			{
				logger.info( symbol + " " + CHART + " trend change detected at " +  ((QuoteData)quotes[i]).time);
				return;
			}
		}
		}
		
	}
	
	
	Object[] findLastNPushesHigh( Object[] quotes, int start, int width )
	{
		int lastbar = quotes.length-1;
		
		PushHighLow phl_cur = findLastNHigh( quotes, start, lastbar, width );
		if ( phl_cur == null )
			return null;
		
		Vector<PushHighLow> highlows = new Vector<PushHighLow>();
		highlows.add(phl_cur);
		
		int pos = phl_cur.prePos;
		while ( pos > start )
		{	
			for ( int i = pos; i > start; i--)
			{	
				PushHighLow phl = findLastNHigh( quotes, start, i, width);
				if ( phl != null )
				{
					highlows.add(phl);
					pos = phl.prePos;
					continue;
				}
			}
		}
		
		return highlows.toArray();
	}
	

	Object[] findLastNPushesLow( Object[] quotes, int start, int width )
	{
		int lastbar = quotes.length-1;
		
		PushHighLow phl_cur = findLastNLow( quotes, start, lastbar, width );
		if ( phl_cur == null )
			return null;
		
		Vector<PushHighLow> highlows = new Vector<PushHighLow>();
		highlows.add(phl_cur);
		
		int pos = phl_cur.prePos;
		while ( pos > start )
		{	
			for ( int i = pos; i > start; i--)
			{	
				PushHighLow phl = findLastNLow( quotes, start, i, width);
				if ( phl != null )
				{
					highlows.add(phl);
					pos = phl.prePos;
					continue;
				}
			}
		}
		
		return highlows.toArray();
	}

	@Override
	public boolean inExitingTime(String time)
	{
		// TODO Auto-generated method stub
		return false;
	}

	
	
}
