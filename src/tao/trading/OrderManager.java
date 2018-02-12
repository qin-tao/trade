package tao.trading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;

public class OrderManager
{
	Vector trades = new Vector();
	EClientSocket m_client;
	Contract m_contract;
	Logger logger = Logger.getLogger("OrderManager");
	int m_clientId;
	int pause_topbottom = 0;

	public OrderManager(EClientSocket client, Contract contract, int clientId)
	{
		// no code req'd
		m_client = client;
		m_contract = contract;
		m_clientId = clientId;
	}

	public Order createLMTOrder(String action, int quality, String orderType, double lmtPrice)
	{
		Order order = new Order();

		order.m_account = "DU31237";
		order.m_orderId = getOrderId();
		order.m_clientId = m_clientId;
		order.m_action = action; // "BUY";
		order.m_totalQuantity = quality; // 10;
		order.m_orderType = orderType; // "MKT";
		order.m_lmtPrice = lmtPrice;

		//orders.add(order);

		// main order fields
		/*
		 * public int m_orderId; public int m_clientId; public int m_permId;
		 * public String m_action; public int m_totalQuantity; public String
		 * m_orderType; "MKT" = Market Order
		 * 
		 * "MKTCLS" = Market On Close Order
		 * 
		 * "LMT" = Limit Order
		 * 
		 * "LMTCLS" = Limit On Close
		 * 
		 * "PEGMKT" = Pegged to Buy on Best Offer/Sell on Best Bid
		 * 
		 * "STP" = Stop Order
		 * 
		 * "STPLMT" = Stop Limit Order
		 * 
		 * "TRAIL" = Trailing Order
		 * 
		 * "REL" = Relative Order
		 * 
		 * "VWAP" = Volume-Weighted Avg Price Order
		 * 
		 * 
		 * public double m_lmtPrice; public double m_auxPrice;
		 * 
		 * // extended order fields public String m_tif; // "Time in Force" -
		 * DAY, GTC, etc. public String m_ocaGroup; // one cancels all group
		 * name public int m_ocaType; // 1 = CANCEL_WITH_BLOCK, 2 =
		 * REDUCE_WITH_BLOCK, 3 = REDUCE_NON_BLOCK public String m_orderRef;
		 * public boolean m_transmit; // if false, order will be created but not
		 * transmited public int m_parentId; // Parent order Id, to associate
		 * Auto STP or TRAIL orders with the original order. public boolean
		 * m_blockOrder; public boolean m_sweepToFill; public int m_displaySize;
		 * public int m_triggerMethod; // 0=Default, 1=Double_Bid_Ask, 2=Last,
		 * 3=Double_Last, 4=Bid_Ask, 7=Last_or_Bid_Ask, 8=Mid-point public
		 * boolean m_outsideRth; public boolean m_hidden; public String
		 * m_goodAfterTime; // FORMAT: 20060505 08:00:00 {time zone} public
		 * String m_goodTillDate; // FORMAT: 20060505 08:00:00 {time zone}
		 * public boolean m_overridePercentageConstraints; public String
		 * m_rule80A; // Individual = 'I', Agency = 'A', AgentOtherMember = 'W',
		 * IndividualPTIA = 'J', AgencyPTIA = 'U', AgentOtherMemberPTIA = 'M',
		 * IndividualPT = 'K', AgencyPT = 'Y', AgentOtherMemberPT = 'N' public
		 * boolean m_allOrNone; public int m_minQty; public double
		 * m_percentOffset; // REL orders only public double m_trailStopPrice;
		 * // for TRAILLIMIT orders only
		 */
		return order;
	}

