package tao.trading.strategy;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
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
import tao.trading.Peak;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.strategy.util.Utility;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.TradeOrder;
import com.ib.client.TradeOrder2;
import com.ib.client.TradeRecord;



public class SFHistory2TradeManager extends TradeManager2 implements Serializable
{
	HashMap<String, QuoteData> qts = new HashMap<String, QuoteData>(1000);
	int MODE = Constants.REAL_MODE;
	boolean CNT= false; 
	int CHART;
	int TARGET_PROFIT_SIZE;
	double PIP_SIZE;
	boolean oneMinutChart = false;
	int POSITION_SIZE;
	double FIXED_STOP;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	Vector<Trade> tradeHistory = new Vector<Trade>();
	String portofolioReport = null;
	double totalProfit = 0;
	String currentTime;
	Vector<TradeOrder2> tradeOrders = new Vector<TradeOrder2>();
	double exchangeRate;
	String currency;
	int DEFAULT_RETRY = 1;
	boolean test = false;
	
	public SFHistory2TradeManager()
	{
		super();
	}
	
	public SFHistory2TradeManager(String account, EClientSocket client, Contract contract, int symbolIndex,  Logger logger)
	{
		super( account, client, contract, symbolIndex, logger );
		
	   // m_client.reqMktData(199, m_contract, null, false);

	}

	public Object[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
	    Arrays.sort(quotes);
	    return quotes;
	}


	//////////////////////////////////////////////////////////////////////////////////////
	public int placeMktOrder( String action, int quantity)
	{
		if ( MODE == Constants.TEST_MODE )
			return 97;

		logger.warning( symbol + " " + CHART + " MIN " + " to place market " + action + " order");
		return super.placeMktOrder(action, quantity);
			
	}
	
	public int placeStopOrder( String action, double stopPrice, int posSize, String ocaGroup)
	{
		if ( MODE == Constants.TEST_MODE )
			return 99;

		logger.warning(symbol + " " + CHART + " MIN " + " to place stop " + action + " order "  + posSize + " " + stopPrice);
		return super.placeStopOrder(action, stopPrice, posSize, ocaGroup);

	}

	public int placeLmtOrder(String action, double limitPrice, int posSize, String ocaGroup)
	{
		if ( MODE == Constants.TEST_MODE )
			return 98;

		logger.warning( symbol + " " + CHART + " MIN " + " to place limit " + action + " order "  + posSize + " " + limitPrice);
		return super.placeLmtOrder( action, limitPrice, posSize, ocaGroup);
	}
	///////////////////////////////////////////////////////////////////////////////////

	
	
	
	public void closePositionByMarket( int posSize, double currentPrice )
	{
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
	
		// this is to place the real order
		placeMktOrder(action, posSize);

		trade.remainingPositionSize -= posSize;
		
		if ( trade.remainingPositionSize == 0 )
		{
			if ( trade.stopId != 0 )
				cancelOrder(trade.stopId);
			tradeHistory.add(trade);
			trade = null;
		}
		
	}
	
	public void closePositionByLimit( double limitPrice, int positionSize)
	{
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.closeId = placeLmtOrder( action, limitPrice, positionSize, null);
		
	}
	
