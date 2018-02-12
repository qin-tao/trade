package tao.trading.utility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.*;
import java.util.StringTokenizer;

public class DBUtil
{
	// String driver = "org.apache.derby.jdbc.EmbeddedDriver";
	String driver = "org.apache.derby.jdbc.ClientDriver";
	// String connectionURL = "jdbc:derby:QuoteDB;create=true";

	protected Connection conn = null;

	
	public DBUtil( String connectionURL)
	{
		try
		{
			Class.forName(driver);
			conn = DriverManager.getConnection(connectionURL);

			//conn.close();
			System.out.println("Connection connected");

		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}
	
	
	 public void finalize ()  {
		 
		 try
		 {
		 conn.close();
		 }
		 catch ( Exception e)
		 {}
	 }
}