	public void placeMktOrder(Trade trade)
	{
		Order order = new Order();

		order.m_account = "DU31237";
		order.m_orderId = getOrderId();
		order.m_clientId = m_clientId;
		order.m_action = trade.action;
		order.m_totalQuantity = trade.POSITION_SIZE;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info("Market order placed:" + trade.action + " " + trade.POSITION_SIZE);

		trade.orderId = order.m_orderId;
		trade.executed = true;
		trade.status = Constants.STATUS_PLACED;
		trade.entryQuotes = null;   // clear entry Quotes
		
		if ( trade.stop != 0 )
		{
			// place a stop order
			order.m_orderId = order.m_orderId + 10000;
			if (Constants.ACTION_BUY.equals(order.m_action))
				order.m_action = Constants.ACTION_SELL;
			else
				order.m_action = Constants.ACTION_BUY;
			order.m_orderType = "STP";
			//order.m_stopPrice = trade.stop;

			m_client.placeOrder(order.m_orderId, m_contract, order);
			logger.info("Stop order placed:" + trade.action + " " + trade.POSITION_SIZE + " " + trade.stop);
		}

		trade.stopId = order.m_orderId;
		
		writeToTradeLog(trade);		
		
	}

	
	public void closeTrade(Trade trade)
	{
		Order order = new Order();

		order.m_account = "DU31237";
		order.m_orderId = getOrderId();
		order.m_clientId = m_clientId;
		order.m_action = trade.action.equals("BUY")?"SELL":"BUY";
		order.m_totalQuantity = trade.POSITION_SIZE;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info("close order placed:" + trade.action + " " + trade.POSITION_SIZE);
		
		trade.orderId = order.m_orderId;
		trade.status = Constants.STATUS_CLOSED;

		// cancel the stop order
		m_client.cancelOrder(order.m_orderId + 10000);
		logger.info("stop order canbcelled:" + order.m_orderId + 10000);
		
		writeToTradeLog(trade);		
		
	}

