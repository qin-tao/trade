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


public class Stretch_BreakoutTradeManager extends TradeManager
{
	public Stretch_BreakoutTradeManager(EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super( client, contract, symbolIndex, logger );
	}


	public Trade calculateStretch( Vector<QuoteData> qts, String action, double entryPrice)
	{
		int size = qts.size();
		Object[] quotes = qts.toArray();
		int lastbar = size -1;

		//System.out.println("size=" + size + " action=" + action + " entryPrice=" + entryPrice + " lastbarhigh=" + ((QuoteData)quotes[lastbar]).high + " lastbarlow=" + ((QuoteData)quotes[lastbar]).low);
		// at least high 20MA once and now it is all time high
		if ((Constants.ACTION_BUY.equals(action)) && (((QuoteData)quotes[lastbar]).low < entryPrice ))
		{
			Trade tt = new Trade();
			tt.action = Constants.ACTION_BUY;
			tt.status = Constants.STATUS_OPEN;
			tt.reEnter = Constants.NUM_REENTER_STRETCH;
			tt.addEntryQuotes((QuoteData)quotes[lastbar]);
			return tt;
		}
		else if ((Constants.ACTION_SELL.equals(action)) && (((QuoteData)quotes[lastbar]).high > entryPrice ))
		{
			Trade tt = new Trade();
			tt.action = Constants.ACTION_SELL;
			tt.status = Constants.STATUS_OPEN;
			tt.reEnter = Constants.NUM_REENTER_STRETCH;
			tt.addEntryQuotes((QuoteData)quotes[lastbar]);
			return tt;
		}
		return null;
	}


