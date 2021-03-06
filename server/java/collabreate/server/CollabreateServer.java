/*
    collabREate CollabreateServer
    Copyright (C) 2008 Chris Eagle <cseagle at gmail d0t com>
    Copyright (C) 2008 Tim Vidas <tvidas at gmail d0t com>

    This program is free software; you can redistribute it and/or modify it
    under the terms of the GNU General Public License as published by the Free
    Software Foundation; either version 2 of the License, or (at your option)
    any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
    more details.

    You should have received a copy of the GNU General Public License along with
    this program; if not, write to the Free Software Foundation, Inc., 59 Temple
    Place, Suite 330, Boston, MA 02111-1307 USA
*/

package collabreate.server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import com.google.gson.*;


/**
 * CollabreateServer
 * This class is responsible for accepting new incoming client
 * nections and passing them along to the ConnectionManager
 * @author Tim Vidas
 * @author Chris Eagle
 * @version 0.2.0, January 2017
 */
public class CollabreateServer extends Thread implements CollabreateConstants {

   /**
    * the tcp port to default to if no config is specified
    */
   public static final int DEFAULT_PORT = 5042;

   private JsonObject config = new JsonObject();

   private ServerSocket ss;
   private ConnectionManagerBase cm;
   private ManagerHelper mh;

   private PrintStream logStream = System.err;

   private int verbosityLevel = DEFAULT_VERBOSITY;

   /**
    * CollabreateServer Construct a server object that pulls options from a config file 
    * @param configFile A configuration file to read for options 
    */
   protected CollabreateServer(String configFile) throws Exception {
      try {
         JsonParser p = new JsonParser();
         FileReader fr = new FileReader(configFile);
         config = (JsonObject)p.parse(fr);
         fr.close();
      } catch (Exception ex) {
         System.err.println("Failed to load config file: " + configFile);
         throw ex;
      }
      initCommon();
   }

   /**
    * CollabreateServer Construct a server object that uses default settings
    */
   protected CollabreateServer() throws Exception {
      initCommon();
   }

   /**
    * logs a message to the configured log file (server.conf)
    * @param msg the string to log
    */
   protected void log(String msg) {
      log(msg, 0);
   }

   /**
    * logs a message to the configured log file (server.conf)
    * @param msg the string to log
    * @param verbosity apply a verbosity level to the msg
    */
   protected void log(String msg, int verbosity) {
      if (verbosity < verbosityLevel) {
         logStream.print(msg);
      }
   }

   /**
    * logs a message to the configured log file (server.conf) (with newline)
    * @param msg the string to log
    */
   protected void logln(String msg) {
      logln(msg, 0);
   }

   /**
    * logs a message to the configured log file (server.conf) (with newline)
    * @param msg the string to log
    * @param verbosity apply a verbosity level to the msg
    */
   protected void logln(String msg, int verbosity) {
      if (verbosity < verbosityLevel) {
         logStream.println(msg);
      }
   }

   /**
    * logs an exception to the configured log file (server.conf) (with newline)
    * @param ex the exception to log
    */
   protected void logex(Exception ex) {
      logex(ex, 0);
   }

   /**
    * logs an exception to the configured log file (server.conf) (with newline)
    * @param ex the exception to log
    * @param verbosity apply a verbosity level to the exception 
    */
   protected void logex(Exception ex, int verbosity) {
      if (verbosity < verbosityLevel) {
         ex.printStackTrace(logStream);
      }
   }

   private String getConfigString(String key, String default_value) {
      if (config.has(key)) {
         return config.getAsJsonPrimitive(key).getAsString();
      }
      return default_value;
   }

   private int getConfigInt(String key, int default_value) {
      if (config.has(key)) {
         return config.getAsJsonPrimitive(key).getAsInt();
      }
      return default_value;
   }