	public void cancelOrder( int orderId)
	{
		if ( MODE == Constants.REAL_MODE )
		{
			logger.warning( symbol + " " + CHART + " MIN " + " cancel order "  + orderId );
			super.cancelOrder(orderId);
		}
	}
	


/*	private void placeMarketSellOrder(double price)
	{
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.positionSize, price);
		trade.price = price;
		trade.entryPrice = price;
		trade.status = Constants.STATUS_PLACED;
		trade.position = Constants.POSITION_SHORT;
		trade.remainingPositionSize=trade.positionSize;
		return;

	}
	
	private void placeMarketBuyOrder(double price)
	{
		trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.positionSize, price);
		trade.price = price;
		trade.entryPrice = price;
		trade.status = Constants.STATUS_PLACED;
		trade.position = Constants.POSITION_LONG;
		trade.remainingPositionSize=trade.positionSize;
		return;

	}
*/
	
	
	
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
			if (trade.stopId == orderId )
			{
				logger.warning(symbol + " " + CHART + " minute " + filled + " stopped out " );
				trade.stoppedOutPos = ((QuoteData)quotes[lastbar]).pos;
				trade.stopId = 0;
					
				cancelTarget();

				processAfterHitStopLogic();
			}
			else if (trade.followUpId == orderId )
			{
				logger.warning(symbol + " " + CHART + " minute " + filled + " follow up position filled " );
				trade.remainingPositionSize += trade.followUpPositionSize;
				trade.followUpTrade = true;
				trade.stopStatus = Constants.STOPSTATUS_EXTEND1;
			}
			else if ( orderId == trade.targetId )  //TODO:  need to have take profit 1 id, and take profit 2 Id so they do not mix up the previous targets  gbp.cas
			{
				logger.warning(symbol + CHART + " target filled, " + filled + " closed @ " + trade.targetPrice);
				trade.targetReached = true;
				trade.remainingPositionSize -= trade.targetPositionSize;
				trade.targetId = 0;
					
				cancelOrder( trade.stopId );
				if ( trade.remainingPositionSize > 0 )
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				else  	
					trade = null;
			}
			else if ( orderId == trade.takeProfit2Id ) 
			{
				logger.warning(symbol + CHART + " take profit 2 filled ");
				trade.targetReached = true;
				trade.remainingPositionSize -= trade.takeProfit2PosSize;
				trade.takeProfit1Id = 0;
					
				cancelOrder( trade.stopId );
				if ( trade.remainingPositionSize > 0 )
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				else  	
					trade = null;
			}
		}
	}
	
	
	
	public void checkStopTarget(QuoteData data)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check stop;
			if (( trade.stop != 0 ) && (data.high > trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out " + data.time );
				trade.stoppedOutPos = lastbar;
				AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
				placeMktOrder( Constants.ACTION_BUY, trade.remainingPositionSize);
				trade.stop = 0;

				cancelTarget();

				processAfterHitStopLogic();
				return;
			}

			if (( trade.followUpPrice != 0 ) && (data.high > trade.followUpPrice))
			{
				logger.warning(symbol + " " + CHART + " minute follow up position added " + data.time );
				trade.remainingPositionSize += trade.followUpPositionSize;
				AddOpenRecord( data.time, Constants.ACTION_SELL, trade.followUpPositionSize, data.close);
				trade.stopStatus = Constants.STOPSTATUS_EXTEND1;
				return;
			}

			// check target;
			if ((trade.targetPrice != 0) && ( data.low < trade.targetPrice ))
			{
				logger.info(symbol + " target hit, close " + trade.targetPositionSize + " @ " + trade.targetPrice);
				AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.targetPositionSize, data.close);
				closePositionByMarket( trade.targetPositionSize, trade.targetPrice);
				if ( trade != null )
				{
					trade.targetReached = true;
					trade.targetPrice = 0;
				}
				else
					return;
			}
			
			if ((trade.takeProfit2Price != 0) && ( data.low < trade.takeProfit2Price ))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PosSize + " @ " + trade.takeProfit2Price);
				AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.takeProfit2PosSize, data.close);
				closePositionByMarket( trade.targetPositionSize, trade.targetPrice);
				// should have everything closed
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (( trade.stop != 0 ) && (data.low < trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out " + data.time );
				trade.stoppedOutPos = lastbar;
				AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
				placeMktOrder( Constants.ACTION_SELL, trade.remainingPositionSize);
				trade.stop = 0;
				
				cancelTarget();

				processAfterHitStopLogic();
				return;
			}
				
			if (( trade.followUpPrice != 0 ) && (data.low <= trade.followUpPrice))
			{
				logger.warning(symbol + " " + CHART + " minute follow up position added " + data.time );
				trade.remainingPositionSize += trade.followUpPositionSize;
				AddOpenRecord( data.time, Constants.ACTION_BUY, trade.followUpPositionSize, data.close);
				trade.stopStatus = Constants.STOPSTATUS_EXTEND1;
				return;
			}

			if ((trade.targetPrice != 0) && ( data.high > trade.targetPrice ))
			{
				logger.info(symbol + " target hit, close " + trade.targetPositionSize + " @ " + trade.targetPrice);
				AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.targetPositionSize, data.close);
				this.closePositionByMarket( trade.targetPositionSize, trade.targetPrice);
				if ( trade != null )
				{
					trade.targetReached = true;
					trade.targetPrice = 0;
				}
				else
					return;
			}

			if ((trade.takeProfit2Price != 0) && ( data.high > trade.takeProfit2Price ))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PosSize + " @ " + trade.takeProfit2Price);
				AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.takeProfit2PosSize, data.close);
				closePositionByMarket( trade.targetPositionSize, trade.targetPrice);
				// should have everything closed
			}

		}
	}

	void cancelTarget()
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

	void cancelStop()
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
	
	
	void processAfterHitStopLogic()
	{
		if ( trade.status.equals(Constants.STATUS_EXITING))
		{
			tradeHistory.add(trade);
			this.trade = null;
			return;
		}

		Object[] quotes = getQuoteData();
		int lastbar = quotes.length -1;
		QuoteData lastData = (QuoteData)quotes[lastbar];

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (( trade.moveStop1 == false ) && ( trade.moveStop2 == false )&& ( trade.targetReached == false ))
			{
				// reverse criteria 1:  no big pull backs during the trigger
				// reverse criteria 2:  shallow pull back
			/*
				int pushs = findNumLows( quotes, trade.pullBackPos, trade.entryPos, 3 );
				if	( pushs < 1 )
				{
					double highAfterEntry = Utility.getHigh( quotes, trade.entryPos, lastbar).high;
					
					if ( highAfterEntry < ((QuoteData)quotes[trade.pullBackPos]).high)
					{
						trade.type = Constants.TRADE_CNT;
						trade.action = Constants.ACTION_SELL;
						trade.positionSize = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.reEnter = 0;
						
						logger.warning(symbol + " " + CHART + " place market sell reversal order");
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.positionSize, data.close);
						trade.price = data.close;
						trade.entryPrice = data.close;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize=trade.positionSize;
						trade.entryTime = ((QuoteData)quotes[lastbar]).time;
						trade.entryPos = lastbar;
						if ( MODE == Constants.TEST_MODE )
							AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.positionSize, data.close);

						trade.stop = highAfterEntry;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.positionSize, null);//new Long(oca).toString());

						// calculate and place target order
						trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
						trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize = trade.positionSize/2;
						logger.warning(symbol + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());

						return;
					}
				}*/

				
				if ( trade.reEnter > 0 )
				{
					logger.warning(symbol + " " + CHART + " minute to re-enter" );
					trade.status = Constants.STATUS_STOPPEDOUT;
					trade.stopId = 0;  // so it does not get hit multiple times
					trade.reEnter--;

					trade.entryQualifyPrice = ((QuoteData)lastData).high;
					trade.entryQualifyPricePos = lastbar;
					
					return;
				}
				else
				{
					logger.warning(symbol + " " + CHART + " minute maximum retry reached, exit trade" );
					tradeHistory.add(trade);
					this.trade = null;
					return;
				}
			}
			else
			{
				logger.warning(symbol + " " + CHART + " already moved stop, exit trade");
				this.trade = null;
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action)) 
		{
			if (( trade.moveStop1 == false ) && ( trade.moveStop2 == false )&& ( trade.targetReached == false ))
			{
				// reverse criteria 1:  no big pull backs during the trigger
				// reverse criteria 2:  shallow pull back 
				/*
				int pushs = findNumLows( quotes, trade.pullBackPos, trade.entryPos, 3 );
				if	( pushs < 1 )
				{
					double highAfterEntry = Utility.getHigh( quotes, trade.entryPos, lastbar).high;
					
					if ( highAfterEntry < ((QuoteData)quotes[trade.pullBackPos]).high)
					{
						trade.type = Constants.TRADE_CNT;
						trade.action = Constants.ACTION_SELL;
						trade.positionSize = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.reEnter = 0;
						
						logger.warning(symbol + " " + CHART + " place market sell reversal order");
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.positionSize, data.close);
						trade.price = data.close;
						trade.entryPrice = data.close;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize=trade.positionSize;
						trade.entryTime = ((QuoteData)quotes[lastbar]).time;
						trade.entryPos = lastbar;
						if ( MODE == Constants.TEST_MODE )
							AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.positionSize, data.close);

						trade.stop = highAfterEntry;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.positionSize, null);//new Long(oca).toString());

						// calculate and place target order
						trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
						trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize = trade.positionSize/2;
						logger.warning(symbol + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());

						return;
					}
				}
*/
				
				if ( trade.reEnter > 0 )
				{
					logger.warning(symbol + " " + CHART + " minute to re-enter" );
					trade.status = Constants.STATUS_STOPPEDOUT;
					trade.stopId = 0;  // so it does not get hit multiple times
					trade.reEnter--;
					
					trade.entryQualifyPrice = ((QuoteData)lastData).high;
					trade.entryQualifyPricePos = lastbar;
					trade.POSITION_SIZE = trade.POSITION_SIZE * 2;
					trade.entryResets = 1;

					return;
				}
				else
				{
					logger.warning(symbol + " " + CHART + " minute maximum retry reached, exit trade" );
					tradeHistory.add(trade);
					this.trade = null;
					return;
				}
			}
			else
			{
				logger.warning(symbol + " " + CHART + " minute already moved stop, trade to close" );
				tradeHistory.add(trade);
				this.trade = null;
				return;
			}
		}
		else
		{
			logger.warning(symbol + " " + CHART + " already moved stop, exit trade");
			this.trade = null;
		}
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	//	Entry Setups
	//
	//
	////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public Trade checkHigherHighSetup( QuoteData data, int minCrossBar )
	{
		return checkRSTSetup( data, minCrossBar );
		//return checkHigherHighSetup( data, qts, chart, 0 );
	}
	
	public Trade checkHigherHighSetup( QuoteData data, Vector<QuoteData> qts, int chart, int minCrossBar )
	{
		double[] ema20 = Indicator.calculateEMA(qts, 20);
		double[] ema50 = Indicator.calculateEMA(qts, 50);
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length - 1;
		
		Utility.setQuotePositions( quotes );

		if (ema20[lastbar] > ema50[lastbar])
		{
			int maCrossOverPos;
			if ( minCrossBar > 0 )
				maCrossOverPos = Pattern.findLastMACrossUp( ema20, ema50, minCrossBar );
			else
				maCrossOverPos = Pattern.findLastMACrossUp( ema20, ema50 );
				
			if ( maCrossOverPos == Constants.NOT_FOUND )
				return null;
			
			//logger.info(symbol + " 20/40 cross over up at " + ((QuoteData) quotes[maCrossOverPos]).time);
			
			Vector<Peak> peaks = findHigherHigh4( quotes, maCrossOverPos - 5, lastbar);  // just to make sure there are no peaks before
			
			//if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback == 0) && (peaks.lastElement().highlowQuoteData.size() == 1))
			if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback == 0) && (peaks.lastElement().highlow > peaks.elementAt(peaks.size()-2).highlow))
			{	
				logger.info(symbol + " peaksize123=" + peaks.lastElement().highlowQuoteData.size());
				int lastpeak = peaks.size() -1;
				
				for ( int i = 0; i <= lastpeak; i++)
				{
					Peak p = peaks.elementAt(i);
					logger.info(symbol + " i = " + i );
					logger.info(symbol + " p.highlowpos = " + p.highlowpos );
					logger.info(symbol + " p.highlowpullbackpos = " + p.pullbackpos );
					logger.info(symbol + " Higher High at " + ((QuoteData) quotes[p.highlowpos]).time + " @ " + p.highlow);
					logger.info(symbol + " Higher High pull back at " + ((QuoteData) quotes[p.pullbackpos]).time + " @" + p.pullback);
					Iterator it = p.highlowQuoteData.iterator();
					while ( it.hasNext())
					{
						QuoteData q = (QuoteData)it.next();
						logger.info(symbol + " highs at " + q.time + " " + q.high + " " + q.pos );
					}
					it = p.pullbackQuoteData.iterator();
					while ( it.hasNext())
					{
						QuoteData q = (QuoteData)it.next();
						logger.info(symbol + " high pullbacks at " + q.time + " " + q.pos + " @ " + q.low );
					}
				}

				
				if (( peaks.elementAt(lastpeak-1).pullback < peaks.elementAt(lastpeak-2).pullback ) 
					&& (( peaks.lastElement().highlow - peaks.elementAt(lastpeak-1).pullback ) > 10  * PIP_SIZE))
				{
					logger.warning(symbol + " " + chart + " minute Higher high break up SELL at " + data.time );
					trade = new Trade(symbol);
					trade.type = Constants.TRADE_RST;
					trade.action = Constants.ACTION_SELL;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.detectTime = ((QuoteData) qts.lastElement()).time;
					trade.status = Constants.STATUS_OPEN;
					trade.reEnter = 1;
					return trade;
				}
			}
				/*
				if ( peaks.elementAt(lastpeak).pullback < peaks.elementAt(lastpeak-1).pullback )
				{
					// pull back  half 
					Peak peak = peaks.elementAt(lastpeak);
					QuoteData lastQuote = (QuoteData)peak.pullbackQuoteData.lastElement();
					if ( lastQuote.close > (peak.pullback + (peak.highlow - peak.pullback)*0.318))
					{
						System.out.println(symbol + " " + chart + " minute Higher high break up SELL at " + data.time );
						logger.info(symbol + " " + chart + " minute Higher high break up SELL at " + data.time ); 
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_HIGHERHIGHLOWERLOW;
						trade.action = Constants.ACTION_SELL;
						trade.positionSize = positionSize;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.status = Constants.STATUS_OPEN;
						trade.reEnter = 2;
						return trade;
					}
				}*/
		}
		else if (ema20[lastbar] < ema50[lastbar])
		{
			int maCrossOverPos;
			if ( minCrossBar > 0 )
				maCrossOverPos = Pattern.findLastMACrossDown( ema20, ema50, minCrossBar );
			else
				maCrossOverPos = Pattern.findLastMACrossDown( ema20, ema50 );

			if ( maCrossOverPos == Constants.NOT_FOUND )
				return null;

			//logger.info(symbol + " 20/40 cross over down at " + ((QuoteData) quotes[maCrossOverPos]).time);

			Vector<Peak> peaks = findLowerLow4( quotes, maCrossOverPos-5, lastbar);  // just to make sure there are no peaks before
			
			//if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback == 0)  && (peaks.lastElement().highlowQuoteData.size() == 1))
			if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback == 0) && (peaks.lastElement().highlow < peaks.elementAt(peaks.size()-2).highlow))
			{	
				int lastpeak = peaks.size() -1;

				for ( int i = 0; i <= lastpeak; i++)
				{
					Peak p = peaks.elementAt(i);
					logger.info(symbol + " i = " + i );
					logger.info(symbol + " p.lowpos = " + p.highlowpos );
					logger.info(symbol + " p.lowpullbackpos = " + p.pullbackpos );
					logger.info(symbol + " Lower Low at " + ((QuoteData) quotes[p.highlowpos]).time +  " @ " + p.highlow);
					logger.info(symbol + " Lower Low pull back at " + ((QuoteData) quotes[p.pullbackpos]).time + " @" + p.pullback);
					Iterator it = p.highlowQuoteData.iterator();
					while ( it.hasNext())
					{
						QuoteData q = (QuoteData)it.next();
						logger.info(symbol + " lows at " + q.time + " " + q.pos );
					}
					it = p.pullbackQuoteData.iterator();
					while ( it.hasNext())
					{
						QuoteData q = (QuoteData)it.next();
						logger.info(symbol + " low pullbacks at " + q.time + " " + q.pos + " @ " + q.high);
					}
				}

				if (( peaks.elementAt(lastpeak-1).pullback > peaks.elementAt(lastpeak-2).pullback )
					&& (( peaks.elementAt(lastpeak-1).pullback - peaks.lastElement().highlow ) > 10  * PIP_SIZE))
				{
					logger.warning(symbol + " " + chart + " minute Lower low break up BUY at " + data.time ); 
					trade = new Trade(symbol);
					trade.type = Constants.TRADE_RST;
					trade.action = Constants.ACTION_BUY;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.detectTime = ((QuoteData) qts.lastElement()).time;
					trade.status = Constants.STATUS_OPEN;
					trade.reEnter = 1;
					return trade;
				}
			}
		}
		
		return null;

	}

	

	
	public Trade checkRSTSetup( QuoteData data, int minCrossBar )
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int SecondWidth = 8;
		int FirstWidth = 5;
		int BufferWidth = 5;
		
		if ( CHART == Constants.CHART_1_MIN )
		{
			SecondWidth = 12;
			FirstWidth = 8;
		}

		if (ema20[lastbar] > ema50[lastbar])
		{
			int maCrossOverPos;
			if ( minCrossBar > 0 )
				maCrossOverPos = Pattern.findLastMACrossUp( ema20, ema50, minCrossBar );
			else
				maCrossOverPos = Pattern.findLastMACrossUp( ema20, ema50 );
				
			if ( maCrossOverPos == Constants.NOT_FOUND )
				return null;
			//logger.info(symbol + " 20/50 cross over up at " + ((QuoteData) quotes[maCrossOverPos]).time);
			
			if (( CHART == Constants.CHART_1_MIN ) && ( lastbar - maCrossOverPos < 60 ))
				return null;

			if (( CHART == Constants.CHART_5_MIN ) && ( lastbar - maCrossOverPos < 40 ))
				return null;
			
			//logger.info(symbol + " 20/50 cross over up at " + ((QuoteData) quotes[maCrossOverPos]).time);
			PushHighLow phl_cur = findLastNHigh20( quotes, maCrossOverPos, lastbar, SecondWidth );
			if ( phl_cur == null )
				return null;
			
			int preHigh = phl_cur.prePos;
			for ( int i = preHigh; i > maCrossOverPos - BufferWidth; i--)
			{	
				PushHighLow phl_pre = findLastNHigh20( quotes, maCrossOverPos - BufferWidth, i, FirstWidth );
				if ( phl_pre != null )
				{
					if (( phl_cur.pullBack.low < phl_pre.pullBack.low) && (((QuoteData)quotes[phl_cur.curPos]).high > ((QuoteData)quotes[phl_pre.curPos]).high ))                      // && ((((QuoteData)quotes[phl_pre.curPos]).high - phl_pre.pullBack.low) > 2 * avgBarSize ))
					{	
						logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );
						logger.warning("this high between " + ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " + ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" + phl_cur.pullBack ); 
						logger.warning("last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@" + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotes[phl_pre.curPos]).time + "@" + ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.detectPrice = ((QuoteData)quotes[lastbar]).close;
						//trade.detectPos = lastbar;
						trade.pullBackPos = phl_cur.pullBack.pos;
						trade.status = Constants.STATUS_OPEN;
						trade.reEnter = DEFAULT_RETRY;
						trade.entryQualifyPrice = ((QuoteData)quotes[phl_cur.prePos]).high;
						trade.entryQualifyPricePos = phl_cur.curPos;
						return trade;
					}
					return null;
				}
			}
		}
		else if (ema20[lastbar] < ema50[lastbar])
		{
			int maCrossOverPos;
			if ( minCrossBar > 0 )
				maCrossOverPos = Pattern.findLastMACrossDown( ema20, ema50, minCrossBar );
			else
				maCrossOverPos = Pattern.findLastMACrossDown( ema20, ema50 );

			if ( maCrossOverPos == Constants.NOT_FOUND )
				return null;

			if (( CHART == Constants.CHART_1_MIN ) && ( lastbar - maCrossOverPos < 30 ))
				return null;

			if (( CHART == Constants.CHART_5_MIN ) && ( lastbar - maCrossOverPos < 20 ))
				return null;

			//logger.info(symbol + " 20/50 cross over up at " + ((QuoteData) quotes[maCrossOverPos]).time);
			PushHighLow phl_cur = findLastNLow20( quotes, maCrossOverPos, lastbar, SecondWidth );
			if ( phl_cur == null )
				return null;
	
			int preLow = phl_cur.prePos;
			for ( int i = preLow; i > maCrossOverPos - 5; i--)
			{	
				PushHighLow phl_pre = findLastNLow20( quotes, maCrossOverPos - BufferWidth, i, FirstWidth );
				if ( phl_pre != null )
				{
					//double avgBarSize = Utility.getAverage(quotes, lastbar-30, lastbar);
					if (( phl_cur.pullBack.high > phl_pre.pullBack.high) && (((QuoteData)quotes[phl_cur.curPos]).low < ((QuoteData)quotes[phl_pre.curPos]).low ))// && ((phl_pre.pullBack.high - (((QuoteData)quotes[phl_pre.curPos]).low ) > 2 * avgBarSize )))
					{	
						logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );
						logger.warning(symbol + " this low between " + ((QuoteData)quotes[phl_cur.prePos]).time + "-" + ((QuoteData)quotes[phl_cur.curPos]).time + " pullback@" + phl_cur.pullBack ); 
						logger.warning(symbol + " last low between " + ((QuoteData)quotes[phl_pre.prePos]).time + "-" + ((QuoteData)quotes[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData)quotes[lastbar]).time;
						trade.detectPrice = ((QuoteData)quotes[lastbar]).close;
						//trade.detectPos = lastbar;
						trade.pullBackPos = phl_cur.pullBack.pos;
						trade.status = Constants.STATUS_OPEN;
						trade.reEnter = DEFAULT_RETRY;
						trade.entryQualifyPrice = ((QuoteData)quotes[phl_cur.prePos]).low;
						trade.entryQualifyPricePos = phl_cur.curPos;
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
	public void trackTradeEntry(QuoteData data, Object[] quotesL)
	{
		if ( Constants.TRADE_RST.equals(trade.type))
			trackReveralTradeEntry(data, quotesL);
		//else if ( Constants.TRADE_CNT.equals(trade.type))
		//	trackPullBackTradeEntry(data, qtsS, qtsL);
	}
	
	
	
	public void trackReveralTradeEntry(QuoteData data, Object[] quotesL)
	{
		//logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		int lastbarL = quotesL.length -1;
		

		int detectPos = Utility.getQuotePositionByMinute( quotes, trade.detectTime);

		if ( lastbar == detectPos )	
			return; 	// no new bar yet

		// qualifier 1:  first bar is not too deep
		// qualifier 2:  second bar start to move away
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low) && (((QuoteData)quotes[lastbar]).high < ((QuoteData)quotes[lastbar-1]).high))
			{
				// check how far it went after pass the detect position
				 // TODO: this should also apply to stops, at least once?
				if ( trade.numOfEntry == 0 ) // first time 
				{
					boolean reachedEntryQualifyPrice = false;
					int reachEntryQualifyPricePos = 0;
					for ( int i = trade.entryQualifyPricePos+1; i <= lastbar; i++ )
					{
						if (((QuoteData)quotes[i]).high >= trade.entryQualifyPrice )
						{
							reachedEntryQualifyPrice = true;
							reachEntryQualifyPricePos = i;
							break;
						}
					}
				
					if ( reachedEntryQualifyPrice == false )
						return;

					if ( lastbar - reachEntryQualifyPricePos >= 3 )
					{
						resetHighEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
						trade.entryResets = 1;
						return;
					}

					// close it if reached 10 pips below the trigger price
					if ((trade.detectPrice - ((QuoteData)quotes[lastbar]).close) > 3 * Utility.getAverage(quotes, lastbar-20, lastbar))
					{
						logger.warning(symbol + " " + CHART + " missed the boat at " + ((QuoteData)quotes[lastbar]).time);
						trade = null;
						return;
					}
				}

				
				// calculate and place stop order
				//long oca = new Date().getTime();
				//trade.oca = oca;
				if ( trade.numOfEntry == 0 ) // first time 
				{
					// place order
					logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
					trade.price = data.close;
					trade.entryPrice = data.close;
					trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
					trade.remainingPositionSize=trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData)quotes[lastbar]).time;
					trade.entryPos = lastbar;
					trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
					if ( MODE == Constants.TEST_MODE )
						AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

					trade.stop = data.close + FIXED_STOP * PIP_SIZE;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

					// calculate and place target order
					//trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
					trade.targetPrice = trade.price - FIXED_STOP * PIP_SIZE;
					trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
					trade.targetPositionSize = trade.POSITION_SIZE/2;
					logger.warning(symbol + " " + CHART + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());

					trade.numOfEntry++;
					trade.status = Constants.STATUS_PLACED;
					trade.stopStatus = Constants.STOPSTATUS_DOUBLEUP;
					return;
				}
				else 
				{
					trade.POSITION_SIZE = trade.POSITION_SIZE * 2;
					// place order
					logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
					trade.price = data.close;
					trade.entryPrice = data.close;
					trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
					trade.remainingPositionSize=trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData)quotes[lastbar]).time;
					trade.entryPos = lastbar;
					trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
					if ( MODE == Constants.TEST_MODE )
						AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

					trade.stop = data.close + FIXED_STOP * 3 * PIP_SIZE;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

					// put follow up order
					/*
					trade.followUpPrice = data.close + TARGET_PROFIT_SIZE * PIP_SIZE/2;
					trade.followUpPrice = adjustPrice( trade.followUpPrice, Constants.ADJUST_TYPE_UP);
					trade.followUpPositionSize = trade.positionSize / 2;
					logger.warning(symbol + " " + CHART + " place follow up sell order of " + trade.followUpPositionSize + " @ " + trade.followUpPrice);
					trade.followUpId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					*/
					// calculate and place target order
					//trade.targetPrice = trade.price - TARGET_PROFIT_SIZE * PIP_SIZE;
					trade.targetPrice = trade.price - FIXED_STOP * PIP_SIZE;
					trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
					trade.targetPositionSize = trade.POSITION_SIZE/2;
					logger.warning(symbol + " " + CHART + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());

					trade.numOfEntry++;
					trade.status = Constants.STATUS_EXTENDED;
					trade.stopStatus = Constants.STOPSTATUS_DOUBLEUP;
					return;
				}
			} 
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData)quotes[lastbar]).high > ((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low))
			{
				if ( trade.numOfEntry == 0 ) // first time 
				{
					boolean reachedEntryQualifyPrice = false;
					int reachEntryQualifyPricePos = 0;
					for ( int i = trade.entryQualifyPricePos+1; i <= lastbar; i++ )
					{
						if (((QuoteData)quotes[i]).low <= trade.entryQualifyPrice )
						{
							reachedEntryQualifyPrice = true;
							reachEntryQualifyPricePos = i;
							break;
						}
					}
				
					if ( reachedEntryQualifyPrice == false )
						return;

					if ( lastbar - reachEntryQualifyPricePos >= 3 )
					{
						resetLowEntryQualifyPrice( trade, quotes, reachEntryQualifyPricePos, lastbar );
						trade.entryResets = 1;
						return;
					}

					if (((QuoteData)quotes[lastbar]).close - trade.detectPrice > 3 * Utility.getAverage(quotes, lastbar-20, lastbar))
					{
						logger.warning(symbol + " " + CHART + " missed the boat at " + ((QuoteData)quotes[lastbar]).time);
						trade = null;
						return;
					}
				}
				
				if ( trade.numOfEntry == 0 ) // first time 
				{
					logger.warning(symbol + " " + CHART + " place market buy order at " + data.time);
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
					trade.price = data.close;
					trade.entryPrice = data.close;
					trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
					trade.remainingPositionSize=trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData)quotes[lastbar]).time;
					trade.entryPos = lastbar;
					trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
					if ( MODE == Constants.TEST_MODE )
						AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);
		
					trade.stop = data.close - FIXED_STOP * PIP_SIZE;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());
					
					// calculate and place target order
					//trade.targetPrice = trade.price + TARGET_PROFIT_SIZE * PIP_SIZE;
					trade.targetPrice = trade.price + FIXED_STOP * PIP_SIZE;
					trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
					trade.targetPositionSize = trade.POSITION_SIZE/2;
					logger.warning(symbol + " " + CHART + " place limit target sell order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
					logger.warning(symbol + " " + CHART + " minte, trigger high/low is " + trade.triggerHighLow.time) ;
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());

					trade.numOfEntry++;
					trade.status = Constants.STATUS_PLACED;
					return;
				}
				else
				{	
					trade.POSITION_SIZE = trade.POSITION_SIZE * 2;
					// place order
					logger.warning(symbol + " " + CHART + " place market buy order at " + data.time);
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
					trade.price = data.close;
					trade.entryPrice = data.close;
					trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
					trade.remainingPositionSize=trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData)quotes[lastbar]).time;
					trade.entryPos = lastbar;
					trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
					if ( MODE == Constants.TEST_MODE )
						AddTradeOpenRecord( trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

					trade.stop = data.close - FIXED_STOP * 3 * PIP_SIZE;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);//new Long(oca).toString());

					// put follow up order
					/*
					trade.followUpPrice = data.close - TARGET_PROFIT_SIZE * PIP_SIZE/2;
					trade.followUpPrice = adjustPrice( trade.followUpPrice, Constants.ADJUST_TYPE_DOWN);
					trade.followUpPositionSize = trade.positionSize / 2;
					logger.warning(symbol + " " + CHART + " place follow up sell order of " + trade.followUpPositionSize + " @ " + trade.followUpPrice);
					trade.followUpId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					*/
					
					// calculate and place target order
					//trade.targetPrice = trade.price + TARGET_PROFIT_SIZE * PIP_SIZE;
					trade.targetPrice = trade.price + FIXED_STOP * PIP_SIZE;
					trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
					trade.targetPositionSize = trade.POSITION_SIZE/2;
					logger.warning(symbol + " " + CHART + " place limit target buy order of " + trade.targetPositionSize + " @ " + trade.targetPrice);
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());

					trade.numOfEntry++;
					trade.status = Constants.STATUS_EXTENDED;
					return;

				}
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

	

	public void trackReveralTradeEntry3(QuoteData data, Vector<QuoteData> qtsS, Vector<QuoteData> qtsL)
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
						PushHighLow first = findLastNLow( quotes, trade.pullBackPos, i--, 2);
						if ( first != null )
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

					
				//	if ( counting123( quotes,trade.pullBackPos, lastbar, Constants.DIRECTION_UP ) < 3 )
				//		return;
					
				}

			}
		}
	}


	
	
	
	
	
	

