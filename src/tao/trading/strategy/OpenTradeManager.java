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


public class OpenTradeManager extends TradeManager
{
	public OpenTradeManager(EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super( client, contract, symbolIndex, logger );
	}


	public Trade calculateOpenTrade( Vector<QuoteData> qts, QuoteData data)
	{
		int size = qts.size();
		Object[] quotes = qts.toArray();
		int lastbar = size -1;

		//System.out.println("size=" + size + " action=" + action + " entryPrice=" + entryPrice + " lastbarhigh=" + ((QuoteData)quotes[lastbar]).high + " lastbarlow=" + ((QuoteData)quotes[lastbar]).low);
		// at least high 20MA once and now it is all time high
		if (data.high > ((QuoteData)quotes[lastbar]).high ) 
		{
			trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
			trade.price = ((QuoteData)quotes[lastbar]).high;
			trade.action = Constants.ACTION_BUY;
			trade.status = Constants.STATUS_OPEN;
			trade.POSITION_SIZE = 1;
			trade.reEnter = 1;
			trade.stop = ((QuoteData)quotes[lastbar]).low;
			trade.moveStopPrice = data.close + (data.close - ((QuoteData)quotes[lastbar]).low);
			
			// place the order immediately
			placeMktOrder();
			trade.status = Constants.STATUS_PLACED;
			trade.trackQuotes.clear();

			return trade;
		}
		else if (data.low < ((QuoteData)quotes[lastbar]).low )
		{
			trade = new Trade(m_contract.m_symbol + "." + m_contract.m_currency);
			trade.price = ((QuoteData)quotes[lastbar]).low;
			trade.action = Constants.ACTION_SELL;
			trade.status = Constants.STATUS_OPEN;
			trade.POSITION_SIZE = 1;
			trade.reEnter = 1;
			trade.stop = ((QuoteData)quotes[lastbar]).low;
			trade.moveStopPrice = data.close - (((QuoteData)quotes[lastbar]).high - data.close);
			
			// place the order immediately
			placeMktOrder();
			trade.status = Constants.STATUS_PLACED;
			trade.trackQuotes.clear();

			return trade;
		}
		return null;
	}

	/*
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
					closeTrade( );
					return;
				}
				else
				{	
					double low = getLow(trade.trackQuotes);
					double average = getAverage(trade.trackQuotes);
					
					if ( data.high - low > 4 * average) 
					{
						logger.info("price has travelled but turned back, exit trade");
						closeTrade();
						return;
					}
					
					logger.info("Stop hit, exit"); 
					Order order = createOrder();
					order.m_action = Constants.ACTION_BUY;
					order.m_totalQuantity = trade.positionSize;
						
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
						closeTrade();
					}
				}
				else 
				{
					if ( data.low < targetPrice) // default target is 200MA
					{
						logger.info("Trade " + trade.orderId + " target hit, exist");
						closeTrade();
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
					closeTrade( );
					return;
				}
				else
				{
					double high = getHigh(trade.trackQuotes);
					double average = getAverage(trade.trackQuotes);
					
					if ( high - data.low > 4 * average) 
					{
						logger.info("price has travelled but turned back, exit trade");
						closeTrade();
						return;
					}

					logger.info("Stop hit, exit"); 
					Order order = createOrder();
					order.m_action = Constants.ACTION_SELL;
					order.m_totalQuantity = trade.positionSize;
						
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
						closeTrade();
					}
				}
				else 
				{
					if ( data.high > targetPrice) // default target is 200MA
					{
						logger.info("Trade " + trade.orderId + " target hit, exist");
						closeTrade();
					}
				}
			}
		}
	}
	
	
	public void trackTradeEntry(QuoteData quote)
	{
		if (trade.action.equals(Constants.ACTION_SELL) && trade.status.equals(Constants.STATUS_STOPPEDOUT))
		{
			if (quote.low < trade.price)
			{
				Order order = createOrder();
				order.m_action = trade.action;
				order.m_totalQuantity = trade.positionSize;
				placeMktOrder(trade, order);
				trade.trackQuotes.clear();
			}
		} 
		else if (trade.action.equals(Constants.ACTION_BUY) && trade.status.equals(Constants.STATUS_STOPPEDOUT))
		{
			if (quote.high < trade.price)
			{
				Order order = createOrder();
				order.m_action = trade.action;
				order.m_totalQuantity = trade.positionSize;
				placeMktOrder(trade, order);
				trade.trackQuotes.clear();
			}
		}
	}
*/

	public void trackTradeExit(QuoteData data)
	{
		// wait for five bars and sell
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (data.low < trade.moveStopPrice)
			{
				logger.info("price cross risk, move the stops");
				m_client.cancelOrder(trade.stopId);
				trade.stop = trade.price;
				placeStopOrder(Constants.ACTION_BUY, trade.stop);
			}
				
			if ( trade.trackQuotes.size() > 5)
			{
				double low = getLow( trade.trackQuotes);
				if (data.low < low)
				{
					logger.info("Trade " + trade.orderId + " target hit, exist");
					closePosition();
				}
			}
		} 
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (data.high > trade.moveStopPrice)
			{
				logger.info("price cross risk, move the stops");
				m_client.cancelOrder(trade.stopId);
				trade.stop = trade.price;
				placeStopOrder(Constants.ACTION_SELL, trade.stop);
			}
	
			if ( trade.trackQuotes.size() > 5)
			{
				double high = getHigh( trade.trackQuotes);
				if (data.high > high)
				{
					logger.info("Trade " + trade.orderId + " target hit, exist");
					closePosition();
				}
			}
		}
	}



}
