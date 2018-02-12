package tao.trading;

import java.io.Serializable;

public class TradePosition implements Serializable
{
	static final long serialVersionUID = 4240866629497253291L; 
	
	public double price;
	public int position_size;
	public int orderId;
	public boolean filled;
	public int targetId;
	public String status;
	public String position_time;
	
	public TradePosition(double price, int position_size, String position_time)
	{
		super();
		this.price = price;
		this.position_size = position_size;
		this.position_time = position_time;
	}

	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + position_size;
		long temp;
		temp = Double.doubleToLongBits(price);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TradePosition other = (TradePosition) obj;
		if (position_size != other.position_size)
			return false;
		if (Double.doubleToLongBits(price) != Double.doubleToLongBits(other.price))
			return false;
		return true;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	
}
