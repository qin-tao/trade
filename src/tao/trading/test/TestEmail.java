package tao.trading.test;

import java.net.InetAddress;

import tao.trading.EmailSender;

public class TestEmail
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		EmailSender es = EmailSender.getInstance();
		es.sendEmail("qintao@hotmail.com","Entry Detection", "2 test email" );

		/*
		try{
			String hostname = InetAddress.getLocalHost().getHostName();
			System.out.println("Hostname:" +hostname);
		}
		catch( Exception e ){
			e.printStackTrace();
		}*/
	}

}
