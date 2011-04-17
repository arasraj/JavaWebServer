/*--------------------------------------------------------
 *  
 * 1. Raj Arasu - 02/04/10:
 *
 * 2. >javac MyWebServer.java
 *    >javac MyListener.java
 *    >javac cgi/addnums.java
 *
 * 3. Instructions to run this program:
 * 
 * In separate shell windows:
 *
 * > java MyWebServer
 * 
 *
 * 4. Java version used: 
 *
 *    build 1.6.0.0
 *
 * 5. List of files included in this directory:
 *
 *  a. MyWebServer.java
 *  b. MyListener.java  
 *  c. cat.html
 *  d. checklist.html
 *  e. dog.txt
 *  f. index.html
 *  g. notfound.html
 *  h. http-streams.txt
 *  i. serverlog.txt
 *  j. cgi/
 *	addnums.java
 *	addnums.php
 *  k. css/
 *  	404.css
 *  	style.css
 *  l. images/
 *  
 *  6. Notes:
 *
 *  In order to run php script "php cli" must be installed.  
 *
 *----------------------------------------------------------*/

import java.io.*; // get IO libraries
import java.net.*; // get java networking libraries
import java.util.*;

class Worker extends Thread
{
	Socket sock;

	Worker(Socket sock)
	{
		this.sock = sock; //take constructor's parameter and set to field
	}

	//run thread
	public void run()
	{
		PrintStream out = null;
		BufferedReader in = null;
		String mimeType = null;
		String fileName = null;

		try {
			out = new PrintStream(sock.getOutputStream()); //create stream to send data to client
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));  //create stream to read data from client
			