	public void removeTrade( int tradeId )
	{
		int size = trades.size();
		
		for ( int i = 0; i < size; i++ )
		{
			if (((Trade)trades.elementAt(i)).id == tradeId )
			{
				trades.remove(i);
				return;
			}
		}
	}


/*	
	public void rePlaceMktOrder(Trade trade)
	{
		Order order = new Order();

		order.m_account = "DU31237";
		order.m_orderId = getOrderId();
		order.m_clientId = m_clientId;
		order.m_action = trade.action;
		order.m_totalQuantity = trade.positionSize;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info("Market order placed:" + trade.action + " " + trade.positionSize);
		
		trade.orderId = order.m_orderId;
		trade.executed = true;
		trade.reEnterQuotes = null;
		trade.status = Constants.STATUS_PLACED;
		
	}
*/
	
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

	
	public int getOrderId()
	{
		int orderId = readOrderId();
		writeOrderId(++orderId);
		return orderId;
	}
	
	
	public void writeOrderId(int orderId)
	{
		try
		{
			// Create file
			FileWriter fstream = new FileWriter("c:/orderId.txt");
			BufferedWriter out = new BufferedWriter(fstream);
			String st = (new Integer(orderId)).toString();
			out.write(st);
			// Close the output stream
			out.close();
		} catch (Exception e)
		{// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}

	public int readOrderId()
	{
		BufferedReader in;
		String read=null;
		try
		{

			in = new BufferedReader(new FileReader("c:/orderId.txt"));// open a
																	// bufferedReader
																	// to file
																	// hellowrold.txt
			read = in.readLine();// read a line from helloworld.txt and save
									// into a string
			in.close();// safley close the BufferedReader after use
		} catch (IOException e)
		{
			System.out.println("There was a problem:" + e);

		}
		return new Integer(read);
	}

	
	public void addTrade(Trade trade) throws Exception
	{
		if (findTradeByType( trade.trigger ) != null )
		{
			logger.info("Add trade: same trade type alread exist");
			throw new Exception("Add trade: same trade type alread exist");
		}
		else{
			trades.add(trade);
		}
	}
	
	
	synchronized public void orderFilled( int orderId,  int filled, double avgFillPrice)
	{
		Iterator iterator = trades.iterator();

		while( iterator. hasNext() )
		{
			Trade trade = (Trade)( iterator.next() );
			if (trade.orderId == orderId )
			{
				trade.filled += filled;
				//trade.avgFillPrice = avgFillPrice;
			}
			else if ( trade.stopId == orderId)
			{
				trade.status = Constants.STATUS_STOPPEDOUT;
				trade.entryQuotes = null;
				trade.reEnter = trade.reEnter -1;
				if ( trade.reEnter < 1 )
					removeTrade(trade.id);
			}
		}

	}
	

	public int hasPosition(int trigger) 
	{
		Trade trade;
		
		if ((trade = findTradeByType( trigger )) != null )
			return trade.POSITION_SIZE;
		
		return 0;
	}
		
	public boolean isStoppedOut(int trigger) 
	{
		Trade trade = findTradeByType( trigger );
		
		if (( trade != null ) && (trade.status.equals( Constants.STATUS_STOPPEDOUT)))
			return true;
		else
			return false;
	}

	
	public Trade findTradeByType( int trigger )
	{
		Iterator iterator = trades.iterator();

		while( iterator. hasNext() )
		{
			Trade trade = (Trade)( iterator.next() );
			if (trade.trigger == trigger )
				return trade;
		}
		return null;

	}
	
	
	public void trackTopButtom(Vector<QuoteData> quotes, double[] last_sma200 )
	{
		int size = last_sma200.length;
		double close = quotes.lastElement().close;
		double low = quotes.lastElement().low;
		double high = quotes.lastElement().high;
		
		Iterator iterator = trades.iterator();

		while( iterator.hasNext() )
		{
			Trade trade = (Trade)( iterator.next() );
		
			if ( Constants.TRIGGER_TOPBUTTOM == trade.trigger )
			{
				if (Constants.ACTION_SELL.equals(trade.action))		
				{
					// First check if price reached, if it is close position
					if ( trade.targetPrice != 0 )  
					{
						if (low < trade.targetPrice)
						{
							logger.info("Trade " + trade.orderId + " target hit, exist");
							closeTrade( trade );
							removeTrade(trade.id);
							continue;
						}
					}
					else
					{
						// default target is 200MA
						double ma_200 = last_sma200[size-1];
						if ( low < ma_200) 
						{
							logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
							closeTrade( trade );
							removeTrade(trade.id);
							continue;
						}
					}
					
					// Second check if this is getting stopped out
					// it is whether it is closed above the stop price
					if ( close > trade.stop)
					{
						logger.info("Trade " + trade.orderId + " stop hit, exist");
						closeTrade( trade );
						if ( trade.getReEnter() > 0 )
						{
							trade.setStatus(Constants.STATUS_STOPPEDOUT);
						}
						else
						{
							removeTrade(trade.id);
							setPause(Constants.TRIGGER_TOPBUTTOM,60);
						}
						continue;
					}
				}
				else if (Constants.ACTION_BUY.equals(trade.action))		
				{
					// First check if price reached, if it is close position
					if ( trade.targetPrice != 0 )  
					{
						if (high > trade.targetPrice)
						{
							logger.info("Trade " + trade.orderId + " target hit, exist");
							closeTrade( trade );
							removeTrade(trade.id);
							continue;
						}
					}
					else
					{
						// default target is 200MA
						double ma_200 = last_sma200[size-1];
						if ( high > ma_200) 
						{
							logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
							closeTrade( trade );
							removeTrade(trade.id);
							continue;
						}
					}
					
					// Second check if this is getting stopped out
					// it is whether it is closed above the stop price
					if ( close < trade.stop)
					{
						logger.info("Trade " + trade.orderId + " stop hit, exist");
						closeTrade( trade );
						if ( trade.getReEnter() > 0 )
						{
							trade.setStatus(Constants.STATUS_STOPPEDOUT);
						}
						else
						{
							removeTrade(trade.id);
							setPause(Constants.TRIGGER_TOPBUTTOM,60);
						}
						continue;
					}
				}
			}
		}
	}
	
	
	public void trackCrossOver(Vector<QuoteData> quotes, double[] smaS, double[] smaL)
	{
		// TODO: todo:;
	}

	public void trackStretchTrade(Trade trade, Vector<QuoteData> quotes, double[] sma200 )
	{
		int size = sma200.length;
		double close = quotes.lastElement().close;
		double low = quotes.lastElement().low;
		double high = quotes.lastElement().high;

		if (Constants.ACTION_SELL.equals(trade.action))		
		{
			// default target is 200MA
			double ma_200 = sma200[size-1];
			if ( low < ma_200) 
			{
				logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
				closeTrade( trade );
				removeTrade(trade.id);
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))		
		{
			// default target is 200MA
			double ma_200 = sma200[size-1];
			if ( high > ma_200) 
			{
				logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
				closeTrade( trade );
				removeTrade(trade.id);
			}
		}
	}
	
	
	public void addTopButtomReEnterData(int trigger, QuoteData quote)
	{
		Trade trade = findTradeByType( trigger );
		
		if (trigger == Constants.TRIGGER_TOPBUTTOM )
		{
			if ( trade.action.equals( Constants.ACTION_SELL))
			{
				if ( trade.entryQuotes != null ) 
				{
					QuoteData lastQuote = (QuoteData)trade.entryQuotes.lastElement();
					if ( quote.low < lastQuote.low)
					{
						trade.stop = getHigh(trade.entryQuotes);
						placeMktOrder(trade);
					}
					else
					{
						trade.addEntryQuotes(quote);
					}
				}
				else
					trade.addEntryQuotes(quote);
			}
			else if ( trade.action.equals( Constants.ACTION_BUY))
			{
				if ( trade.entryQuotes != null ) 
				{
					QuoteData lastQuote = (QuoteData)trade.entryQuotes.lastElement();
					if (quote.high > lastQuote.high)
					{
						trade.stop = getLow(trade.entryQuotes);
						placeMktOrder(trade);
					}
					else
					{
						trade.addEntryQuotes(quote);
					}
				}
				else
					trade.addEntryQuotes(quote);
			}
		}
		
	}
	

	public void addStretchEntryData(Trade trade, QuoteData quote)
	{
		if ( trade.action.equals( Constants.ACTION_SELL))
		{
			if ( trade.entryQuotes != null ) 
			{
				QuoteData lastQuote = (QuoteData)trade.entryQuotes.lastElement();
				if ( quote.low < lastQuote.low)
				{
					trade.stop = getHigh(trade.entryQuotes);
					placeMktOrder(trade);
				}
				else
				{
					trade.addEntryQuotes(quote);
				}
			}
			else
				trade.addEntryQuotes(quote);
		}
		else if ( trade.action.equals( Constants.ACTION_BUY))
		{
			if ( trade.entryQuotes != null ) 
			{
				QuoteData lastQuote = (QuoteData)trade.entryQuotes.lastElement();
				if (quote.high > lastQuote.high)
				{
					trade.stop = getLow(trade.entryQuotes);
					placeMktOrder(trade);
				}
				else
				{
					trade.addEntryQuotes(quote);
				}
			}
			else
				trade.addEntryQuotes(quote);
		}
	}
		

	
	
	
	
	private void setPause( int trigger, int timeout)
	{
		if (trigger == Constants.TRIGGER_TOPBUTTOM)
			pause_topbottom = timeout;
	}

	public int getPause( int trigger)
	{
		if (trigger == Constants.TRIGGER_TOPBUTTOM)
			return --pause_topbottom;
		
		return 0;
	}


	private double getHigh(Vector<QuoteData> quotes)
	{
		Iterator iterator = quotes.iterator();

		double high = 0;
		while( iterator. hasNext() )
		{
			QuoteData quote = (QuoteData)( iterator.next() );
			if ( quote.high > high )
				high = quote.high;
		}
		return high;
	}

	private double getLow(Vector<QuoteData> quotes)
	{
		Iterator iterator = quotes.iterator();

		double low = 999;
		while( iterator. hasNext() )
		{
			QuoteData quote = (QuoteData)( iterator.next() );
			if ( quote.low < low )
				low = quote.low;
		}
		return low;
	}
	
	public void writeToTradeLog(Trade tt)
	{
		try {
	        BufferedWriter out = new BufferedWriter(new FileWriter("c:\trade.log", true));
	        out.write(tt.toString() + "\n");
	        out.close();
	    } catch (IOException e) {
	    }

	}


}
