package tao.trading.strategy;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import tao.trading.EmailSender;

public class PingServer
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");

		while (true)
		{

			try
			{
				InetAddress address = InetAddress.getByName("173.193.223.92");

				// Try to reach the specified address within the timeout
				// periode. If during this periode the address cannot be
				// reach then the method returns false.
				boolean reachable = address.isReachable(10000);

				if (!reachable)
				{
					System.out.println(IBDataFormatter.format( new Date()) + " 173.193.223.92 NOT DETECTED");
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail("Trading Server 173.193.223.92 Not Responding", "send from trading monitor");
				}
				else
				{
					System.out.println(IBDataFormatter.format( new Date()) + " 173.193.223.92 detected");
				}
				
				Thread.sleep(10 * 60 * 1000);  // 10  minutes interval

				//System.out.println("Is host reachable? " + reachable);
				
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}
