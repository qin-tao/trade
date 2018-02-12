package tao.trading.strategy;

import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;
import com.ib.client.TradeOrder;
import com.ib.client.TradeOrder2;


public class TradeManager
{
	protected Logger logger;
	protected Trade trade;
	protected EClientSocket m_client;
	protected Contract m_contract;
	protected int m_clientId;
	protected int on_pause = 0;
	protected String orderIdFile = "c:/trade/orderId.txt";
	protected String symbol;
	protected int priceType;

	public TradeManager()
	{
		
	}
	
	public TradeManager(EClientSocket client, Contract contract, int clientId, Logger logger)
	{
		m_contract = new Contract();
		m_contract.m_conId = contract.m_conId + 1000;
		m_contract.m_symbol = contract.m_symbol;
		m_contract.m_secType = contract.m_secType;
		m_contract.m_exchange = contract.m_exchange;  // this is so the order can send to IDEAL instead of IDEAPRO
		m_contract.m_currency = contract.m_currency;

        m_client = client;
		//m_contract = contract;
		m_clientId = clientId;
	    this.logger = logger;
		
	    symbol = m_contract.m_symbol + "." + m_contract.m_currency;
	    // Add to logger

	}

	public TradeManager(EClientSocket client, int clientId, Logger logger )
	{
        m_client = client;
		//m_contract = contract;
		m_clientId = clientId;
	    this.logger = logger;

	}

	
	synchronized public int getOrderId()
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
			FileWriter fstream = new FileWriter(orderIdFile);
			BufferedWriter out = new BufferedWriter(fstream);
			String st = (new Integer(orderId)).toString();
			out.write(st);
			// Close the output stream
			out.close();
		}
		catch (Exception e)
		{// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}

	public int readOrderId()
	{
		BufferedReader in;
		String read = null;
		try
		{
			in = new BufferedReader(new FileReader(orderIdFile));// open a
			read = in.readLine();
			in.close();// safley close the BufferedReader after use
		} 
		catch (IOException e)
		{
			System.out.println("There was a problem:" + e);
		}
		return new Integer(read);
	}

	
	public Order createOrder()
	{
		Order order = new Order();
		order.m_orderId = getOrderId();
		order.m_account = "DU31237";
		order.m_clientId = m_clientId;
		return order;
	}

	public Trade getTrade()
	{
		return trade;
	}
/*
	public void placeTrade(Trade t)
	{
		setTrade(t);
		placeMktOrder();

	}*/

	public void setTrade(Trade t)
	{
		this.trade = t;
	}

	///////////////////////////////////////////////////////////////	
	//
	//   Market Order
	//
	///////////////////////////////////////////////////////////////
	public int placeMktOrder()
	{
		return placeMktOrder(trade.action, trade.POSITION_SIZE);
	}

	public int placeMktOrder( int quantity )
	{
		return placeMktOrder( trade.action, quantity );
	}

	public int placeMktOrder( String action, int quantity )
	{
		Order order = createOrder();
		order.m_action = action;
		order.m_totalQuantity = quantity;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, m_contract, order);
		return order.m_orderId;
	}

	public int placeMktOrder(Order order)
	{
		m_client.placeOrder(order.m_orderId, m_contract, order);
		System.out.println(m_contract.m_symbol + "." + m_contract.m_currency +  " " + order.m_action + " " + order.m_totalQuantity + " " + order.m_orderType + " market order placed");

		/*
		trade.orderId = order.m_orderId;
		trade.executed = true;
		//trade.status = Constants.STATUS_PLACED;
		trade.entryQuotes.clear(); // clear entry Quotes
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " trade:" + trade.toString());
		*/
		return order.m_orderId;

	}
	

	///////////////////////////////////////////////////////////////////////////
	//
	// Limit Order
	//
	//////////////////////////////////////////////////////////////////////////
	public int placeLmtOrder(String action, double limitPrice, String ocaGroup)
	{
		return placeLmtOrder( action, limitPrice, trade.POSITION_SIZE, ocaGroup);
	}

	public int placeLmtOrder(String action, double limitPrice, int posSize, String ocaGroup)
	{
		System.out.println(m_contract.m_symbol + "." + m_contract.m_currency + " to place limit order "  + posSize + " " + limitPrice);

		Order order = createOrder();
		order.m_action = action; 
		order.m_lmtPrice = limitPrice;
		order.m_totalQuantity = posSize;
		order.m_orderType = "LMT";
		if (ocaGroup != null )
		{
			order.m_ocaGroup = ocaGroup;
			order.m_ocaType = 1;  //1 = CANCEL_WITH_BLOCK, 2 = REDUCE_WITH_BLOCK, 3 = REDUCE_NON_BLOCK
		}

		m_client.placeOrder(order.m_orderId, m_contract, order);
		
		return order.m_orderId;

	}


	////////////////////////////////////////////////////
	//
	//  Stop order
	//
	////////////////////////////////////////////////////
	public int placeStopOrder( String action, double stopPrice)
	{
		return placeStopOrder( action, stopPrice, trade.remainingPositionSize, null);
	}

	public int placeStopOrder( String action, double stopPrice, String ocaGroup)
	{
		return placeStopOrder( action, stopPrice, trade.POSITION_SIZE, ocaGroup);
	}
	
	public int placeStopOrder( String action, double stopPrice, int posSize, String ocaGroup)
	{
		System.out.println(m_contract.m_symbol + "." + m_contract.m_currency + " to place stop order "  + posSize + " " + stopPrice);

		Order stopOrder = createOrder();
		stopOrder.m_action = action;
		stopOrder.m_orderType = "STP";
		stopOrder.m_auxPrice = stopPrice;
		stopOrder.m_totalQuantity = posSize;
		//stopOrder.m_outsideRth = true;		// to trigger outside regular trading hour
		if (ocaGroup != null )
		{
			stopOrder.m_ocaGroup = ocaGroup;
			stopOrder.m_ocaType = 1;  //1 = CANCEL_WITH_BLOCK, 2 = REDUCE_WITH_BLOCK, 3 = REDUCE_NON_BLOCK
		}
			
		m_client.placeOrder(stopOrder.m_orderId, m_contract, stopOrder);

		return stopOrder.m_orderId;
	}

	public void placeStopLimitOrder( String action, double stopPrice, double limitPrice, int posSize, String ocaGroup)
	{
		Order stopOrder = createOrder();
		stopOrder.m_action = action;
		stopOrder.m_orderType = "STPLMT";
		stopOrder.m_auxPrice = stopPrice;
		stopOrder.m_lmtPrice = limitPrice;
		stopOrder.m_totalQuantity = posSize;
		//stopOrder.m_outsideRth = true;		// to trigger outside regular trading hour
		if (ocaGroup != null )
		{
			stopOrder.m_ocaGroup = ocaGroup;
			stopOrder.m_ocaType = 1;  //1 = CANCEL_WITH_BLOCK, 2 = REDUCE_WITH_BLOCK, 3 = REDUCE_NON_BLOCK
		}
		
		m_client.placeOrder(stopOrder.m_orderId, m_contract, stopOrder);
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " stop order placed:" + stopOrder.m_action + " " + posSize + " " + stopPrice);

		if (trade != null )
			trade.stopId = stopOrder.m_orderId;
	}

	
	public int placeTrailOrder( String action, int positionSize, double trailamount, String ocaGroup )
	{
		Order trailOrder = createOrder();
		
		trailOrder.m_action = action;
		trailOrder.m_orderType = "TRAIL";//"STP";
		trailOrder.m_auxPrice = trailamount;
		trailOrder.m_totalQuantity = positionSize;
		
		if (ocaGroup != null )
		{
			trailOrder.m_ocaGroup = ocaGroup;
			trailOrder.m_ocaType = 1;  //1 = CANCEL_WITH_BLOCK, 2 = REDUCE_WITH_BLOCK, 3 = REDUCE_NON_BLOCK
		}

		m_client.placeOrder(trailOrder.m_orderId, m_contract, trailOrder);
		logger.info(m_contract.m_symbol + "." + m_contract.m_currency + " trail stop order placed:" + trailOrder.m_action + " " + trade.POSITION_SIZE + " " + trade.stop);

		return trailOrder.m_orderId;

	}

	
	
	
	public void cancelOrder( int orderId)
	{
		m_client.cancelOrder(orderId);
	}
	
	
	

	public void closePosition()
	{
		closePosition(trade.remainingPositionSize);
	}

	
	public void closePosition( int tradesize)
	{
		Order order = createOrder();
		order.m_action = trade.action.equals("BUY") ? "SELL" : "BUY";
		order.m_totalQuantity = tradesize;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, m_contract, order);
		logger.info("close order placed:" + order.m_action + " " + order.m_totalQuantity);

		this.trade.orderId = order.m_orderId;

		writeToTradeLog(this.trade);
		trade.remainingPositionSize -= tradesize;
		
		if ( trade.remainingPositionSize == 0 )
		{
			if ( trade.stopId != 0 )
				cancelOrder(trade.stopId);
			trade = null;
		}
		
	}
	

	// this is for "close all position"
	public void closePosition( Contract contract, String action, int positionSize)
	{
		Order order = createOrder();
		order.m_action = action;
		order.m_totalQuantity = positionSize;
		order.m_orderType = "MKT";

		m_client.placeOrder(order.m_orderId, contract, order);
	}


	public void closePositionByLimit( double limitPrice, int positionSize)
	{
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.closeId = placeLmtOrder( action, limitPrice, null);
		
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

	public String displayOrder(Order order)
	{
		return ("Order:" + order.m_action + " " + order.m_totalQuantity + " " + order.m_orderType);
	}


	public void checkStopTriggered(int orderId)
	{
		if (trade != null ) 
		{
			if (trade.stopId == orderId )
			{
				logger.warning(symbol + " order stopped out," + trade.reEnter + " retry left");
		
				logger.info("reEnter= " + trade.reEnter);
				if ( trade.reEnter > 0 )
				{
					trade.status = Constants.STATUS_STOPPEDOUT;
					trade.stopId = 0;  // so it does not get hit multiple times
					trade.reEnter--;
				}
				else
				{
					logger.warning(symbol + " maximum retry reached, exit trade");
					trade = null;
				}
			}
		}
	}



	protected void beep(){
	     Toolkit.getDefaultToolkit().beep();     
	}
	
	
	protected double adjustPrice( double price, int adjustType )
	{
		//System.out.println("price is " + price + " priceType: " + priceType + " adjustType: " + adjustType);

		String priceStr = new Double(price).toString();
		int strSize = priceStr.length();
		int dotpos = priceStr.indexOf(".");
		String decimalPortion = priceStr.substring(dotpos+1);

		if ( priceType == Constants.PRICE_TYPE_4 )
		{
			if (( decimalPortion.length() == 6 ) && ( "25".equals(priceStr.substring(strSize-2)) || "75".equals(priceStr.substring(strSize-2))))
			{
				// convert 0.000025/0.000075 to 0.00005
				double pricemin = 0.000025;
				if ( adjustType == Constants.ADJUST_TYPE_DOWN )
					price = price - pricemin;
				else if ( adjustType == Constants.ADJUST_TYPE_UP )
					price = price + pricemin;

				BigDecimal bd = new BigDecimal(price);
				bd = bd.setScale(5, BigDecimal.ROUND_HALF_UP);
				return bd.doubleValue();
			
			}
			else if (( decimalPortion.length() == 5 ) && ( "5".equals(priceStr.substring(strSize-1)))) 
			{
				// 0.00005 return as is
				return price;
			}
			else
			{	
				NumberFormat formatter = new DecimalFormat("###.####");
				return new Double(formatter.format(price));
			}

		}
		else if ( priceType == Constants.PRICE_TYPE_3 )
		{
			double pricemin = 0.00005;

			// convert 0.00005 to 0.0001
  		    if (( decimalPortion.length() == 5 ) && ("5".equals(priceStr.substring(strSize-1))))
			{
				if ( adjustType == Constants.ADJUST_TYPE_DOWN )
					price = price - pricemin;
				else if ( adjustType == Constants.ADJUST_TYPE_UP )
					price = price + pricemin;

				BigDecimal bd = new BigDecimal(price);
				bd = bd.setScale(4, BigDecimal.ROUND_HALF_UP);
				return bd.doubleValue();
			}

			// we have invalid price here e.g. 120.203
			NumberFormat formatter = new DecimalFormat("###.####");
			return new Double(formatter.format(price));
		}
		else if ( priceType == Constants.PRICE_TYPE_2 )
		{	
			// convert 0.0025/0.0075 to 0.005/0.01
			if (( decimalPortion.length() == 4 ) && (
			 ( "25".equals(priceStr.substring(strSize-2)) || "75".equals(priceStr.substring(strSize-2)))))
			{
				double pricemin = 0.0025;
				if ( adjustType == Constants.ADJUST_TYPE_DOWN )
					price = price - pricemin;
				else if ( adjustType == Constants.ADJUST_TYPE_UP )
					price = price + pricemin;
				
				BigDecimal bd = new BigDecimal(price);
				bd = bd.setScale(3, BigDecimal.ROUND_HALF_UP);
				return bd.doubleValue();

			}
			if ( decimalPortion.length() == 2 ) 
				return price;
			
			if (( decimalPortion.length() == 3 ) && ("5".equals(priceStr.substring(strSize-1)))) 
				return price;
			
			// we have invalid price here e.g. 120.203
			NumberFormat formatter = new DecimalFormat("###.##");
			return new Double(formatter.format(price));
		}
		else if ( priceType == Constants.PRICE_TYPE_40 )
		{
			NumberFormat formatter = new DecimalFormat("###.####");
			return new Double(formatter.format(price));
		}
		
		return price;
	}
	
	
	double inProfit(QuoteData data)
	{
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			return ( data.close - trade.price ); 
		}
		else if (Constants.ACTION_SELL.equals(trade.action))
		{
			return ( trade.price - data.close ); 
		}
		else
			return 0;
		
	}
	
	public void AdjustStopTargetOrders( String ocaId )
	{
		if ( trade.remainingPositionSize == 0 )
		{
			// cancel the stop order
			if ( this.trade.stopId != 0 )
				m_client.cancelOrder(this.trade.stopId);
			if ( this.trade.targetId != 0 )
				m_client.cancelOrder(this.trade.targetId);
			trade = null;
		}
		else
		{
			m_client.cancelOrder(this.trade.stopId);
			m_client.cancelOrder(this.trade.targetId);
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, ocaId);
				trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize, ocaId);
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, ocaId);
				trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.remainingPositionSize, ocaId);
			}
		}
	}


	public void AdjustStopOrders( String ocaId )
	{
		if ( trade.remainingPositionSize == 0 )
		{
			// cancel the stop order
			if ( this.trade.stopId != 0 )
				m_client.cancelOrder(this.trade.stopId);
			trade = null;
		}
		else
		{
			m_client.cancelOrder(this.trade.stopId);
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, ocaId);
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, ocaId);
			}
		}
	}
	
}
