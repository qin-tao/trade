package tao.trading.strategy;

import java.net.InetAddress;

public class Ping
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		// TODO Auto-generated method stub
		try
		{
			InetAddress address = InetAddress.getByName("173.193.223.92");
			boolean reachable = address.isReachable(10000);
			System.out.println("Is host reachable? " + reachable);
		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}

}
