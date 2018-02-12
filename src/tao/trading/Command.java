package tao.trading;

import java.io.Serializable;

public class Command implements Serializable
{
	public CommandAction cmd;
	public String symbol;
	public String action;
	public double quantity;  // in lot size
	public double price;
	public double quantity2;  // in lot size
	public double price2;

	// IncrementalEntry
	public String entryMode = null;
	public String incrementalEntryStartTime;
	
	public Command(CommandAction cmd, String symbol, String action, double quantity, double price, double quantity2, double price2)
	{
		super();
		this.cmd = cmd;
		this.symbol = symbol;
		this.action = action;
		this.quantity = quantity;
		this.price = price;
		this.quantity2 = quantity2;
		this.price2 = price2;
	}

	public double getQuantity()
	{
		return quantity;
	}

	public void setQuantity(int quantity)
	{
		this.quantity = quantity;
	}

	public void setCmd(CommandAction cmd)
	{
		this.cmd = cmd;
	}

	public void setSymbol(String symbol)
	{
		this.symbol = symbol;
	}

	public void setAction(String action)
	{
		this.action = action;
	}

	public void setPrice(double price)
	{
		this.price = price;
	}

	public CommandAction getCmd()
	{
		return cmd;
	}

	public String getSymbol()
	{
		return symbol;
	}

	public String getAction()
	{
		return action;
	}

	public Double getPrice()
	{
		return price;
	}

	public double getQuantity2()
	{
		return quantity2;
	}

	public void setQuantity2(double quantity2)
	{
		this.quantity2 = quantity2;
	}

	public double getPrice2()
	{
		return price2;
	}

	public void setPrice2(double price2)
	{
		this.price2 = price2;
	}

	public void setQuantity(double quantity)
	{
		this.quantity = quantity;
	}

	
	
	@Override
	public String toString()
	{
		return "Command [cmd=" + cmd + ", symbol=" + symbol + ", action=" + action + ", quantity=" + quantity + ", price=" + price + ", quantity2="
				+ quantity2 + ", price2=" + price2 + "]";
	}

	
	
	
}