   /**
    * getJDBCConnection sets up and returns a JDBC connection 
    * @return a JDBC connection
    */
   private Connection getJDBCConnection() {
      Connection con = null;
      String driver = getConfigString("JDBC_DRIVER", "org.postgresql.Driver");
      try {
         Class.forName(driver);
      } catch(java.lang.ClassNotFoundException e) {
         logln("ClassNotFoundException: " + e.getMessage(), LERROR);
         logln("you need the jdbc jar for " + driver + " in your classpath!\n", LERROR);
         logln("Current classpath is: ", LERROR);
         logln(System.getProperty("java.class.path"), LERROR);
         logex(e);
         return null;
      }

      try {
         String userid = getConfigString("DB_USER", "collabreate");
         String password = getConfigString("DB_PASS", null);
         if (password == null) {
            //need to prompt for the password
         }
         String url = getConfigString("JDBC_URL", null);
         if (url == null) {
            String dbname = getConfigString("DB_NAME", "collabreate");
            String host = getConfigString("DB_HOST", "127.0.0.1");
            String ssl = getConfigString("USE_SSL", "no");
            String dbtype = getConfigString("JDBC_NAME", "postgresql");
            url = "jdbc:" + dbtype + "://" + host + "/" + dbname;
            if (ssl.equalsIgnoreCase("yes")) {
               url += "?ssl";
            }
         }
         con = DriverManager.getConnection(url, userid, password);
      } catch(SQLException ex) {
         logln("SQLException: " + ex.getMessage(), LERROR);
         logln("check permissions in your database configuration file\n", LERROR);
         return null;
      }
      
      try {
         DatabaseMetaData meta = con.getMetaData();
         logln("Connected to " + meta.getURL(), LINFO);
         log("DB Driver : " + meta.getDriverName(), LINFO3);
         logln(" v: " + meta.getDriverVersion(), LINFO3);
         logln("Database: " + meta.getDatabaseProductName() + " "
                              + meta.getDatabaseMajorVersion() + "." + meta.getDatabaseMinorVersion(), LINFO3);
         logln("JDBC v: " + meta.getJDBCMajorVersion() + "." + meta.getJDBCMinorVersion(), LINFO3);
      } catch(Exception ex1) {
         logln("Couldn't get driver metadata: " + ex1.getMessage());
         //Is this a fatal error, do you want to close con here?
      }
      return con;
   }

   private void initCommon() throws Exception {
      int port = getConfigInt("SERVER_PORT", DEFAULT_PORT);
      ss = new ServerSocket(port);

      String logFile = getConfigString("LOG_FILE", null);
      if (logFile != null) {
         try {
            logStream = new PrintStream(new FileOutputStream(logFile, true), true);
            logln("Logging started to " + logFile + ". (defined in server.conf)");
         } catch (Exception ex) {
            ex.printStackTrace();
            logStream = System.err;
         }
      }
      else {
         logStream = System.err;
         logln("Could not get LogFile from server.conf");
      }

      verbosityLevel = getConfigInt("LogVerbosity", LINFO1);
      logln("Log Verbosity set to " + verbosityLevel);

      boolean dbMode = getConfigString("SERVER_MODE", "database").equals("database");
      if (!dbMode) {
         cm = new BasicConnectionManager(this, config);
      }
      else {
         Connection con = getJDBCConnection();
         if (con == null) {
            cm = new BasicConnectionManager(this, config);
         }
         else {
            cm = new DatabaseConnectionManager(this, config, con);
         }
      }
      cm.start();
      mh = new ManagerHelper(cm, config);
      mh.start();
   }

   /**
    * run is invoked when the thread is kicked off, accepts a new socket.
    */

   public void run() {
      try {
         while (true) {
            Socket s = ss.accept();
            cm.add(s);
         }
      } catch (Exception ex) {
         logln("CollabreateServer terminating: " + ex.getMessage());
      }
   }

   /**
    * terminate closes open sockets and attempts to 'nicely' terminate related objects
    */
   protected void terminate() {
      try {
         ss.close();
         cm.terminate();
         mh.terminate();
         System.exit(0);
      } catch (Exception ex) {
      }
   }

   /**
    * main the main function does little more tham instantiate a new Collabreate Server
    */

   public static void main(String args[]) throws Exception {
      CollabreateServer cs = null;
      if (args.length == 1) {
         //user specified a config file
         cs = new CollabreateServer(args[0]);
      }
      else {
         cs = new CollabreateServer();
      }
      cs.start();
   }

}

