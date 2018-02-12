package tao.trading;

import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Order;

public class Request
{
	public Contract contract;
    public Order order;
    public EClientSocket client;
    public String requestType;
    public int position;
    public Vector trackingQuotes = new Vector();
    private int orderId;
    private int stopOrderId;
    public boolean triggered=false;
    int retry = 0;
	private static Logger logger = Logger.getLogger("Request");
    
    public void submitOrder( EClientSocket client, int orderId)
    {
    	this.orderId = orderId;
		client.placeOrder(orderId, contract, order);

    }

    public void submitStopOrder( EClientSocket client, int orderId)
    {
    	this.stopOrderId = orderId;
		client.placeOrder(orderId, contract, order);

    }
    
    void addPositionFilled( int orderId, int numFilled )
    {
    	if (orderId == this.orderId )
    	{
    		position += numFilled;
    		
    		// setup
    	}
    	else if ( orderId == stopOrderId )
    	{
    		// stop trigger, remove 
    	}
    		
    }
    
    public void orderFilled( int orderId, int numFilled)
    {
		
    	if ( orderId == this.orderId )
		{
    		position += numFilled;

    		stopOrderId = orderId + 10000;
			// place an stop loss order 
			// stop should be the high/low of all tracking quotes

    		if (requestType.equals( Constants.REQUEST_TYPE_SELL_TOP))
    		{
    			double price = 0.0;
    			Iterator itr = trackingQuotes.iterator();

    			while(itr.hasNext())
    			{
    				QuoteData quote = (QuoteData)itr.next();
    				if ( quote.high > price )
    					price = quote.high;
    			}
    			stopOrderId = orderId + 10000;
    			client.placeOrder(stopOrderId, contract, order);
    		} 
    		else if (requestType.equals( Constants.REQUEST_TYPE_BUY_BUTTOM))
    		{
    			double price = 0.0;
    			Iterator itr = trackingQuotes.iterator();

    			while(itr.hasNext())
    			{
    				QuoteData quote = (QuoteData)itr.next();
    				if ( quote.high > price )
    					price = quote.high;
    			}
    			stopOrderId = orderId + 10000;
    			client.placeOrder(stopOrderId, contract, order);
    		} 
		}
    	// TODO:  else if the orderid = stop order id

    }
    
}