			//if server control variable is true 
			if (MyWebServer.controlSwitch != true)
			{
				System.out.println("Listener is now shutting down as per client request."); //print this on the server
			}
			else //otherwise run server thread
			{
				String browserRequest = null; //var for storing what HTTP requests the browser sends

				try { 
					browserRequest = in.readLine(); //grab the first line of a browser request

					System.out.println(browserRequest);
					//if (getFileString(browserRequest) != null)
					fileName = getFileString(browserRequest); //grab the filename out of the http request
					fileName = fileName.replace("..", "");//make sure any .. are removed from filename

					//while the brwoser is still sending the http request
					while(in.ready())
					{
						browserRequest = in.readLine();//read in the reqeust line by line
						System.out.println(browserRequest);
					}


					//assuming no . is allowd in folder names
					if(!fileName.contains("."))
					{
						createDirectoryResponse(out, fileName); //send a directory view of folder to browser
					}
					else
					{
						//based on mime type create appropriate response
						if(!getMimeType(fileName).equals("other"))
						{
							createResponse(fileName, out, sock); //create a normal response
						}
						else if(fileName.split("[.]")[1].contains("cgi") || fileName.split("[.]")[1].contains("php"))
						{
							//enter this  if statement if the http request is for a cgi script
							
							//check to see if the request cam from a submitted form
							if(fileName.contains("?"))
							{
								String[] urlParsed = fileName.split("[?]"); //split string on ?
								//process form and give it the file name and form data
								processForm(out, urlParsed[0], urlParsed[1]); 
								System.out.println("1st: " + urlParsed[0] + " second: " + urlParsed[1]);
							}
							else
								processForm(out, fileName, ""); //run cgi script at normal
						}
						else
							create404Response(out); //give back 404 page to browser
					}


				}
				catch (IOException x) {
					System.out.println("Server read error");
					x.printStackTrace();
				}

			}
			sock.close(); //close connection with client
		}
		catch (IOException ioe) { System.out.println(ioe); } 
	}

	//parse first line of http request
	static String getFileString(String get)
	{
		int bIndex = get.indexOf("/"); // get the position of the start of the filename or file path
		int eIndex = get.indexOf(" ", bIndex); //get the pos of the end of filename or file path
		System.out.println("substring: " + get.substring(bIndex+1, eIndex));

		String formData = "";

		String url = get.substring(bIndex, eIndex); //grab filename or file path
		int lastIndexOfSlash = url.lastIndexOf("/", eIndex); //get the pos of the last instance of a "/"
		int lastIndexOfQuestion;

		//check to see if passing data from file to another
		if((lastIndexOfQuestion = url.lastIndexOf("?", eIndex)) > lastIndexOfSlash)
		{
			//grab data at end of the filename
			formData = url.substring(lastIndexOfQuestion);
			System.out.println("form data" + formData);
		}	
		
		//get filename for file patha and append form data
		String path = get.substring(bIndex+1, eIndex) + formData;

		//if the http request for a file or folder has a / at the end, strip it off
		if(path.lastIndexOf("/") == path.length())
			path = path.substring(0, path.length()-1);

		System.out.println("Requested file/folder: " + path);
		return path;

	}

	// return correct mimetype depending on the file ext
	static String getMimeType(String fn)
	{
		//if a file and not a folder
		if (fn.indexOf(".") >= 0)
		{
			int dotIndex = fn.lastIndexOf(".");
			String ext = fn.substring(dotIndex + 1, fn.length()); //extract the file ext

			//get mime type
			if(ext.equals("html"))
				ext = "text/html";
			else if(ext.equals("txt"))
				ext = "text/plain";
			else if(ext.equals("css"))
				ext = "text/css";
			else if(ext.equals("jpg") || ext.equals("jpeg"))
				ext = "image/jpeg";
			else if(ext.equals("gif"))
				ext = "image/gif";
			else if(ext.equals("png"))
				ext = "image/png";
			else if(ext.equals("java"))
				ext = "text/plain";
			else
				ext = "other"; //assume it is a script

			System.out.println("Mime Type: " + ext);

			return ext;
		}
		
		return null;
	}

	//create a normal http response
	static void createResponse(String fn, PrintStream out, Socket sock)
	{
		StringBuilder body = new StringBuilder(); //create http body
		StringBuilder header = new StringBuilder(); //create http header
		String mimeType = getMimeType(fn); // get the mime type of the file
		File file = new File(fn);

		//if the requested file exists
		if(file.exists())
		{
			BufferedReader fileReader  = null; //var for reading contents of file
			String line = null; 
			long length = 0;
			FileInputStream imageStream = null; //var for reading in bytes from an image
			
			try
			{
				//it the file is a image
				if(mimeType.contains("image"))
				{
					imageStream = new FileInputStream(file); //get input stream for file
					length = file.length(); //get its length for the header
					byte[] buffer = new byte[32768]; //buffer to hold image bytes read in
	
					int imageLength = 0;

					//create normal http response
					header.append("HTTP/1.1 200 OK\n");
					header.append("Content-Type: " + mimeType + "\n");
					header.append("Content-Length: " + length + "\n");
					header.append("Accept-Ranges: bytes\n");
					header.append("\n");

					//convert header to bytes and send to browser
					byte[] headerBytes = header.toString().getBytes();
					int headerByteLen = headerBytes.length;
					out.write(headerBytes);
							
					//read in image bytes and send to browser
					while((imageLength = imageStream.read(buffer)) != -1)
					{
						out.write(buffer, 0, imageLength);
					}
					out.flush(); //write out anything in buffer

				}
				else //otherwise it is a text file
				{
					fileReader = new BufferedReader( new InputStreamReader( new FileInputStream(fn)));

					//read in file and append to body of http response
					while((line = fileReader.readLine()) != null)
					{
						body.append(line + "\n");
					}
					length = body.toString().getBytes().length; //get the lenght of the body for the header

					//create normal http response
					header.append("HTTP/1.1 200 OK\r\n");
					header.append("Content-Length: " + length + "\r\n");
					header.append("Content-Type: " + mimeType + "\r\n");
					header.append("\r\n\r\n");
	
					out.println(header.toString() + body.toString()); //send http response to browser
					System.out.println("HTTP Response: " + header.toString() + body.toString());
				}
			}
			catch (IOException ioe) { ioe.printStackTrace(); }
		}
		else
			create404Response(out); //create 404 page and send back

	}

	//create a directory view of requested folder
	static void createDirectoryResponse(PrintStream out, String folder)
	{
		StringBuilder body = new StringBuilder(); //http body
		StringBuilder header = new StringBuilder(); //http header


		File file = new File("."); //create file for current dir
		String parent = null;
		
		//if the requested folder is the empty string sent from thr browser
		//then assume the browser wants a view of the root dir
		if (folder.equals(""))
		{

			file = new File(".");
			parent = "/";
		}
		else //otherwise get view of sub dir
		{
			file = new File("./" + folder); //create file for requested dir
			folder = "/" + folder; //append / to front 
			parent = folder.substring(0, folder.lastIndexOf("/")); //get the parent of the requested dir

			//if requested folder is / then parent is itself
			if(parent.equals(""))
				parent = "/";
		}

		File[] fileDirs = file.listFiles(); //list file of dir
		String path = null;
		
		//create http body
		body.append("<h1> Index of " + folder + "</h1>");
		body.append("<pre>");
		body.append("<a href='" + parent + "'>Parrent Directory</a><br><br>");

		//for all the directories and files in requested folder
		for( int i=0; i<fileDirs.length; i++)
		{
			//if it is a dir
			if (fileDirs[i].isDirectory())
			{
				//append to http body the dir and its path
				body.append("[Dir]&nbsp; <a href=" + fileDirs[i].toString().substring(1) + ">" + fileDirs[i].toString().substring(2) + "</a>\n");
				System.out.println("dir: " + fileDirs[i]);
			}
			//otherwise do same for files
			else if (fileDirs[i].isFile())
			{
				body.append("[File] <a href=" + fileDirs[i].toString().substring(1) + ">" + fileDirs[i].toString().substring(2) + "</a>\n");
				System.out.println("file: " + fileDirs[i]);
			}
		}

		body.append("</pre>");
		String bodyString = body.toString();
		int length = bodyString.getBytes().length; //get length of body for header

		//create normal http response
		header.append("HTTP/1.1 200 OK\r\n");
		header.append("Content-Length: " + length + "\r\n");
		header.append("Content-Type: text/html \r\n");
		header.append("\r\n\r\n");
		
		System.out.println(bodyString);
		//send http response
		out.println(header.toString());
		out.println(bodyString);
			
	}

	//process form data by exexcuting cgi script
	static void processForm(PrintStream out, String fn, String values)
	{
		System.out.println("fn: " + fn);
		String fileNameNoExt = fn.split("[.]")[0]; //grab filename with no ext
		String[] commandArgs = null; //command line args to run cgi script

		String fileName = null;

		//if the file has a cgi ext.
		//cgi is used as file ext for purposed of this assignment only
		if(fn.split("[.]")[1].equals("cgi"))
		{
			//append .class to file
			fileName = fileNameNoExt + ".class";
			//command line args to run java sub process
			commandArgs = new String[]{"java" ,"-cp", "cgi/",  fileNameNoExt.split("/")[1], values}; 
		}
		else if(fn.split("[.]")[1].equals("php")) //execute php script
		{
			fileName = fn;
			//command line args to run php script
			commandArgs = new String[]{"php", fileName, values}; 
		}

		File execFile = new File(fileName);

		//if script exists
		if(execFile.exists())
		{
			try
			{
				Process p = Runtime.getRuntime().exec(commandArgs); //start a process from the Runtime
				BufferedReader in = new BufferedReader( new InputStreamReader( p.getInputStream())); //get input stream to communicate with process

				String line = null;		

				//read in any data send from subprocess via standard output
				while ((line = in.readLine()) != null)
				{
					//send data from cgi script to browser
					out.println(line);
				}

				out.flush();
				System.out.flush();
			}
			catch (IOException ioe) { ioe.printStackTrace(); }
			catch (Exception e) { e.printStackTrace(); }
		}
	}

	//create 404 page
	static void create404Response(PrintStream out)
	{
		StringBuilder header = new StringBuilder();
		StringBuilder body = new StringBuilder();
		
		try
		{

			BufferedReader fileReader = new BufferedReader( new InputStreamReader( new FileInputStream("notfound.html")));
			String line = null;
	
			int length;
			while((line = fileReader.readLine()) != null)
			{
				body.append(line + "\n");
			}

			length = body.toString().getBytes().length;
			header.append("HTTP/1.1 404 Not Found\r\n");
			header.append("Content-Length:" + length + " \r\n");
			header.append("Content-Type: text/html\r\n");
			header.append("\r\n\r\n");

			out.println(header.toString() + body.toString());
		}
		catch (IOException ioe) { ioe.printStackTrace(); }
	}
}

	
//main server code
class MyWebServer
{
	public static boolean controlSwitch = true; //control variable on whether to run server or not
	
	public static void main(String args[]) throws IOException
	{
		int q_len = 6; //number of requests for OS to queue
		int port = 2540; //port that server will be listening on

		Socket sock;
		ServerSocket servsock = new ServerSocket(port, q_len); //socket to accept from

		System.out.println("Raj Arasu's Web Server is statring up, listening at port " + port + "\n"); //indicate server is starting

		//while the server is supposed to be running
		while(controlSwitch)
		{
			sock = servsock.accept(); //block until a conncection request comes and accept it
			new Worker(sock).start(); //spawn Worker thread with the current socket
		}
	}
}