	public Trade calculateStretchHigh(int index, Vector<QuoteData> qts, double[] sma200, double[] sma20)
	{
		int size = qts.size();
		Object[] quotes = qts.toArray();
		int lastbar = size -1;
		
		// this bar has to be the first bar breakout from the previous high
		// we are looking for the past 3 hours  20bar * 3 = 60 bars

		
		// at least is distance from 200ma
		double distance = getAverage(qts) * 5;
		if ((((QuoteData)quotes[lastbar]).close - sma200[lastbar] ) < distance )
		{	
			// this is not stretch enough
			return null; // still keep going up
		}

		logger.info(index + " calculateStretchHigh" + " distance " + distance + " reached");

		
		
		// need to look for a pull back to 200MA
		int pos = 0;
		for ( int i = lastbar-1; i >=0; i-- )
		{
			if (((QuoteData)quotes[i]).low <= sma200[i])
			{
				pos = i;
				break;
			}
		}
		
		if (pos == 0)
		 return null;
		
		logger.info("200MA start from pos" + pos + " " + ((QuoteData)quotes[pos]).time);
		
		boolean sma20touched = false;
		int lasttouchpos = 0;
		for ( int i = pos; i < lastbar; i++)
		{
			if (((QuoteData)quotes[i]).low <= sma20[i])
			{
				sma20touched = true;
				lasttouchpos = i;
			}
		}
		
		if (!sma20touched)
			return null;
		else
			logger.info("last touch 20MA at " + ((QuoteData)quotes[lasttouchpos]).time);

		double lasthigh = 0;
		int lasthighpos = 0;
		for ( int i = pos; i <= lasttouchpos; i++)
		{
			if (((QuoteData)quotes[i]).high > lasthigh)
			{
				lasthigh = ((QuoteData)quotes[i]).high;
				lasthighpos = i;
			}
		}
		logger.info("last high is " + lasthigh + " " + ((QuoteData)quotes[lasthighpos]).time);

		
		if (( lasthigh - sma200[lasthighpos])< distance*0.8)
		{
			logger.info("last high did not reach 70% distance " + distance);
			return null;
		}
		else
			logger.info("last high is " + lasthigh);

		if (((QuoteData)quotes[lastbar]).high > lasthigh )
		{
			
			//enter trade;
			Trade tt = new Trade();
			tt.action = Constants.ACTION_SELL;
			tt.status = Constants.STATUS_OPEN;
			tt.reEnter = Constants.MAX_RETRY;
			tt.addEntryQuotes((QuoteData)quotes[lastbar]);
			System.out.println("Trade placed:" + contracts[index].m_symbol + "." + contracts[index].m_currency + tt.toString());
			return tt;
			
		}
		else{
			logger.info("not at the all time high");
			return null;
		}
	}

	
	
	
	public void trackTradeExit(QuoteData data, double targetPrice, double[] sma200 )
	{
		int lastbar = sma200.length -1;

		if (Constants.ACTION_SELL.equals(trade.action))		
		{
			// first check stop
			if ( data.high > trade.stop )
			{
				trade.reEnter--;
				if ( trade.reEnter <= 0 )
				{
					logger.info("Maxium retry hit, exit trade"); 
					closePosition( );
					return;
				}
				else
				{	
					double low = getLow(trade.trackQuotes);
					double average = getAverage(trade.trackQuotes);
					
					if ( data.high - low > 4 * average) 
					{
						logger.info("price has travelled but turned back, exit trade");
						closePosition();
						return;
					}
					
					logger.info("Stop hit, exit"); 
					Order order = createMktOrder();
					order.m_action = Constants.ACTION_BUY;
					order.m_totalQuantity = trade.POSITION_SIZE;
						
					placeMktOrder(trade, order);
					trade.status = Constants.STATUS_STOPPEDOUT;
					trade.entryQuotes.clear();
					trade.trackQuotes.clear();
					return;
				}

			}
			
			if ( targetPrice != 0 )
			{
				if ( Constants.TARGET_200MA == targetPrice )
				{
					if ( data.low < sma200[lastbar]) // default target is 200MA
					{
						logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
						closePosition();
					}
				}
				else 
				{
					if ( data.low < targetPrice) // default target is 200MA
					{
						logger.info("Trade " + trade.orderId + " target hit, exist");
						closePosition();
					}
				}
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))		
		{
			if ( data.low < trade.stop )
			{
				trade.reEnter--;
				if ( trade.reEnter <= 0 )
				{
					logger.info("Maxium retry hit, exit trade"); 
					closePosition( );
					return;
				}
				else
				{
					double high = getHigh(trade.trackQuotes);
					double average = getAverage(trade.trackQuotes);
					
					if ( high - data.low > 4 * average) 
					{
						logger.info("price has travelled but turned back, exit trade");
						closePosition();
						return;
					}

					logger.info("Stop hit, exit"); 
					Order order = createMktOrder();
					order.m_action = Constants.ACTION_SELL;
					order.m_totalQuantity = trade.POSITION_SIZE;
						
					placeMktOrder(trade, order);
					trade.status = Constants.STATUS_STOPPEDOUT;
					trade.entryQuotes.clear();
					trade.trackQuotes.clear();
					return;
				}

			} 

			if ( targetPrice != 0 )
			{
				if ( Constants.TARGET_200MA == targetPrice )
				{
					if ( data.high > sma200[lastbar]) // default target is 200MA
					{
						logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
						closePosition();
					}
				}
				else 
				{
					if ( data.high > targetPrice) // default target is 200MA
					{
						logger.info("Trade " + trade.orderId + " target hit, exist");
						closePosition();
					}
				}
			}
		}
	}
	
	
	public void trackTradeEntry(QuoteData quote)
	{
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((trade.entryQuotes != null) && (trade.entryQuotes.size() != 0))
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.low < lastQuote.low)
				{
					logger.info("quote low " + quote.low + " < " + lastQuote.low);
					Order order = createMktOrder();
					order.m_action = trade.action;
					order.m_totalQuantity = trade.POSITION_SIZE;
					
					trade.stop = getHigh(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder(trade, order);
					trade.position = Constants.POSITION_SHORT;
					trade.trackQuotes.clear();
				}
			} 
			else
			{
				// has to wait till entry Quotes gets filled
			}
		} 
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((trade.entryQuotes != null) && (trade.entryQuotes.size() != 0))
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.high > lastQuote.high)
				{
					Order order = createMktOrder();
					order.m_action = trade.action;
					order.m_totalQuantity = trade.POSITION_SIZE;

					trade.stop = getLow(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder(trade, order);
					trade.position = Constants.POSITION_LONG;
					trade.trackQuotes.clear();
				}
				else
				{
					// has to wait for entry Quotes to get filled
				}
			}
		}
	}


	// we override this method so it does not place stop order
	public void placeMktOrder(Trade trade, Order order)
	{
		System.out.println(displayOrder(order));

		m_client.placeOrder(order.m_orderId, m_contract, order);
		System.out.println(m_contract.m_symbol + "." + m_contract.m_currency + " market order placed");

		trade.orderId = order.m_orderId;
		trade.executed = true;
		trade.status = Constants.STATUS_PLACED;
		trade.entryQuotes.clear(); // clear entry Quotes
		logger.info("Trade:" + trade.toString());

	}




}
