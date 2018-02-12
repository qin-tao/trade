package tao.trading;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.internet.MimeMessage.RecipientType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;


public class EmailSender
{
	//public String SEND_TO;
	public String MAIL_SUBJECT_PREFIX;
	Logger logger = Logger.getLogger("EmailSender");

	private static EmailSender instance = null;

	public static EmailSender getInstance() {
	      if(instance == null) {
	         instance = new EmailSender();
	      }
	      return instance;
	}
	   
	/*private EmailSender()
	{
	    String MailFile = "mail.txt";
	    
	    try{
			BufferedReader br = new BufferedReader( new FileReader(MailFile));
	
			SEND_TO = br.readLine();
			MAIL_SUBJECT_PREFIX = br.readLine();
	
			br.close();
	    }
	    catch( Exception e)
	    {
	    	e.printStackTrace();
	    }
	}*/
	
	public void sendEmail( String to, String subject, String text)
	{
		sendGMail(  to, subject, text);
		//sendYahooMail( to, subject, text);
		
		
	}
	
	
	private void sendGMail(  String send_to, String Subject, String Text)
	{
		if ( send_to == null )
			send_to = "qintao88@gmail.com";
		
		String HOST = "smtp.gmail.com";
		String USER = "qintao88@gmail.com";
		String PASSWORD = "Hmaggie0@";
		String PORT = "465";
		String FROM = "qintao88@gmail.com";
		String TO = "put_to_address_here";
		String STARTTLS = "true";
		String AUTH = "true";
		String DEBUG = "true";
		String SOCKET_FACTORY = "javax.net.ssl.SSLSocketFactory";


		// Use Properties object to set environment properties
		Properties props = new Properties();
		props.put("mail.smtp.host", HOST);
		props.put("mail.smtp.port", PORT);
		props.put("mail.smtp.user", USER);
		props.put("mail.smtp.auth", AUTH);
		props.put("mail.smtp.starttls.enable", STARTTLS);
		props.put("mail.smtp.debug", DEBUG);
		props.put("mail.smtp.socketFactory.port", PORT);
		props.put("mail.smtp.socketFactory.class", SOCKET_FACTORY);
		props.put("mail.smtp.socketFactory.fallback", "false");

		try
		{
			// Obtain the default mail session
			Session session = Session.getDefaultInstance(props, null);
			session.setDebug(true);
			
			// Construct the mail message
			MimeMessage message = new MimeMessage(session);
			message.setText(Text);
			message.setSubject(/*MAIL_SUBJECT_PREFIX +*/ Subject);
			message.setFrom(new InternetAddress(FROM));
			//message.addRecipient(RecipientType.TO, new InternetAddress(TO));
			StringTokenizer st = new StringTokenizer(send_to, ";");
			while(st.hasMoreTokens()) 
			{
				String sendToAddr = st.nextToken();
			    message.addRecipient(Message.RecipientType.TO, new InternetAddress( sendToAddr.trim()));
			}
			message.saveChanges();

			// Use Transport to deliver the message
			Transport transport = session.getTransport("smtp");
			transport.connect(HOST, USER, PASSWORD);
			transport.sendMessage(message, message.getAllRecipients());
			transport.close();

		} 
		catch ( Throwable e ){
			logger.warning(e.getMessage());
			e.printStackTrace();
		}

	}
	
	
	private void sendYahooMail(   String send_to, String Subject, String Text)
	{
		if ( send_to == null )
			send_to = "qintao88@gmail.com";
		
		String host = "smtp.gmail.com"; 
		String from = "user name"; 
		Properties props = System.getProperties(); 
		props.put("mail.smtp.host", host); 
		props.put("mail.smtp.user", from); 
		props.put("mail.smtp.password", "asdfgh"); 
		props.put("mail.smtp.port", "587"); // 587 is the port number of yahoo mail 
		props.put("mail.smtp.auth", "true"); 

		try
		{
			Session session = Session.getDefaultInstance(props, null); 
			MimeMessage message = new MimeMessage(session); 
			message.setFrom(new InternetAddress("tomqin@ymail.com")); 
			
			StringTokenizer st = new StringTokenizer(send_to, ";");
			while(st.hasMoreTokens()) 
			{
				String sendToAddr = st.nextToken();
			    message.addRecipient(Message.RecipientType.TO, new InternetAddress( sendToAddr.trim()));
			}

			message.setText(Text);
			message.setSubject(MAIL_SUBJECT_PREFIX + Subject);
			Transport transport = session.getTransport("smtp"); 
			transport.connect("smtp.mail.yahoo.com", "tao_qin@yahoo.com", "hmaggie0"); 
			//transport.connect("smtp.mail.yahoo.com", "tomqin@ymail.com", "hmaggie0"); 
			transport.sendMessage(message, message.getAllRecipients()); 
			transport.close();
		}
		catch ( Throwable e ){
			logger.warning(e.getMessage());
			e.printStackTrace();
		}
	
	}	

}
