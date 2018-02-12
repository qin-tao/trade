package tao.trading.strategy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import tao.trading.EmailSender;

public class StartEmail
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		EmailSender es = EmailSender.getInstance();
		es.sendYahooMail("Trading Started", "send from automatic trading system");
	}

	//es.sendGMail("Trading Started", "send from automatic trading system");


}
