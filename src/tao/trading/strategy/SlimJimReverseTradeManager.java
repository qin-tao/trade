package tao.trading.strategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

public class SlimJimReverseTradeManager extends TradeManager
{
	//protected static Logger logger;

	public SlimJimReverseTradeManager(EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super( client, contract, symbolIndex, logger );
/*
		logFile = "c:/trade_logs/slimjimreverse.log";

		try
		{
			// Create an appending file handler
			//boolean append = true;
			//FileHandler handler = new FileHandler(logFile, append);
			FileHandler handler = new FileHandler(logFile);

			// Add to the desired logger
			logger = Logger.getLogger("SlimJimReverseTradeManager");
			logger.addHandler(handler);
		
		} 
		catch (IOException e)
		{
		}
*/
	}

	public Order createMktOrder()
	{
		Order order = new Order();
		order.m_orderId = getOrderId();
		order.m_account = "DU31237";
		order.m_clientId = m_clientId;
		order.m_orderType = "MKT";
		return order;
	}

	public Trade getTrade()
	{
		return trade;
	}

	public void placeTrade(Trade t)
	{
		setTrade(t);
		placeMktOrder(t);

	}

	public void setTrade(Trade t)
	{
		this.trade = t;
	}

	public void placeMktOrder(Trade trade)
	{
		Order order = createMktOrder();
		order.m_action = trade.action;
		order.m_totalQuantity = trade.POSITION_SIZE;
		placeMktOrder(trade, order);
	}

	public void placeMktOrder(Trade trade, Order order)
	{
		System.out.println(displayOrder(order));

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " market order placed");

		trade.orderId = order.m_orderId;
		trade.executed = true;
		trade.entryQuotes = null; // clear entry Quotes
		logger.info("Trade:" + trade.toString());

		if (trade.stop != 0)
		{
			// place a stop order
			order.m_orderId = order.m_orderId + 10000;
			if (Constants.ACTION_BUY.equals(order.m_action))
				order.m_action = Constants.ACTION_SELL;
			else
				order.m_action = Constants.ACTION_BUY;
			order.m_orderType = "STP";
			order.m_auxPrice = trade.stop;

			m_client.placeOrder(order.m_orderId, m_contract, order);
			logger.info("Stop order placed:" + trade.action + " " + trade.POSITION_SIZE + " " + trade.stop);
		}

