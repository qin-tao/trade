package tao.trading.strategy;

import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;

public class DlgTradeManager extends TradeManager
{
	public DlgTradeManager(EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super( client, contract, symbolIndex, logger );
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

	
	
	/*
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
	
	/*
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
		
*/
	
	
	
	
	public void addTradeTrackData(QuoteData quote)
	{
		trade.trackQuotes.add(quote);
	}

	
	public void addTradeEntryData(QuoteData quote)
	{
		trade.entryQuotes.add(quote);

		/*
		if (trade.entryQuotes.size() < 1 )
		{
			trade.entryQuotes.add(quote);
			return;
		}
		
		QuoteData lastQuote = (QuoteData)trade.entryQuotes.lastElement();
		
		// if it does not make new high/low place the order
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			logger.info("trade action is sell");
			logger.info("lastQuotes:high=" + lastQuote.high);
			logger.info("lastQuotes:low=" + lastQuote.low);
			logger.info("thisQuotes:high=" + quote.high);
			logger.info("thisQuotes:low=" + quote.low);
			
			if (quote.high <= lastQuote.high)
			{
				logger.info("no longer making new high, sell - ");
				// place order
				Order order = createMktOrder();
				order.m_action = trade.action;
				order.m_totalQuantity = trade.positionSize;
				
				// find stop
				trade.stop = getHigh(trade.entryQuotes);

				placeMktOrder(trade, order);
			}
			else
			{
				logger.info("still making new highs, wait");
				trade.addEntryQuotes(quote);
			}

		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			logger.info("trade action is buy");
			if (quote.low >= lastQuote.low)
			{
				logger.info("no longer making new lows, buy - ");
				// place order
				Order order = createMktOrder();
				order.m_action = trade.action;
				order.m_totalQuantity = trade.positionSize;

				// find stop
				trade.stop = getLow(trade.entryQuotes);
				
				placeMktOrder(trade, order);
			}
			else
			{
				logger.info("still making new lows, wait");
				trade.addEntryQuotes(quote);
			}

		}*/
	}


	public void trackTradeExit(QuoteData data, Vector<QuoteData> quotes, double[] sma200 )
	{
		int lastbar = sma200.length -1;

		if (Constants.ACTION_SELL.equals(trade.action))		
		{
			// first check stop
			if ( data.high > trade.stop )
			{
				trade.reEnter--;
				if ( trade.reEnter < 0 )
				{
					logger.info("Maxium retry hit, exit trade"); 
					closePosition( );
				}
				else
				{	
					logger.info("Stop hit, exit, reEnter="+ trade.reEnter); 
					placeMktOrder();
					trade.status = Constants.STATUS_STOPPEDOUT;	// to override "placed"
					trade.trackQuotes.clear();
				}

			}
			else if ( data.low < sma200[lastbar]) // default target is 200MA 
			{
				logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
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
				}
				else
				{
					logger.info("Stop hit, exit, reEnter="+ trade.reEnter); 
						
					placeMktOrder();
					trade.status = Constants.STATUS_STOPPEDOUT;
					trade.trackQuotes.clear();
				}

			} 
			else if ( data.high > sma200[lastbar]) // default target is 200MA 
			{
				logger.info("Trade " + trade.orderId + " default 200MA target hit, exist");
				closePosition();
			}
		}
		else if (Constants.ACTION_REVERSAL.equals(trade.action))		
		{
			QuoteData lastQuote = (QuoteData) quotes.lastElement();
			if ( trade.position.equals(Constants.POSITION_LONG))
			{
				if (data.low < lastQuote.low)
				{
					// it is consider a reversal
					// 1. first detect how far is it
					
					double avgBar = getAverage(quotes);
					if (( data.close - trade.stop ) > 3*avgBar)
					{
						logger.info("data close > 3 times average bar, change trade type to buy");
						trade.action = Constants.ACTION_BUY;
						trade.reEnter = 1;
					}
					else
					{
						trade.reEnter--;
						if ( trade.reEnter <= 0 )
						{
							logger.info("maxinum retry reached, close trade"); 
							closePosition( );
						}
						else
						{
							placeMktOrder();
							
							trade.stop = getHigh(trade.trackQuotes);
							trade.trackQuotes.clear();
						}
					}
				} 
			}
			else if ( trade.position.equals(Constants.POSITION_SHORT))
			{
				if ( data.high > lastQuote.high )
				{
					// it is consider a reversal
					// 1. first detect how far is it
					
					double avgBar = getAverage(quotes);
					if (( trade.stop - data.close ) > 3*avgBar)
					{
						logger.info("data close > 3 times average bar, change trade type to buy");
						trade.action = Constants.ACTION_BUY;
						trade.reEnter = 1;
					}
					else
					{
						trade.reEnter--;
						if ( trade.reEnter <= 0 )
						{
							logger.info("maxinum retry reached, close trade"); 
							closePosition( );
						}
						else
						{
							placeMktOrder();
						
							trade.stop = getLow(trade.trackQuotes);
							trade.trackQuotes.clear();
						}
					}
				}
			}
		}
	}
	
	
	public void trackTradeEntry(QuoteData quote)
	{
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((trade.entryQuotes != null) && ( trade.entryQuotes.size() != 0 ))
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.low < lastQuote.low)
				{
					logger.info("quote low " + quote.low + " < " + lastQuote.low);
					
					trade.stop = getHigh(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder();
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
			if ((trade.entryQuotes != null) && ( trade.entryQuotes.size() != 0 ))
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.high > lastQuote.high)
				{
					trade.stop = getLow(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder();
					trade.position = Constants.POSITION_LONG;
					trade.trackQuotes.clear();
				}
				else
				{
					// has to wait for entry Quotes to get filled
				}
			}
		}
		else if (trade.action.equals(Constants.ACTION_REVERSAL))
		{
			if ((trade.entryQuotes != null) && ( trade.entryQuotes.size() != 0 ))
			{
				QuoteData lastQuote = (QuoteData) trade.entryQuotes.lastElement();
				if (quote.high > lastQuote.high)
				{
					trade.stop = getLow(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder();
					trade.position = Constants.POSITION_LONG;
				}
				else if (quote.low < lastQuote.low)
				{
					logger.info("quote low " + quote.low + " < " + lastQuote.low);
				
					trade.stop = getHigh(trade.entryQuotes);
					trade.status = Constants.STATUS_PLACED;
					placeMktOrder();
					trade.position = Constants.POSITION_SHORT;
				}
			}
		}
	}


	// this is to override the default one, without place the stop order
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