/*
	public void trackPullBackTradeEntry(QuoteData data, Vector<QuoteData> qtsS, Vector<QuoteData> qtsL)
	{
		logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);

		Object[] quotes = qtsS.toArray();
		int lastbar = quotes.length-1;

		int detectPos = Constants.NOT_FOUND;
		detectPos = Utility.getQuotePositionByMinute( quotes, trade.detectTime);
		
		if ( detectPos == Constants.NOT_FOUND )
		{
			logger.warning(symbol + " " + trade.type + " trade detect time " + trade.detectTime + " not found, remove trade");
			trade =  null;
			return;
		}

		// need at least two bars
		if ( lastbar == detectPos )	
		{
			logger.info(symbol + " no movement " );
			return; 	// no new bar yet
		}

		logger.info(symbol + " " + CHART + " minute " + trade.type + " trade detect time " + trade.detectTime + " position " + detectPos);

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			//int pos = detectPos;
			//while ( pos < lastbar )
			//{
				// looking for first pull back
				// when it was detect, it was already on a pull back
				//if ((((QuoteData)quotes[pos]).low > ((QuoteData)quotes[pos-1]).low) && (((QuoteData)quotes[pos]).high > ((QuoteData)quotes[pos-1]).high))
				{
					//int pullBackStart = detectPos;
					// when it was detect, it was already on a pull back
					int pos2 = detectPos+1;
					while ( pos2 <= lastbar )
					{
						if ((((QuoteData)quotes[pos2]).low < ((QuoteData)quotes[pos2-1]).low) && (((QuoteData)quotes[pos2]).high < ((QuoteData)quotes[pos2-1]).high))
						{
							// resume direction
							placeMarketSellOrder( data );
							trade.entryTime = ((QuoteData)quotes[lastbar]).time;
							logger.info(symbol + " place sell order @ " + data.close  );
							trade.entryTime = ((QuoteData)quotes[lastbar]).time;
							AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_SELL, trade.positionSize, data.close);

							// calculate and place stop order
							long oca = new Date().getTime();
							trade.oca = oca;
							//trade.stop = Utility.getHigh(quotes, lastbar-5, lastbar).high;// + 2*avgBarSize*pipSize;
							trade.stop = data.close + fixedStop * PIP_SIZE;
							trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
							logger.warning(symbol + " adjusted stop is " + trade.stop);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.positionSize, null);//new Long(oca).toString());
							trade.risk = trade.stop - data.close;
							trade.price = data.close;
							trade.entryPrice = data.close;
							trade.status = Constants.STATUS_PLACED;
							trade.position = Constants.POSITION_SHORT;
							trade.entryTime = ((QuoteData)quotes[lastbar]).time;
							trade.entryTimeL = ((QuoteData)qtsL.lastElement()).time;
							return;
						}
						
						pos2++;
					}
			//	}
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
//			int pos = detectPos;
	//		while ( pos < lastbar )
		//	{
				// looking for first pull back
		//		if ((((QuoteData)quotes[pos]).low < ((QuoteData)quotes[pos-1]).low) && (((QuoteData)quotes[pos]).high < ((QuoteData)quotes[pos-1]).high))
			//	{
				//	int pullBackStart = pos;
					int pos2 = detectPos + 1;
					while ( pos2 <= lastbar )
					{
						if ((((QuoteData)quotes[pos2]).low > ((QuoteData)quotes[pos2-1]).low) && (((QuoteData)quotes[pos2]).high > ((QuoteData)quotes[pos2-1]).high))
						{
							placeMarketBuyOrder( data );
							trade.entryTime = ((QuoteData)quotes[lastbar]).time;
							logger.info(symbol + " place buy order @ " + data.close  );
							trade.entryTime = ((QuoteData)quotes[lastbar]).time;
							AddTradeOpenRecord(  trade.type, data.time, Constants.ACTION_BUY, trade.positionSize, data.close);

							// calculate and place stop order
							long oca = new Date().getTime();
							trade.oca = oca;
							trade.stop = data.close - fixedStop * PIP_SIZE;
							trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
							logger.warning(symbol + " adjusted stop is " + trade.stop);
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.positionSize, null);//new Long(oca).toString());
							trade.risk = trade.stop - data.close;
							trade.price = data.close;
							trade.entryPrice = data.close;
							trade.status = Constants.STATUS_PLACED;
							trade.position = Constants.POSITION_LONG;
							trade.entryTime = ((QuoteData)quotes[lastbar]).time;
							trade.entryTimeL = ((QuoteData)qtsL.lastElement()).time;
							return;
						}
						pos2++;
					//}
				//}
				
				//pos++;
			}
		}
	}	

	*/

	
	

	/*
	private void takeProfit(Vector<Integer> trendStrength )
	{
		int pullbacks = trendStrength.size();
		int pullback40s = 0;
		
		Iterator it = trendStrength.iterator();
		while ( it.hasNext())
		{
			Integer i = (Integer)it.next();
			if (i.compareTo(Constants.touch40)== 0)
				pullback40s++;
		}
		
		boolean weakTrend = false;
		if (( pullbacks > 3 ) && ( pullback40s > 1))
			weakTrend = true;
		
		if ( weakTrend == false )
		{
			if ( trade.initProfitTaken == false )
			{
				int profitSize = (int) (0.5 * POSITION_SIZE);
				closePosition(profitSize);
				trade.initProfitTaken = true;
			}
		}
		else
		{
			closePosition(trade.remainingPositionSize);
		}
	}*/


	
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
	

	
	
	
	
	
	
	private Vector<Peak> findLowerLow2( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		peak.startpos = pos;

		while ( pos < end )
		{	
			// find the high
			int lowStart = pos;
			peak = new Peak();
			while (( pos < end-2 ) && !((((QuoteData) quotes[pos+1]).high > ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low)
					&& (((QuoteData) quotes[pos+2]).low > ((QuoteData) quotes[pos]).low) && (((QuoteData) quotes[pos+3]).low > ((QuoteData) quotes[pos]).low)))
			{
				peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}
			while (( pos >= end -2 ) && (pos < end) && !((((QuoteData) quotes[pos+1]).high > ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low)))
			{
				peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}
			
			QuoteData lowPoint = Utility.getLow(quotes, lowStart, pos);

			if ( pos >= end )
			{
				peak.highlow = lowPoint.high;
				peak.highlowpos = lowPoint.pos;
				peaks.add(peak);
				return peaks;
			}
			
			int highStart = pos+1;
			while (( pos <= end ) && ((QuoteData) quotes[pos]).low >= lowPoint.low) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}
			
			if ( pos > end )
				pos = end;
			
			QuoteData highPoint = Utility.getHigh(quotes, highStart, pos);
			
			peak.highlow = lowPoint.low;
			peak.highlowpos = lowPoint.pos;
			peak.pullback = highPoint.high;
			peak.pullbackpos = highPoint.pos;
			peaks.add( peak ); 

			if ( pos >= end )
				return peaks;
		}
		
		return peaks;
		
	}

	
	private Vector<Peak> findHigherHigh3( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos < end )
		{	
			// find the high
			peak = new Peak();

			peak.startpos = pos;
			boolean peakfound = false;
			
			// a high is the next two bar are lower and one makes "lower low"
			while ( pos <= end-2 ) 
			{	
				if (!(((((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+2]).high < ((QuoteData) quotes[pos]).high))
			       &&	
				   (((((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low < ((QuoteData) quotes[pos]).low))
					|| 	((((QuoteData) quotes[pos+2]).high < ((QuoteData) quotes[pos+1]).high) && (((QuoteData) quotes[pos+2]).low < ((QuoteData) quotes[pos+1]).low)))))
				{	
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					pos++;
				}
				else
				{
					// found a peak
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					peakfound = true;
					break;
				}

			}
			
			if ( pos > end -2 )
			{	
				QuoteData highPoint = Utility.getHigh(quotes, peak.startpos, end);
				peak.highlow = highPoint.high;
				peak.highlowpos = highPoint.pos;
				peaks.add(peak);
				return peaks;
			}
			else
			{	
				QuoteData highPoint = Utility.getHigh(quotes, peak.startpos, pos);
				peak.highlow = highPoint.high;
				peak.highlowpos = highPoint.pos;
			}	
			
			// now counting the pull backs
			
			pos++;
			int lowStart = pos;
			while (( pos < end ) && ((QuoteData) quotes[pos+1]).high <= peak.highlow) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos+1]);
				pos++;
			}
			
			QuoteData lowPoint = Utility.getLow(quotes, lowStart, pos);
			
			peak.pullback = lowPoint.low;
			peak.pullbackpos = lowPoint.pos;
			peaks.add( peak ); 

			if ( pos >= end )
				return peaks;
			
			pos++;
		}
		
		return peaks;
		
	}

	private Vector<Peak> findLowerLow3( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos <= end )
		{	
			// find the high
			peak = new Peak();

			peak.startpos = pos;

			// a high is the next two bar are lower and one makes "lower low"
			while ( pos < end-2 )
			{
				if (!(((((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low) && (((QuoteData) quotes[pos+2]).low > ((QuoteData) quotes[pos]).low))
					   &&
				    (((((QuoteData) quotes[pos+1]).high > ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low))
					|| 	((((QuoteData) quotes[pos+2]).high > ((QuoteData) quotes[pos+1]).high) && (((QuoteData) quotes[pos+2]).low > ((QuoteData) quotes[pos+1]).low)))))
  			  	{	
					// found a peak
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					pos++;
				}
				else
				{
				   peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				   break;
				}
			}
			
			if ( pos >end -2 )
			{	
				QuoteData lowPoint = Utility.getLow(quotes, peak.startpos, end);
				peak.highlow = lowPoint.low;
				peak.highlowpos = lowPoint.pos;
				peaks.add(peak);
				return peaks;
			}
			else
			{	
				QuoteData lowPoint = Utility.getLow(quotes, peak.startpos, pos);
				peak.highlow = lowPoint.low;
				peak.highlowpos = lowPoint.pos;
			}	
			
			// now counting the pull backs
			int highStart = pos;
			while (( pos < end ) && ((QuoteData) quotes[pos+1]).low >= peak.highlow) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos+1]);
				pos++;
			}
			
			QuoteData highPoint = Utility.getHigh(quotes, highStart, pos);
			
			peak.pullback = highPoint.high;
			peak.pullbackpos = highPoint.pos;
			peaks.add( peak ); 

			if ( pos >= end )
				return peaks;
			
			pos++;
		}
		
		return peaks;
		
	}
	

	
	private Vector<Peak> findHigherHigh4( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos <= end )
		{	
			// find the high
			peak = new Peak();

			peak.startpos = pos;
			boolean peakfound = false;
			
			// a high is the next two bar are lower and one makes "lower low"
			while ( pos <= end-2 ) 
			{	
				if (!(((((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+2]).high < ((QuoteData) quotes[pos]).high))
			       &&	
				   (((((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low < ((QuoteData) quotes[pos]).low))
					|| 	((((QuoteData) quotes[pos+2]).high < ((QuoteData) quotes[pos+1]).high) && (((QuoteData) quotes[pos+2]).low < ((QuoteData) quotes[pos+1]).low)))))
				{	
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					pos++;
				}
				else
				{
					// found a peak
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					peakfound = true;
					break;
				}

			}
	
			if ( peakfound == true )
			{
				QuoteData highPoint = Utility.getHigh(quotes, peak.startpos, pos);
				peak.highlow = highPoint.high;
				peak.highlowpos = highPoint.pos;
			}
			else
			{
				// it is running to the end
				for ( int i = pos; i<= end; i++)
					peak.highlowQuoteData.add((QuoteData)quotes[i]);
				QuoteData highPoint = Utility.getHigh(quotes, peak.startpos, end);
				peak.highlow = highPoint.high;
				peak.highlowpos = highPoint.pos;
				peaks.add(peak);
				return peaks;
			}

			// now counting the pull backs, pos holds the peak
			pos++;
			int lowStart = pos;
			while (( pos <= end ) && ((QuoteData) quotes[pos]).high <= peak.highlow) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}
			
			QuoteData lowPoint = Utility.getLow(quotes, lowStart, pos-1);
			peak.pullback = lowPoint.low;
			peak.pullbackpos = lowPoint.pos;
			peaks.add(peak);
			if ( pos > end )
				return peaks;
		}
		
		return peaks;
		
	}

	private Vector<Peak> findLowerLow4( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos <= end )
		{	
			// find the high
			peak = new Peak();

			peak.startpos = pos;
			boolean peakfound = false;

			// a high is the next two bar are lower and one makes "lower low"
			while ( pos < end-2 )
			{
				if (!(((((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low) && (((QuoteData) quotes[pos+2]).low > ((QuoteData) quotes[pos]).low))
					   &&
				    (((((QuoteData) quotes[pos+1]).high > ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low))
					|| 	((((QuoteData) quotes[pos+2]).high > ((QuoteData) quotes[pos+1]).high) && (((QuoteData) quotes[pos+2]).low > ((QuoteData) quotes[pos+1]).low)))))
  			  	{	
					// found a peak
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					pos++;
				}
				else
				{
				   peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				   peakfound = true;
				   break;
				}
			}
			
			if ( peakfound == true )
			{
				QuoteData lowPoint = Utility.getLow(quotes, peak.startpos, pos);
				peak.highlow = lowPoint.low;
				peak.highlowpos = lowPoint.pos;
			}
			else
			{
				// it is running to the end
				for ( int i = pos; i<= end; i++)
					peak.highlowQuoteData.add((QuoteData)quotes[i]);
				QuoteData lowPoint = Utility.getLow(quotes, peak.startpos, end);
				peak.highlow = lowPoint.low;
				peak.highlowpos = lowPoint.pos;
				peaks.add(peak);
				return peaks;
			}

			// now counting the pull backs
			pos++;
			int highStart = pos;
			while (( pos <= end ) && ((QuoteData) quotes[pos]).low >= peak.highlow) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}

			QuoteData highPoint = Utility.getHigh(quotes, highStart, pos-1);
			peak.pullback = highPoint.high;
			peak.pullbackpos = highPoint.pos;
			peaks.add( peak ); 
			if ( pos > end )
				return peaks;

		}
		
		return peaks;
		
	}

	

	
	private Vector<Peak> findHigherHigh5( Object[] quotes, int start, int end, int minNumBars )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos <= end )
		{	
			peak = new Peak();

			// now counting the pull backs, pos holds the peak
			while ( pos <= end )
			{
				if ( ((QuoteData) quotes[pos]).high <= Utility.getHigh(peak.pullbackQuoteData ))
				{	
					peak.pullbackQuoteData.add( (QuoteData) quotes[pos]);
					pos++;
				}
				break;
			}
			
			if ( pos > end )
			{
				peaks.add(peak);
				return peaks;
			}

			// pos is at high point
			while ( true ) 
			{	
				peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				while ( ( pos+1 <= end ) && ((QuoteData)quotes[pos+1]).high > ((QuoteData)quotes[pos]).high)
				{
					peak.highlowQuoteData.add((QuoteData)quotes[pos+1]);
					pos++;
					
				}

				if ( pos+1 > end )  
				{
					peaks.add(peak);
					return peaks;
				}

				// pos+1 < pos
				int nextHigh = -1;
				for ( int i = pos+ 1; i <= end; i++ )
				{
					if (((QuoteData) quotes[i]).high > ((QuoteData) quotes[pos]).high)
					{
						nextHigh = i;
						break;
					}
				}
				
				if ( nextHigh == -1 )
				{
					// not found, add everything as high and return 
					for ( int i = pos+ 1; i <= end; i++ )
						peak.highlowQuoteData.add((QuoteData) quotes[i]);
					
					peaks.add(peak);
					return peaks;
				}
				else
				{	
					if ( nextHigh - pos < 3 )  // this is the criteria
					{
						for ( int i = pos+1; i < nextHigh; i++ )
							peak.highlowQuoteData.add((QuoteData) quotes[i]);
						pos = nextHigh;
						continue;
					}
					else
					{
						peaks.add(peak);
						pos++;
						break; // next high found, break the loop, so the lows can be added
					}
				}
			}
		}
		
		return peaks;
		
	}

	
	
	private Vector<Peak> findHigherHigh6( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos <= end )
		{	
			// find the high
			peak = new Peak();

			peak.startpos = pos;
			boolean peakfound = false;
			
			// a high is the next two bar are lower and one makes "lower low"
			while ( pos < end ) 
			{	
				if (!(((((QuoteData) quotes[pos+1]).high < ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low < ((QuoteData) quotes[pos]).low))))
				{	
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					pos++;
				}
				else
				{
					// found a peak
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					peakfound = true;
					break;
				}

			}
	
			if ( peakfound == true )
			{
				QuoteData highPoint = Utility.getHigh(quotes, peak.startpos, pos);
				peak.highlow = highPoint.high;
				peak.highlowpos = highPoint.pos;
			}
			else
			{
				// it is running to the end
				peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				QuoteData highPoint = Utility.getHigh(quotes, peak.startpos, end);
				peak.highlow = highPoint.high;
				peak.highlowpos = highPoint.pos;
				peaks.add(peak);
				return peaks;
			}

			// now counting the pull backs, pos holds the peak
			pos++;
			int lowStart = pos;
			while (( pos <= end ) && ((QuoteData) quotes[pos]).high <= peak.highlow) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}
			
			QuoteData lowPoint = Utility.getLow(quotes, lowStart, pos-1);
			peak.pullback = lowPoint.low;
			peak.pullbackpos = lowPoint.pos;
			peaks.add(peak);
			if ( pos > end )
				return peaks;
		}
		
		return peaks;
		
	}
	
	
	private Vector<Peak> findLowerLow6( Object[] quotes, int start, int end )
	{
		Vector<Peak> peaks = new Vector<Peak>();
		int pos = start;
		Peak peak = null;

		while ( pos < end )
		{	
			// find the high
			peak = new Peak();

			peak.startpos = pos;
			boolean peakfound = false;

			// a high is the next two bar are lower and one makes "lower low"
			while ( pos < end-2 )
			{
				if (!((((QuoteData) quotes[pos+1]).high > ((QuoteData) quotes[pos]).high) && (((QuoteData) quotes[pos+1]).low > ((QuoteData) quotes[pos]).low)))
  			  	{	
					// found a peak
					peak.highlowQuoteData.add((QuoteData)quotes[pos]);
					pos++;
				}
				else
				{
				   peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				   peakfound = true;
				   break;
				}
			}
			
			if ( peakfound == true )
			{
				QuoteData lowPoint = Utility.getLow(quotes, peak.startpos, pos);
				peak.highlow = lowPoint.low;
				peak.highlowpos = lowPoint.pos;
			}
			else
			{
				// it is running to the end
				peak.highlowQuoteData.add((QuoteData)quotes[pos]);
				QuoteData lowPoint = Utility.getLow(quotes, peak.startpos, end);
				peak.highlow = lowPoint.low;
				peak.highlowpos = lowPoint.pos;
				peaks.add(peak);
				return peaks;
			}

			// now counting the pull backs
			pos++;
			int highStart = pos;
			while (( pos <= end ) && ((QuoteData) quotes[pos]).low >= peak.highlow) 
			{
				peak.pullbackQuoteData.add((QuoteData)quotes[pos]);
				pos++;
			}

			QuoteData highPoint = Utility.getHigh(quotes, highStart, pos-1);
			peak.pullback = highPoint.high;
			peak.pullbackpos = highPoint.pos;
			peaks.add( peak ); 
			if ( pos > end )
				return peaks;

		}
		
		return peaks;
		
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
	

	
	
	
	
	
	
	
	

	public void trackReversalTarget(QuoteData data, Vector<QuoteData> qtsM)
	{
		double[] ema20 = Indicator.calculateEMA(qtsM, 20);
		Object[] quotes = qtsM.toArray();
		int lastbar = quotes.length-1;

		int entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTime);

		//double avgSizeM = Utility.getAverage(quotes);

		// exit at extrem, reversal signal
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// adjust inital stop and perhaps take initial profit
		    //Pattern.findMABreakoutUps(quotes, ema20, entryPos);

			// take profit if there is a diverage
			Cup cup = Pattern.downCup(symbol, quotes, ema20, entryPos, lastbar, 0, 0, TARGET_PROFIT_SIZE*PIP_SIZE*0.25, false);  
			
			if (( cup != null ) && ( cup.pullBackWidth <=20 ) && (checkProfitTake(lastbar) == false))
			{	
				logger.info(symbol + " down cup detected at " + data.time + "pull back:" + ((QuoteData)quotes[cup.pullBackPos]).time + " pullback size:" + cup.pullBackSize/PIP_SIZE + " previous High:" + cup.lastHighLow);
				double[] so = Indicator.calculateStochastics( quotes, 14 );
				if (so[lastbar] > so[cup.lastHighLowPos])
				{
					logger.info(symbol + " socashtic diverage detected, exit trade");
					System.out.println(symbol + " socashtic diverage detected, exit trade");

					trade.takeProfit.put(lastbar, POSITION_SIZE/2);
					double closePrice = adjustPrice( data.close, Constants.ADJUST_TYPE_DOWN);
					closePositionByLimit(closePrice, trade.POSITION_SIZE/2);
					return;
				}
			}

			if ((((QuoteData)quotes[lastbar]).high < ema20[lastbar]) && ( trade.reach2FixedStop == false ))
			{
				logger.info(symbol + " to move stop to break even ");
				// find the last close above 20MA
				if ( trade.stopId != 0 )
					cancelOrder(trade.stopId);
				if ( trade.targetId != 0 )
					cancelOrder(trade.targetId);

				String oca = new Long(new Date().getTime()).toString();
				trade.stop = adjustPrice( trade.entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);
				trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize,oca);
				
				trade.reach2FixedStop = true;
			}
			
			
			// take some profit if there is a windfall
			/*
			if (((((QuoteData)quotesM[lastbar-1]).open - (((QuoteData)quotesM[lastbarM-1]).close)) >  3 * avgSizeM )
			&& ( inProfit(data) > 50 * pipSize ) && (trade.profitTake.containsValue(lastbarM-1) == false))
			{
				logger.info(symbol + " windfall move, take some profit");
				System.out.println(symbol + " windfall move, take some profit");
				closePositionByLimit(data.close, trade.positionSize / 2);
				trade.takeProfit.put(lastbar-1, positionSize/2);
				return;
			}*/
			
			
			// adjust inital stop and perhaps take initial profit
			// see if we can move the stop
			/*
			if ((((QuoteData)quotes[lastbar]).high < ema20[lastbar]) && (((QuoteData)quotesM[lastbarM-1]).high < ema20M[lastbarM-1])
			{
				logger.info(symbol + " to move stop ");
				// find the last close above 20MA
				for ( int i = lastbarM-2; i >= detectPos ; i-- )
				{
					if (((QuoteData)quotesM[i]).high > ema20M[i])
					{
						int j = i-1;
						while (((QuoteData)quotesM[j]).high > ema20M[j])
							j--;
						
						double lastHigh = Utility.getHigh(quotesM, j, i );
						if ( trade.stop != lastHigh)
						{
							trade.stop = lastHigh;
							logger.info(symbol + " move stop to " + trade.stop);
							if ( trade.stopId != 0 )
								cancelOrder(trade.stopId);

							double adjp = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
							logger.info(symbol + "stop moves to " + adjp);
							System.out.println(symbol + "stop moves to " + adjp);

							trade.stopId = placeStopMarketOrder( Constants.ACTION_BUY, adjp);
						}
						return;
					}
				}
			}*/
			
			
			// only exit or stop out when the MA is broken
			//if (((QuoteData)quotesM[lastbarM-1]).low > ema20M[lastbarM-1])
			//{
				/*
				int count = 0;
				for ( int i = lastbarM-2; i > detectPos; i--)
				{
					if (((QuoteData)quotesM[i]).high < ema20M[i] )
						count++;
				}
				
				if ( count > 2 )*//*
				{
					logger.info(symbol + " MA ended, exit trade");
					closeTrade();
					trade = null;
					return;
				}

			}*/
			
			
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//logger.info(symbol + " to move stop ");
			// adjust inital stop and perhaps take initial profit

			// check if no at a potential reversal point
			Cup cup = Pattern.upCup(symbol, quotes, ema20, entryPos, lastbar, 0, 0, TARGET_PROFIT_SIZE*PIP_SIZE*0.25, false);  
			
			if (( cup != null ) && ( cup.pullBackWidth <=20 ) && (checkProfitTake(lastbar) == false))
			{	
				logger.info(symbol + " up cup detected at " + data.time + "pull back:" + ((QuoteData)quotes[cup.pullBackPos]).time + " pullback size:" + cup.pullBackSize/PIP_SIZE + " previous High:" + cup.lastHighLow);
				double[] so = Indicator.calculateStochastics( quotes, 14 );
				if (so[lastbar] < so[cup.lastHighLowPos])
				{
					logger.info(symbol + " socashtic diverage detected, exit trade");
					System.out.println(symbol + " socashtic diverage detected, exit trade");

					trade.takeProfit.put(lastbar, POSITION_SIZE/2);
					double closePrice = adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
					closePositionByLimit(closePrice, trade.POSITION_SIZE/2);
					return;
				}
			}

			
			if ((((QuoteData)quotes[lastbar]).low > ema20[lastbar]) && ( trade.reach2FixedStop == false ))
			{
				logger.info(symbol + " to move stop to break even ");
				// find the last close above 20MA
				if ( trade.stopId != 0 )
					cancelOrder(trade.stopId);
				if ( trade.targetId != 0 )
					cancelOrder(trade.targetId);

				String oca = new Long(new Date().getTime()).toString();
				trade.stop = adjustPrice( trade.entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);
				trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.remainingPositionSize,oca);
				
				trade.reach2FixedStop = true;
			}
		}
	}


			/*
			if (((((QuoteData)quotesM[lastbarM-1]).open - (((QuoteData)quotesM[lastbarM-1]).close)) >  3 * avgSizeM )
					&& ( inProfit(data) > 50 * pipSize )&& (trade.profitTake.containsValue(lastbarM-1) == false))
			{
				logger.info(symbol + " windfall move, take some profit");
				System.out.println(symbol + " windfall move, take some profit");
				closeTrade(trade.positionSize/2);
				trade.profitTake.put(lastbarM-1, positionSize/2);
				return;
			}
			
			// did not exit
			// now to see if we can move the stop
			/*
			if ((((QuoteData)quotesM[lastbarM]).low > ema20M[lastbarM]) && (((QuoteData)quotesM[lastbarM-1]).low > ema20M[lastbarM-1])
					&& (((QuoteData)quotesM[lastbarM-2]).low <= ema20M[lastbarM-2]) )
			{
				// find the last close above 20MA
				for ( int i = lastbarM-2; i >= detectPos; i-- )
				{
					if (((QuoteData)quotesM[i]).low < ema20M[i])
					{
						int j = i-1;
						while (((QuoteData)quotesM[j]).low < ema20M[j])
							j--;
						
						double lastLow = Utility.getLow(quotesM, j, i );
						if ( trade.stop != lastLow)
						{
							trade.stop = lastLow;
							logger.info(symbol + " move stop to " + trade.stop);
							if ( trade.stopId != 0 )
								cancelOrder(trade.stopId);
							
							logger.info(symbol + "stop moves to " + adjp);
							System.out.println(symbol + "stop moves to " + adjp);

							trade.stopId = placeStopMarketOrder( Constants.ACTION_SELL, adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN));
						}
						return;
					}
				}
			}
			
			
			// only exit or stop out when the MA is broken
			if (((QuoteData)quotesM[lastbarM-1]).high < ema20M[lastbarM-1])
			{
				/*
				int count = 0;
				for ( int i = lastbarM-2; i > detectPos; i--)
				{
					if (((QuoteData)quotesM[i]).low > ema20M[i] )
						count++;
				}
				
				if ( count > 2 )
				{
					logger.info(symbol + " MA ended, exit trade");
					System.out.println(symbol + " MA ended, exit trade");
					closeTrade();
					trade = null;
					return;
				}
			}
		}
	}*/

	
	public void trackTradeTarget(QuoteData data, Object[] quotesL)
	{
		if ( MODE == Constants.TEST_MODE )
			checkStopTarget(data);
		
		if ( trade != null )
		{
			if (Constants.TRADE_RST.equals(trade.type))
				exit123_3( data, quotesL);
			else if (Constants.TRADE_CNT.equals(trade.type))
				exit123_cnt( data, quotesL);
		}

		//takeInitialProfit( data, ti, qts );
		
		//if ( trade.initProfitTaken == false ) 
	
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

	
	
	
	
	
	public void moveStop(QuoteData data, int ti, Vector<QuoteData> qts)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		// close one bar below 20MA, move stop to break even
		/*
		if( trade.moveStop1 == false )
		{		
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar-1]).high < ema20[lastbar-1]))
				{
					logger.warning(symbol + " move stop point 1 point hit - move stop");
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop1 = true;
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar-1]).low > ema20[lastbar-1]))
				{
					logger.warning(symbol + " move stop point 1 point hit - move stop");
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop1 = true;
				}
			}
		}*/
		
		// close two bar below 20MA after first pull back, move stop to first pull back
		if( trade.moveStop2 == false )
		{		
			int entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTime);
			if ( entryPos == Constants.NOT_FOUND )
			{
				logger.warning(symbol + " move stop 2, can not find entry position");
				return;
			}
			
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar-1]).high < ema20[lastbar-1]) && (((QuoteData)quotes[lastbar-2]).high < ema20[lastbar-2]))
				{
					int pos = Pattern.findPriceCross20MADown(quotes, ema20, 1);  // 1 bar consider other side is enough
					if ( pos < entryPos )
						return;   // did not cross after entry
					logger.info(symbol + " 20MA cross up at " + ((QuoteData)quotes[pos]).time);
					
					Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns(quotes, ema20, pos+1);

					if ( breakouts.size() >= 2 )
					{
						logger.warning(symbol + " move stop point 2 point hit - move stop");
						BreakOut b2 = breakouts.elementAt(1);
						trade.stop = b2.below;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " move stop to " + trade.stop);
						cancelOrder( trade.stopId);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						trade.moveStop2 = true;
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar-1]).low > ema20[lastbar-1]) && (((QuoteData)quotes[lastbar-2]).low > ema20[lastbar-2]))
				{
					int pos = Pattern.findPriceCross20MAUp(quotes, ema20, 1);  // 1 bar consider other side is enough
					if ( pos < entryPos )
						return;   // did not cross after entry
					logger.info(symbol + " 20MA cross up at " + ((QuoteData)quotes[pos]).time);
					
					Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns(quotes, ema20, pos+1);

					if ( breakouts.size() >= 2 )
					{
						logger.warning(symbol + " move stop point 2 point hit - move stop");
						BreakOut b2 = breakouts.elementAt(1);
						trade.stop = b2.below;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						cancelOrder( trade.stopId);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						trade.moveStop2 = true;
					}
				}
			}
		}
	}

	
	
	public void takeProfit123(QuoteData data, int ti, Vector<QuoteData> qts)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		
		int entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTime);

		if (entryPos == Constants.NOT_FOUND)
		{
			logger.info(symbol + "entrypos not found" );
			System.out.println(symbol + "entrypos not found" );
		}
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<Peak> peaks = findLowerLow4( quotes, entryPos, lastbar);

			int size = peaks.size();
			if ( size < 2 )
				return;

			Peak peakFirst = peaks.firstElement();
			Peak peakLast = peaks.lastElement();
			double dist = peakFirst.pullback - peakLast.highlow;

			if  ( ( size == 2 ) && ( dist > minTarget1 * 1.5 ))
			{
				logger.warning(symbol + " " + ti + " take initial profit at seocnd wave " + data.time);
				trade.targetId = placeLmtOrder( Constants.ACTION_BUY, data.close, trade.POSITION_SIZE/2, null);
				trade.initProfitTaken = true;
			}

			if  (( size >= 3 ) &&  ( dist > minTarget1 ))
			{	
				//Peak lastPeak = peaks.elementAt(size-2);
				//double breakoutlength = Math.abs(((QuoteData)quotes[lastPeak.highlowpos]).low - ((QuoteData)quotes[lastPeak.startpos]).high);
				//double pullback = Math.abs(((QuoteData)quotes[lastPeak.highlowpos]).low - ((QuoteData)quotes[lastPeak.pullbackpos]).high);
				logger.warning(symbol + " " + ti + " take initial profit at " + data.time);
				trade.targetId = placeLmtOrder( Constants.ACTION_BUY, data.close, trade.POSITION_SIZE/2, null);
				trade.initProfitTaken = true;
			}
			
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<Peak> peaks = findHigherHigh4( quotes, entryPos, lastbar);

			int size = peaks.size();
			if ( size < 2 )
				return;
			
			Peak peakFirst = peaks.firstElement();
			Peak peakLast = peaks.lastElement();
			double dist = peakLast.highlow - peakFirst.pullback;

			if  ( ( size == 2 ) && ( dist > minTarget1 * 1.5 ))
			{
				logger.warning(symbol + " " + ti + " take initial profit at seocnd wave " + data.time);
				trade.targetId = placeLmtOrder( Constants.ACTION_SELL, data.close, trade.POSITION_SIZE/2, null);
				trade.initProfitTaken = true;
			}

			if  (( size >= 3 ) &&  ( dist > minTarget1 ))
			{	
				//Peak lastPeak = peaks.elementAt(size-2);
				//double breakoutlength = Math.abs(((QuoteData)quotes[lastPeak.highlowpos]).low - ((QuoteData)quotes[lastPeak.startpos]).high);
				//double pullback = Math.abs(((QuoteData)quotes[lastPeak.highlowpos]).low - ((QuoteData)quotes[lastPeak.pullbackpos]).high);
				logger.warning(symbol + " " + ti + " take initial profit at " + data.time);
				trade.targetId = placeLmtOrder( Constants.ACTION_SELL, data.close, trade.POSITION_SIZE/2, null);
				trade.initProfitTaken = true;
			}
		}
	}

	
	public void exit123_bak(QuoteData data, int ti, Vector<QuoteData> qts)
	{
		if ( trade.moveStop2 == false )
			return;

		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		int entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTime);
		if  ( entryPos == Constants.NOT_FOUND )
		{	
			logger.info(symbol + " exit - can not found enty position");
			return;
		}
		
		if ( profitTake.get(lastbar) == null )
		{
			if (Constants.ACTION_SELL.equals(trade.action))
			{	
				if ((((QuoteData)quotes[lastbar]).high > (((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low > ((QuoteData)quotes[lastbar-1]).low)))
				{
					Vector<Peak> peaks = findLowerLow4( quotes, entryPos, lastbar);  
				
					if (( peaks.lastElement().pullback <= 1 ) )
					{	
						closePositionByMarket(trade.POSITION_SIZE/2, data.close);
						if (( trade != null ) && (trade.remainingPositionSize != 0 ))
						{	
							cancelOrder( trade.stopId);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}
					}
				}
				
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if ((((QuoteData)quotes[lastbar]).high < (((QuoteData)quotes[lastbar-1]).high) && (((QuoteData)quotes[lastbar]).low < ((QuoteData)quotes[lastbar-1]).low)))
				{
					Vector<Peak> peaks = findHigherHigh4( quotes, entryPos, lastbar);  
				
					if (( peaks.lastElement().pullback <= 1 ) )
					{	
						closePositionByMarket(trade.POSITION_SIZE/2, data.close);
						if (( trade != null ) && (trade.remainingPositionSize != 0 ))
						{	
							cancelOrder( trade.stopId);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
	}
	

	public void exit123(QuoteData data, int ti, Vector<QuoteData> qts, Vector<QuoteData> qtsL)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		int entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTime);
		if  ( entryPos == Constants.NOT_FOUND )
		{	
			logger.info(symbol + " exit - can not found enty position");
			return;
		}

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 2 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			// move stop to the first pull back to 20MA after two break outs
			if ( trade.moveStop2 == false )
			{
				/// move stop after it entered 20MA 
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
				}
				
				// move stop once it reached minStopTarget + 2;
				/*
				if ( data.close - trade.entryPrice > ( this.stopTarget + 2) * PIP_SIZE )
				{
					trade.stop = trade.entryPrice;
					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
					trade.moveStop2 = true;
				}*/
			}
			else
			{
				/*
				if ( breakOutSize - firstTwoBars > 2 )  // two times take off
				{
					BreakOut lastBreakout = breakouts.elementAt(breakOutSize-2);
					double breakOutLow = Utility.getLow(lastBreakout.highlowQuotes);
					double breakOutPullBackHigh = Utility.getHigh( lastBreakout.belowQuotes);
					trade.targetPrice = adjustPrice(breakOutLow + ( breakOutPullBackHigh - breakOutLow )/2, Constants.ADJUST_TYPE_DOWN);
					trade.targetPositionSize = trade.remainingPositionSize;
					logger.info(symbol + " breakout target price:" + trade.targetPrice );
					//trade.reEnter = 0;
				}*/
			
				if ( trade.profitTake1 == false )
				{	
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{
						logger.info( symbol + " take profit");
						for ( int i = 0; i < peaks.size(); i++)
						{
							Peak p = peaks.elementAt(i);
							logger.info(symbol + " i = " + i );
							logger.info(symbol + " p.lowpos = " + p.highlowpos );
							logger.info(symbol + " p.lowpullbackpos = " + p.pullbackpos );
							logger.info(symbol + " Lower Low at " + ((QuoteData) quotes[p.highlowpos]).time +  " @ " + p.highlow);
							logger.info(symbol + " Lower Low pull back at " + ((QuoteData) quotes[p.pullbackpos]).time + " @" + p.pullback);
							Iterator it = p.highlowQuoteData.iterator();
							while ( it.hasNext())
							{
								QuoteData q = (QuoteData)it.next();
								logger.info(symbol + " lows at " + q.time + " " + q.pos );
							}
							it = p.pullbackQuoteData.iterator();
							while ( it.hasNext())
							{
								QuoteData q = (QuoteData)it.next();
								logger.info(symbol + " low pullbacks at " + q.time + " " + q.pos + " @ " + q.high);
							}
						}

						AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.POSITION_SIZE/2, data.close);
						closePositionByMarket( trade.POSITION_SIZE/2, data.close);
						if ( trade != null )
							trade.profitTake1 = true;
					}
				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);

					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
					}
				}
						

				
				
				// looking to take profit if it does not look good
				// 1.  Quickly return to 20MA
				// 2.  successfully return to 20MA
				// 3.  High fly
				/*
				if ( breakouts.size() == 3 )  // two times take off
				{
					logger.warning(symbol + " 3 moves, take at least half profit");
					trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
				}*/
				
				
				// start to take profit after 2 returns
				/*
				if ( breakouts.size() == 3 )
				{
					logger.warning(symbol + " target hit, close position");
					closePosition();
				}*/
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 2 )
				{
					firstTwoBars = i;
					break;
				}
			}

			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
				}
				/*
				if ( trade.entryPrice - data.close > ( this.stopTarget + 2) * PIP_SIZE )
				{
					trade.stop = trade.entryPrice;
					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					trade.moveStop2 = true;
				}*/

			}
			else
			{
				// looking to take profit if it does not look good
				// 1.  Quickly return to 20MA
				// 2.  successfully return to 20MA
				// 3.  High fly
				/*
				if ( breakOutSize - firstTwoBars > 2 )  // two times take off
				{
					double breakOutHigh = Utility.getHigh(lastBreakout.highlowQuotes);
					double breakOutPullBackLow = Utility.getLow( lastBreakout.belowQuotes);
					trade.targetPrice = adjustPrice(breakOutPullBackLow + ( breakOutHigh - breakOutPullBackLow )/2, Constants.ADJUST_TYPE_UP);
					trade.targetPositionSize = trade.remainingPositionSize;
					logger.info(symbol + " breakout target price:" + trade.targetPrice );
					//trade.reEnter = 0;
				}*/
				
				
				if ( trade.profitTake1 == false )
				{	
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.POSITION_SIZE/2, data.close);
						closePositionByMarket( trade.POSITION_SIZE/2, data.close);
						if ( trade != null )
							trade.profitTake1 = true;
					}
				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);
					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
					}
				}
			}
		}
	}

	

	public void exit123_2(QuoteData data, int ti, Vector<QuoteData> qts, Vector<QuoteData> qtsL)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		int entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTime);
		if  ( entryPos == Constants.NOT_FOUND )
		{	
			logger.info(symbol + " exit - can not found enty position");
			return;
		}

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
			
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			
			// move stop to the first pull back to 20MA after two break outs
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					Iterator it = breakouts.elementAt(1).belowQuotes.iterator();
					while ( it.hasNext() )
					{
						QuoteData d = (QuoteData)it.next();
						logger.warning(symbol + " high:" + d.high + " low:" + d.low + " time:" + d.time);
					}
					
					trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
				}
			}
			else
			{
				/*
				if ( breakOutSize - firstTwoBars > 2 )  // two times take off
				{
					BreakOut lastBreakout = breakouts.elementAt(breakOutSize-2);
					double breakOutLow = Utility.getLow(lastBreakout.highlowQuotes);
					double breakOutPullBackHigh = Utility.getHigh( lastBreakout.belowQuotes);
					trade.targetPrice = adjustPrice(breakOutLow + ( breakOutPullBackHigh - breakOutLow )/2, Constants.ADJUST_TYPE_DOWN);
					trade.targetPositionSize = trade.remainingPositionSize;
					logger.info(symbol + " breakout target price:" + trade.targetPrice );
					//trade.reEnter = 0;
				}*/
			
				if ( trade.profitTake1 == false )
				{	
					if ( breakOutSize >= 3 )
					{
						BreakOut lastBreakout = breakouts.lastElement();
						BreakOut lastBreakoutPre = breakouts.elementAt( breakOutSize -2);
					
						if ( Utility.getLow(lastBreakout.highlowQuotes) < Utility.getLow(lastBreakoutPre.highlowQuotes)) 
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.POSITION_SIZE/2, data.close);
							closePositionByMarket( trade.POSITION_SIZE/2, data.close);
							trade.profitTake1 = true;
						}
					}
					/*
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.positionSize/2, data.close);
						closePosition( trade.positionSize/2, data.close);
						trade.profitTake1 = true;
					}*/
				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					//int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);
					entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);

					breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
					breakOutSize = breakouts.size();
					
					logger.info( symbol + " breakout size =" + breakOutSize);
					
					if ( breakOutSize >= 3 )
					{
						BreakOut lastBreakout = breakouts.lastElement();
						BreakOut lastBreakoutPre = breakouts.elementAt( breakOutSize -2);
					
						if ( Utility.getLow(lastBreakout.highlowQuotes) < Utility.getLow(lastBreakoutPre.highlowQuotes)) 
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
						}
					}

					
					/*
					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePosition( trade.remainingPositionSize, data.close);
					}
					*/
				}
						

				
				
				// looking to take profit if it does not look good
				// 1.  Quickly return to 20MA
				// 2.  successfully return to 20MA
				// 3.  High fly
				/*
				if ( breakouts.size() == 3 )  // two times take off
				{
					logger.warning(symbol + " 3 moves, take at least half profit");
					trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
				}*/
				
				
				// start to take profit after 2 returns
				/*
				if ( breakouts.size() == 3 )
				{
					logger.warning(symbol + " target hit, close position");
					closePosition();
				}*/
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}

			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
				}
			}
			else
			{
				// looking to take profit if it does not look good
				// 1.  Quickly return to 20MA
				// 2.  successfully return to 20MA
				// 3.  High fly
				/*
				if ( breakOutSize - firstTwoBars > 2 )  // two times take off
				{
					double breakOutHigh = Utility.getHigh(lastBreakout.highlowQuotes);
					double breakOutPullBackLow = Utility.getLow( lastBreakout.belowQuotes);
					trade.targetPrice = adjustPrice(breakOutPullBackLow + ( breakOutHigh - breakOutPullBackLow )/2, Constants.ADJUST_TYPE_UP);
					trade.targetPositionSize = trade.remainingPositionSize;
					logger.info(symbol + " breakout target price:" + trade.targetPrice );
					//trade.reEnter = 0;
				}*/
				
				
				if ( trade.profitTake1 == false )
				{	
					if ( breakOutSize >= 3 )
					{
						BreakOut lastBreakout = breakouts.lastElement();
						BreakOut lastBreakoutPre = breakouts.elementAt( breakOutSize -2);
					
						if ( Utility.getHigh(lastBreakout.highlowQuotes) > Utility.getHigh(lastBreakoutPre.highlowQuotes)) 
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.POSITION_SIZE/2, data.close);
							closePositionByMarket( trade.POSITION_SIZE/2, data.close);
							trade.profitTake1 = true;
						}
					}

					/*
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.positionSize/2, data.close);
						closePosition( trade.positionSize/2, data.close);
						trade.profitTake1 = true;
					}*/
				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					entryPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);

					breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
					breakOutSize = breakouts.size();
					
					if ( breakOutSize >= 3 )
					{
						BreakOut lastBreakout = breakouts.lastElement();
						BreakOut lastBreakoutPre = breakouts.elementAt( breakOutSize -2);
					
						if ( Utility.getHigh(lastBreakout.highlowQuotes) > Utility.getHigh(lastBreakoutPre.highlowQuotes)) 
						{
							AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket( trade.remainingPositionSize, data.close);
						}
					}

					/*
					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ))
					{	
						AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePosition( trade.remainingPositionSize, data.close);
					}*/
				}
			}
		}
	}


	
	public void exit123_all_small_timeframe(QuoteData data, Vector<QuoteData> qtsL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		int entryPos = trade.entryPos;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			// move stop to the first pull back to 20MA after two break outs
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					//trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop point 2 point hit, move stop to " + trade.stop);
					cancelOrder( trade.stopId );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else
			{
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					/*
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback > peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().highlow + (peaks.lastElement().pullback - peaks.lastElement().highlow)/2;
						trade.targetPositionSize = trade.positionSize/2;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}*/
					
					int numHighs = findNumLows( quotes, beginPos, lastbar, 1 );
					if (( numHighs >= 2 ) && ( trade.profitTake2 == false ))
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake2 = true;
					}

					if (( numHighs >= 3 ) && ( trade.profitTake3 == false ))
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize = trade.POSITION_SIZE/4;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake3 = true;
					}
					
					if (( numHighs >= 4 ) && ( trade.profitTake4 == false ))
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize = trade.POSITION_SIZE/4;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake4 = true;
					}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					//trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else
			{
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}

					/*
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);

					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback < peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().pullback + (peaks.lastElement().highlow - peaks.lastElement().pullback)/2;
						trade.targetPositionSize = trade.positionSize/2;
						logger.info( symbol + " lower low detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}*/
					
					int numHighs = findNumHighs ( quotes, beginPos, lastbar, 1 );
					if (( numHighs >= 2 ) && ( trade.profitTake2 == false ))
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake2 = true;
					}
					if (( numHighs >= 3 ) && ( trade.profitTake3 == false ))
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						trade.targetPositionSize = trade.POSITION_SIZE/4;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake3 = true;
					}
					if (( numHighs >= 4 ) && ( trade.profitTake4 == false ))
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						trade.targetPositionSize = trade.POSITION_SIZE/4;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake4 = true;
					}
			}
		}
	}


	
	
	
	
	
	public void exit123_3(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		int entryPos = trade.entryPos;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			// move stop to the first pull back to 20MA after two break outs
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					//trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " " + CHART + " MINUTE move stop 2 point hit, move stop to " + trade.stop + " " + data.time);
					test = true;
					cancelOrder( trade.stopId );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else
			{
				// take initial profit 
				if ( trade.initProfitTaken == false )
				{	
					// inital profit taken takes the profit so the main one gets a chance to live long
					//int beginPos = Utility.getHigh( quotes, trade.entryPos, lastbar).pos;
					int beginPos = trade.entryPos;
					logger.info( symbol + " " + CHART + " beginPos at "+ ((QuoteData)quotes[beginPos]).time );
					// look for first pull back with at least two bars;
					int numLows = findNumLows( quotes, beginPos, lastbar, 1 );
					if ( numLows > 0 )
					{
						// time to take initial profit and reset stops
						logger.info( symbol + " " + CHART + " take initial profit at "+ data.time + " @ " + trade.targetPrice);
						/*if ( trade.followUpTrade == true )
						{
							closePositionByMarket( trade.followUpPositionSize, data.close );
							if ( MODE == Constants.TEST_MODE)
								AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.followUpPositionSize, data.close);
						}*/

						trade.initProfitTaken = true;

					}
				}
				
				// adjust stop
				if (( trade.adjustStop == 0 ) && (trade.moveStop2 == false)) 
				{
					if (( trade.initProfitTaken == true ) || ( trade.targetReached == true))
					{
						// move stop
						cancelOrder( trade.stopId );
						trade.stop = Utility.getHigh( quotes, trade.entryPos, lastbar).high;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_UP);
						logger.info( symbol + " " + CHART + " adjust " + trade.remainingPositionSize + " positions to stop "+ trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						trade.adjustStop++;
					}
				}
				
				if ( trade.profitTake1 == false )// && ( trade.targetReached == false ))
				{	
					int beginPos = Utility.getHigh( quotes, trade.entryPos, lastbar).pos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}

					// assume first target has not been hit
					int numHighs = findNumLows( quotes, beginPos, lastbar, 1 );
					if ( numHighs >= 2 )
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_DOWN);
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " " + CHART + " 2 times push low detected, take profit at "+ data.time + " @ " + trade.targetPrice);

						if ( MODE == Constants.REAL_MODE)
							cancelOrder( trade.targetId );
						
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}

				}
				else if ( trade.profitTake2 == false )
				{
					quotes = quotesL;
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(quotes, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);

					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}

					int numHighs = findNumLows20( quotes, beginPos, lastbar, 5 );
					if ( numHighs >= 2 )
					{
						trade.takeProfit2Price = data.close;
						trade.takeProfit2Price = adjustPrice( trade.takeProfit2Price, Constants.ADJUST_TYPE_DOWN);
						trade.takeProfit2PosSize = trade.remainingPositionSize;
						logger.info( symbol + " higher high detected at large time frame, take profit at " + data.time + " @ " + trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.takeProfit2Price, trade.takeProfit2PosSize, null);//new Long(oca).toString());
						trade.profitTake2 = true;
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					//trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " " + CHART + " MINUTE move stop 2 point hit, move stop to " + trade.stop + " " + data.time);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else
			{
				if ( trade.initProfitTaken == false )
				{	
					// inital profit taken takes the profit so the main one gets a chance to live long
					int beginPos = trade.entryPos;
					logger.info( symbol + " " + CHART + " beginPos at "+ ((QuoteData)quotes[beginPos]).time );
					// look for first pull back with at least two bars;
					int numHighs = findNumHighs( quotes, beginPos, lastbar, 1 );
					if ( numHighs > 0 )
					{
						// time to take initial profit and reset stops
						logger.info( symbol + " " + CHART + " take initial profit at "+ data.time );
						/*if ( trade.followUpTrade == true )
						{
							closePositionByMarket( trade.followUpPositionSize, data.close );
							if ( MODE == Constants.TEST_MODE)
								AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.followUpPositionSize, data.close);
						}*/

						trade.initProfitTaken = true;

					}
				}
				
				// adjust stop
				if (( trade.adjustStop == 0 ) && (trade.moveStop2 == false)) 
				{
					if (( trade.initProfitTaken == true ) || ( trade.targetReached == true))
					{
						// move stop
						cancelOrder( trade.stopId );
						trade.stop = Utility.getLow( quotes, trade.entryPos, lastbar).low;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.info( symbol + " " + CHART + " adjust " + trade.remainingPositionSize + " positions to stop "+ trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						trade.adjustStop ++;
					}
				}

				if ( trade.profitTake1 == false ) //&& ( trade.targetReached == false ))
				{	
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}

					// change the target to take profit 1
					int numHighs = findNumHighs ( quotes, beginPos, lastbar, 1 );
					if ( numHighs >= 2 )
					{
						trade.targetPrice = data.close;
						trade.targetPrice = adjustPrice( trade.targetPrice, Constants.ADJUST_TYPE_UP);
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " profot begin at "+ ((QuoteData)quotes[beginPos]).time);
						logger.info( symbol + " 2 times push high detected, take profit at "+ data.time + " @ " + trade.targetPrice);
						
						if ( MODE == Constants.REAL_MODE)
							cancelOrder( trade.targetId );

						trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}

				}
				else if ( trade.profitTake2 == false )
				{
					quotes = quotesL;
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(quotes, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);
					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}

					int numHighs = findNumHighs20 ( quotes, beginPos, lastbar, 5 );
					if ( numHighs >= 2 )
					{
						trade.takeProfit2Price = data.close;
						trade.takeProfit2Price = adjustPrice( trade.takeProfit2Price, Constants.ADJUST_TYPE_UP);
						trade.takeProfit2PosSize = trade.remainingPositionSize;
						logger.info( symbol + " lower low detected, take profit at "+ data.time + " @ " + trade.targetPrice);
						trade.takeProfit2Id = placeLmtOrder(Constants.ACTION_SELL, trade.takeProfit2Price, trade.takeProfit2PosSize, null);//new Long(oca).toString());
						trade.profitTake2 = true;
					}
				}
			}
		}
	}

	
	
	
	
	
	
	
	
	
	public void exit123_4(QuoteData data, int ti, Vector<QuoteData> qts, Vector<QuoteData> qtsL)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, trade.entryPos);
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			
			// move stop to the first pull back to 20MA after two break outs
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else
			{
				PushHighLow phl_cur = findLastNLow20( quotes, trade.entryPos, lastbar, 3 );
				if ( phl_cur == null )
					return;
				
				int preHigh = phl_cur.prePos;
				for ( int i = preHigh; i > trade.entryPos; i--)
				{	
					PushHighLow phl_pre = findLastNLow20( quotes, trade.entryPos, i, 3 );
					if ( phl_pre != null )
					{
						// two push, close all position
						AddTradeCloseRecord( data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
						return;
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, trade.entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}

			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else
			{
				PushHighLow phl_cur = findLastNHigh20( quotes, trade.entryPos, lastbar, 3 );
				if ( phl_cur == null )
					return;
				
				int preHigh = phl_cur.prePos;
				for ( int i = preHigh; i > trade.entryPos; i--)
				{	
					PushHighLow phl_pre = findLastNHigh20( quotes, trade.entryPos, i, 3 );
					if ( phl_pre != null )
					{
						// two push, close all position
						AddTradeCloseRecord( data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket( trade.remainingPositionSize, data.close);
						return;
					}
				}
			}
		}
	}

	// exit 5, do not set move stop 2 and take 25% profit on every breakout after 3
	public void exit123_5(QuoteData data, int ti, Vector<QuoteData> qts, Vector<QuoteData> qtsL)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		int entryPos = trade.entryPos;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			// move stop to the first pull back to 20MA after two break outs
			//if ( trade.moveStop2 == false )
			{
				/*
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop point 2 point hit, move stop to " + trade.stop);
					cancelOrder( trade.stopId );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}*/
			}
			//else
			{
					int beginPos = entryPos;
					/*
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}*/
				
					int count = findNumLows20 ( quotes, beginPos, lastbar, 3 );
					System.out.println("find number low count:" + count);
					//Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
					if (( count ==3 ) && ( trade.profitTake1 == false ))
					{
						int takeProfitSize = trade.POSITION_SIZE/4;
						if ( takeProfitSize > trade.remainingPositionSize )
							takeProfitSize = trade.remainingPositionSize;
						logger.info( symbol + " " + CHART + "minute, take profit at lower 3 ");
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, takeProfitSize, null);
						trade.profitTake1 = true;
					}
					else if (( count ==4 ) && ( trade.profitTake2 == false ))
					{
						int takeProfitSize = trade.POSITION_SIZE/4;
						if ( takeProfitSize > trade.remainingPositionSize )
							takeProfitSize = trade.remainingPositionSize;
						logger.info( symbol + " " + CHART + "minute, take profit at lower 4 ");
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, takeProfitSize, null);
						trade.profitTake2 = true;
					}
					else if (( count ==5 ) && ( trade.profitTake3 == false ))
					{
						int takeProfitSize = trade.POSITION_SIZE/4;
						if ( takeProfitSize > trade.remainingPositionSize )
							takeProfitSize = trade.remainingPositionSize;
						logger.info( symbol + " " + CHART + "minute, take profit at lower 5 ");
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, takeProfitSize, null);
						trade.profitTake3 = true;
					}
					else if (( count ==6 ) && ( trade.profitTake4 == false ))
					{
						int takeProfitSize = trade.POSITION_SIZE/4;
						if ( takeProfitSize > trade.remainingPositionSize )
							takeProfitSize = trade.remainingPositionSize;
						logger.info( symbol + " " + CHART + "minute, take profit at lower 5 ");
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, takeProfitSize, null);
						trade.profitTake4 = true;
					}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}

			if ( trade.moveStop2 == false )
			{
				/*
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}*/
			}
			else
			{
				if ( trade.profitTake1 == false )
				{	
					int beginPos = entryPos;
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);

					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback < peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().pullback + (peaks.lastElement().highlow - peaks.lastElement().pullback)/2;
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " lower low detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}
				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);
					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback < peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().pullback + (peaks.lastElement().highlow - peaks.lastElement().pullback)/2;
						trade.targetPositionSize = trade.remainingPositionSize;
						logger.info( symbol + " lower low detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					}
				}
			}
		}
	}

	
	
	public void exit123_6(QuoteData data, int ti, Vector<QuoteData> qts, Vector<QuoteData> qtsL)
	{
		Object[] quotes = qts.toArray();
		int lastbar = quotes.length-1;
		double[] ema20 = Indicator.calculateEMA(qts, 20);

		int entryPos = trade.entryPos;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutDowns2(quotes, ema20, entryPos);
			
			//check first break out that has more than 2 bars;
			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;

			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			
			// move stop to the first pull back to 20MA after two break outs
			/*if ( trade.moveStop2 == false )
			{
				
				if ( breakOutSize - firstTwoBars == 2 )  // two times take off
				{
					//trade.stop = Utility.getHigh(breakouts.elementAt(1).belowQuotes);
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop point 2 point hit, move stop to " + trade.stop);
					cancelOrder( trade.stopId );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else*/
			{
				if ( trade.profitTake1 == false )
				{	
					int beginPos = entryPos;
					/*
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback > peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().highlow + (peaks.lastElement().pullback - peaks.lastElement().highlow)/2;
						trade.targetPositionSize = trade.positionSize/2;
						logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}*/ 
					
					int count = findNumLows20 ( quotes, beginPos, lastbar, 2); 
					if (((CHART == Constants.CHART_1_MIN) && (count == 3)) || (CHART != Constants.CHART_1_MIN) && (count == 2)) 
					{
						trade.targetPrice = data.close;
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " 3 top on smaller chart detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
						
						trade.stop = trade.price;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " move stop point 2 point hit, move stop to " + trade.stop);
						cancelOrder( trade.stopId );
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						trade.moveStop2 = true;
						trade.reEnter = 0;  // can not re-enter once the stop has been moved
					}

				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);

					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).high < ema20[i]) && (((QuoteData)quotes[i+1]).high < ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback > peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().highlow + (peaks.lastElement().pullback - peaks.lastElement().highlow)/2;
						trade.targetPositionSize = trade.remainingPositionSize;
						logger.info( symbol + " higher high detected at large time frame, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			Vector<BreakOut> breakouts = Pattern.findMABreakoutUps2(quotes, ema20, entryPos);

			int breakOutSize = breakouts.size();
			int firstTwoBars = 0;
			for ( int i = 0; i < breakOutSize; i++ )  // two times take off
			{
				if ( breakouts.elementAt(i).highlowQuotes.size() >= 0 )
				{
					firstTwoBars = i;
					break;
				}
			}
			/*
			if ( trade.moveStop2 == false )
			{
				if ( breakOutSize - firstTwoBars == 2 )
				{
					logger.warning(symbol + " move stop point 2 point hit - move stop");
					//trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
					trade.stop = trade.price;
					trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
					logger.warning(symbol + " move stop to " + trade.stop);
					cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.moveStop2 = true;
					trade.reEnter = 0;  // can not re-enter once the stop has been moved
				}
			}
			else*/
			{
				if ( trade.profitTake1 == false )
				{	
					int beginPos = entryPos;
					/*
					for ( int i = entryPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);

					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback < peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().pullback + (peaks.lastElement().highlow - peaks.lastElement().pullback)/2;
						trade.targetPositionSize = trade.positionSize/2;
						logger.info( symbol + " lower low detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
					}*/
					
					int count = findNumHighs20 ( quotes, beginPos, lastbar, 2); 
					if (((CHART == Constants.CHART_1_MIN) && (count == 3)) || (CHART != Constants.CHART_1_MIN) && (count == 2)) 
					{
						trade.targetPrice = data.close;
						trade.targetPositionSize = trade.POSITION_SIZE/2;
						logger.info( symbol + " 3 top on smaller chart detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
						trade.profitTake1 = true;
						
						
						logger.warning(symbol + " move stop point 2 point hit - move stop");
						//trade.stop = Utility.getLow(breakouts.elementAt(1).belowQuotes);
						trade.stop = trade.price;
						trade.stop = adjustPrice( trade.stop, Constants.ADJUST_TYPE_DOWN);
						logger.warning(symbol + " move stop to " + trade.stop);
						cancelOrder( trade.stopId);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						trade.moveStop2 = true;
						trade.reEnter = 0;  // can not re-enter once the stop has been moved

					}

				}
				else
				{
					quotes = qtsL.toArray();
					lastbar = quotes.length-1;
					ema20 = Indicator.calculateEMA(qtsL, 20);
					int beginPos = Utility.getQuotePositionByMinute( quotes, trade.entryTimeL);
					for ( int i = beginPos; i < lastbar-1; i++ )
					{
						if ((((QuoteData)quotes[i]).low > ema20[i]) && (((QuoteData)quotes[i+1]).low > ema20[i+1]))
						{
							beginPos = i;
							break;
						}
					}
				
					Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
				
					if (( peaks.size() >=3 ) && ( peaks.lastElement().pullback < peaks.elementAt(peaks.size()-2).pullback))
					{
						trade.targetPrice = peaks.lastElement().pullback + (peaks.lastElement().highlow - peaks.lastElement().pullback)/2;
						trade.targetPositionSize = trade.remainingPositionSize;
						logger.info( symbol + " lower low detected, take profit at "+ trade.targetPrice);
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPositionSize, null);//new Long(oca).toString());
					}
				}
			}
		}
	}


	
	
	
	
	public void exit123_cnt(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length-1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int beginPos = trade.pullBackPos;
			Vector<Peak> peaks = findLowerLow4( quotes, beginPos, lastbar);
			
			if ( trade.profitTake1 == false )
			{	
				if ( peaks.size() ==2 ) 
				{
					trade.targetPrice = peaks.lastElement().highlow;
					trade.targetPositionSize = trade.POSITION_SIZE/2;
					logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
					trade.profitTake1 = true;
					return;
				}
			}	
			else
			{	
				if ( peaks.size() == 3 ) 
				{
					trade.targetPrice = peaks.lastElement().highlow;
					trade.targetPositionSize = trade.remainingPositionSize;
					logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
					return;
				}
			}
		}
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			int beginPos = trade.pullBackPos;
			//Vector<Peak> peaks = findHigherHigh4( quotes, beginPos, lastbar);
			int count = findNumHighs20 ( quotes, beginPos, lastbar, 2 );

			if ( trade.profitTake1 == false )
			{	
				//if (( peaks.size() ==2 ))
				if ( count == 2)
				{
					trade.targetPrice = ((QuoteData)quotes[lastbar]).close;
					trade.targetPositionSize = trade.POSITION_SIZE/2;
					logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
					trade.profitTake1 = true;
					return;
				}
			}	
			else
			{	
				//if (( peaks.size() == 4 ) )
				if (count == 3)
				{
					//trade.targetPrice = peaks.lastElement().highlow;
					trade.targetPrice = ((QuoteData)quotes[lastbar]).close;
					trade.targetPositionSize = trade.remainingPositionSize;
					logger.info( symbol + " higher high detected, take profit at "+ trade.targetPrice);
					return;
				}
			}
		}
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	boolean checkProfitTake( int lastbar )
	{
		for ( int i = 1; i <=8; i++)
		{
			if ( trade.takeProfit.containsValue(lastbar-i))
			{
				return true;
			}
		}
		
		return false;
		
	}
	
	
	
	private int getStopSize()
	{
		if (CHART == Constants.CHART_1_MIN)
			return 4;
		else if (CHART == Constants.CHART_5_MIN)
			return 7;
		else if (CHART == Constants.CHART_15_MIN)
			return 10;
		
		return 7;
			
	}
	
	
	