		trade.stopId = order.m_orderId;
		writeToTradeLog(trade);

	}

	public void closeTrade(Trade trade)
	{
		Order order = createMktOrder();
		order.m_action = trade.action.equals("BUY") ? "SELL" : "BUY";
		order.m_totalQuantity = trade.POSITION_SIZE;

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info("close order placed:" + trade.action + " " + trade.POSITION_SIZE);

		trade.orderId = order.m_orderId;
		trade.status = Constants.STATUS_CLOSED;

		// cancel the stop order
		// m_client.cancelOrder(order.m_orderId + 10000);
		// logger.info("stop order canbcelled:" + order.m_orderId + 10000);

		writeToTradeLog(trade);
		trade = null;

	}

	public void placeStopOrder(String action, int quality, double stopPrice)
	{
		Order order = new Order();

		order.m_account = "DU31237";
		order.m_orderId = getOrderId();
		order.m_clientId = 200;
		order.m_action = action; // "BUY";
		order.m_totalQuantity = quality; // 10;
		order.m_orderType = "STP";
		order.m_auxPrice = stopPrice;

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info("Stop order placed:" + action + " " + quality + " " + stopPrice);
	}

	/*
	 * synchronized public void orderFilled( int orderId, int filled, double
	 * avgFillPrice) { Iterator iterator = trades.iterator();
	 * 
	 * while( iterator. hasNext() ) { Trade trade = (Trade)( iterator.next() );
	 * if (trade.orderId == orderId ) { trade.filled += filled;
	 * //trade.avgFillPrice = avgFillPrice; } else if ( trade.stopId == orderId)
	 * { trade.status = Constants.STATUS_STOPPEDOUT; trade.entryQuotes = null;
	 * trade.reEnter = trade.reEnter -1; if ( trade.reEnter < 1 )
	 * removeTrade(trade.id); } }
	 * 
	 * }
	 */

	public void trackTradeExit(QuoteData data)
	{
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (data.low < trade.targetPrice)
			{
				logger.info("Trade " + trade.orderId + " target hit, exist");
				closeTrade(trade);
			}
		} else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (data.high < trade.targetPrice)
			{
				logger.info("Trade " + trade.orderId + " target hit, exist");
				closeTrade(trade);
			}
		}
	}

	public void trackTradeEntry(QuoteData quote)
	{
		if (on_pause > 0)
		{
			on_pause--;
			return;
		}

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if (trade.entryQuotes != null)
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.low < lastQuote.low)
				{
					logger.info("quote low " + quote.low + " < " + lastQuote.low);
					Order order = createMktOrder();
					order.m_action = trade.action;
					order.m_totalQuantity = trade.POSITION_SIZE;
					if (Constants.STATUS_REVERSAL.equals(trade.status))
					{
						logger
								.info("position is reversal, need to cover the reversal position, double the size");
						order.m_totalQuantity = trade.POSITION_SIZE * 2;
					}

					trade.stop = getHigh(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder(trade, order);
				}
			} else
			{
				// has to wait till entry Quotes gets filled
			}
		} else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if (trade.entryQuotes != null)
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.high > lastQuote.high)
				{
					Order order = createMktOrder();
					order.m_action = trade.action;
					order.m_totalQuantity = trade.POSITION_SIZE;
					if (Constants.STATUS_REVERSAL.equals(trade.status))
						order.m_totalQuantity = trade.POSITION_SIZE * 2;

					trade.stop = getLow(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder(trade, order);
				} else
				{
					// has to wait for entry Quotes to get filled
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
					Order order = createMktOrder();
					order.m_action = Constants.ACTION_BUY;
					order.m_totalQuantity = trade.POSITION_SIZE;
					trade.status = Constants.STATUS_STOPPEDOUT;
					logger.info("Position stopped out: " + displayOrder(order));

					placeMktOrder(trade, order);

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
					Order order = createMktOrder();
					order.m_action = Constants.ACTION_SELL;
					order.m_totalQuantity = trade.POSITION_SIZE;
					trade.status = Constants.STATUS_STOPPEDOUT;
					logger.info("Position stopped out: " + displayOrder(order));

					placeMktOrder(trade, order);

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

	public void addTradeEntryData(QuoteData quote)
	{
		trade.addEntryQuotes(quote);
	}

	public void addTradeStopData(QuoteData quote)
	{
		trade.setStopQuotes(quote);
	}


	public void checkStopTriggered(int orderId)
	{
		if ((trade != null) && (trade.stopId == orderId))
		{
			logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " order stopped out");
			trade = null;
		}
	}

	
	public Trade calculateSlimJimReverse( Vector<QuoteData> qts, double[] sma200, double[] sma50,double distance)
	{
		int size = qts.size();
		Object[] quotes = qts.toArray();

		int lastbar = size -1;
		
		if ((((QuoteData)quotes[lastbar]).high < sma50[lastbar]) &&  (((QuoteData)quotes[lastbar]).low > sma200[lastbar]))
		{
			// has to be the first one
			for ( int i = lastbar -1; i > lastbar -11; i-- )
			{
				if (((QuoteData)quotes[i]).high < sma50[i])
					return null;
			}
			
			int pos200SMA = 0; 
			for ( int i = lastbar-1; i > 0; i--)
			{
				if (((QuoteData)quotes[i]).low <= sma200[i] )
				{
					pos200SMA = i;
					break;
				}
			}
			
			if ( pos200SMA == 0 )
				return null;
				
			boolean distanceReached = false;
			for ( int i = pos200SMA; i < lastbar; i++ )
			{
				if ( (((QuoteData)quotes[i]).high - sma200[i]) > distance )
				{
					distanceReached = true;
				}
			}

			if ( distanceReached == true )
			{
				logger.info("Slim Jing Reverse on the downside, sell on " +  m_contract.m_symbol + "." + m_contract.m_currency);
				
				Trade tt = new Trade();
				tt.action = Constants.ACTION_SELL;
				tt.status = Constants.STATUS_OPEN;
				tt.price = ((QuoteData)quotes[lastbar]).close;
				return tt;

			}
			else
				return null;
		}

		if ((((QuoteData)quotes[lastbar]).low > sma50[lastbar]) &&  (((QuoteData)quotes[lastbar]).high < sma200[lastbar]))
		{
			// has to be the first one
			for ( int i = lastbar -1; i > lastbar -11; i-- )
			{
				if (((QuoteData)quotes[i]).low > sma50[i])
					return null;
			}
			
			int pos200SMA = 0; 
			for ( int i = lastbar-1; i > 0; i--)
			{
				if (((QuoteData)quotes[i]).low >= sma200[i] )
				{
					pos200SMA = i;
					break;
				}
			}
			
			if ( pos200SMA == 0 )
				return null;
				
			boolean distanceReached = false;
			for ( int i = pos200SMA; i < lastbar; i++ )
			{
				if (( sma200[i] - ((QuoteData)quotes[i]).low) > distance )
				{
					distanceReached = true;
				}
			}

			if ( distanceReached == true )
			{
				logger.info("Slim Jing Reverse on the upside, buy on " +  m_contract.m_symbol + "." + m_contract.m_currency);
				
				Trade tt = new Trade();
				tt.action = Constants.ACTION_BUY;
				tt.status = Constants.STATUS_OPEN;
				tt.price = ((QuoteData)quotes[lastbar]).close;
				return tt;

			}
			else
				return null;
		}


		
		return null;
		
		
	}

}