/*	
	public static PushHighLow findLastNHigh( Object[] quotes, int start, int lastbar, int n )
	{
		double lastHigh = Utility.getHigh(quotes, start, lastbar-1).high;
		if (((QuoteData)quotes[lastbar]).high < lastHigh )
			return null;  // not the highest

		// we find the last time
		int thisHighStarts = lastbar -1;
		while ((thisHighStarts > start) && (((QuoteData)quotes[thisHighStarts-1]).high <= ((QuoteData)quotes[thisHighStarts]).high )) 
		{	
			thisHighStarts--;
		}
		
		if ( thisHighStarts == start)
			return null;   // stright , no pull back

		//logger.info(symbol + " this high starts " + ((QuoteData)quotes[thisHighStarts]).time );

		// find the highest between pos and this highstarts
		int previousHighPos = -1;
		double previousHigh = 0;
		for ( int i = start; i <thisHighStarts; i++)
		{
			if ( ((QuoteData)quotes[i]).high > previousHigh )
			{
				previousHigh = ((QuoteData)quotes[i]).high;
				previousHighPos = i;
			}
		}
		
		if ( previousHighPos == -1 )
			return null;   // this high could start from 200ma so there is no previous high

		if (((QuoteData)quotes[lastbar-1]).high > ((QuoteData)quotes[previousHighPos]).high )
			return null; // not the first bar
		
		//logger.info(symbol + " previousHighPos is " + ((QuoteData)quotes[thisHighStarts]).time );

		if ( lastbar - previousHighPos > n )
		{
			PushHighLow phl = new PushHighLow(previousHighPos,lastbar);
			phl.pullBack = Utility.getLow( quotes, previousHighPos, lastbar);
			return phl;
		}
		else
			return null;
		
	}

	public static PushHighLow findLastNLow( Object[] quotes, int start, int lastbar, int n )
	{
		double lastLow = Utility.getLow(quotes, start, lastbar-1).low;
		if (((QuoteData)quotes[lastbar]).low > lastLow )
			return null;  // not the lowest

		// we find the last time
		int thisLowStarts = lastbar -1;
		while ((thisLowStarts > start) && (((QuoteData)quotes[thisLowStarts-1]).low >= ((QuoteData)quotes[thisLowStarts]).low )) 
		{	
			thisLowStarts--;
		}
		
		if ( thisLowStarts == start)
			return null;   // stright , no pull back

		//logger.info(symbol + " this high starts " + ((QuoteData)quotes[thisHighStarts]).time );

		// find the highest between pos and this highstarts
		int previousLowPos = -1;
		double previousLow = 999;
		for ( int i = start; i <thisLowStarts; i++)
		{
			if ( ((QuoteData)quotes[i]).low < previousLow )
			{
				previousLow = ((QuoteData)quotes[i]).low;
				previousLowPos = i;
			}
		}
		
		if ( previousLowPos == -1 )
			return null;   // this high could start from 200ma so there is no previous high

		if (((QuoteData)quotes[lastbar-1]).low < ((QuoteData)quotes[previousLowPos]).low )
			return null; // not the first bar

		//logger.info(symbol + " previousHighPos is " + ((QuoteData)quotes[thisHighStarts]).time );

		if ( lastbar - previousLowPos > n )
		{
			PushHighLow phl = new PushHighLow(previousLowPos,lastbar);
			phl.pullBack = Utility.getHigh( quotes, previousLowPos, lastbar);
			return phl;
		}
		else
			return null;
		
	}
*/
	
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
		QuoteData lastLow = Utility.getLow(quotes, start -20, lastbar-1);

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

	@Override
	public boolean inExitingTime(String time)
	{
		// TODO Auto-generated method stub
		return false;
	}

	
	
}
