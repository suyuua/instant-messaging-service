/*
 * User class that contains all the information about the user
 */
import java.io.*;
import java.net.Socket;

public class User 
{	
	//UserName and Password
	private String userName;
	private String password;
	
	//must be updated on login or set on creation of new user
	private String ipAddr;	
	private String keyString; //key string associated with socket
	private String[] connections;	//who you're current chat group is
	private boolean hasDisconnected = false;	//check if you logged out intentionally, disconnected or client crash
	private Socket socket;	//current socket of user
	private boolean isOnline;
	private boolean isPrivateChat = false; //is user in a private chat currently
	
	//storage arrays
	public User[] friends = {null};	//alias of friends	
	public User[] pChat = {null}; //private chat group
	public String[] chatLog = {null};	//each message stored in array
	public File[] repository = {null}; //stored file repository for a user
	
	//Constructor
	public User(String userName, String password)
	{
		this.userName = userName;
		this.password = password;
	}
	
	public void setIP(String ipAddr)
	{
		this.ipAddr = ipAddr;
	}
	
	public String getIP()
	{
		return this.ipAddr;
	}
	
	public String getName()
	{
		return this.userName;
	}
	
	public String getPass()
	{
		return this.password;
	}
	
	public boolean getIsOnline()
	{
		return this.isOnline;
	}
	
	public void setIsOnline(boolean o)
	{
		this.isOnline = o;
	}
	
	public Socket getSocket()
	{
		return this.socket;
	}
	
	public void setKeyString(String keys)
	{
		this.keyString = keys;
	}
	
	public String getKeyString()
	{
		return this.keyString;
	}
	
	public void setIsPrivateChat(boolean value)
	{
		this.isPrivateChat = value;
	}
	
	public boolean getIsPrivateChat()
	{
		return this.isPrivateChat;
	}
	
	public void setHasDisconnected(boolean value)
	{
		this.hasDisconnected = value;
	}
	
	public boolean getHasDisconnected()
	{
		return this.hasDisconnected;
	}
	
	//add a new friend to your list
	public void addFriend(User user)
	{
		if(this.friends[0] == null)
		{
			this.friends[0] = user;
		}
		else
		{
			User[] temp = new User[this.friends.length+1];
			for(int i = 0; i<this.friends.length; i++)
			{
				temp[i] = this.friends[i];
			}
			temp[temp.length-1] = user;
			this.friends = temp;
		}
	}
	
	//delete a friend
	public void removeFriend(String theirName)
	{
		int index = 0;
		for(int i =0; i<this.friends.length;i++)
		{
			if(this.friends[i].getName().equals(theirName))
			{
				index = i;
				break;
			}
		}
		
		if(this.friends.length>1)
		{
			User[] n = new User[this.friends.length-1];
			System.arraycopy(this.friends, 0, n, 0, index);
			System.arraycopy(this.friends, index+1, n, index, this.friends.length - index-1);
			
			this.friends = n;
		}
		else
		{
			this.friends[0] = null;
		}
	}
	
	//add to private chat array
	public void addToPrivateChat(User user)
	{
		if(this.pChat[0] == null)
		{
			this.pChat[0] = user;
		}
		else
		{
			User[] temp = new User[this.pChat.length+1];
			for(int i = 0; i<this.pChat.length; i++)
			{
				temp[i] = this.pChat[i];
			}
			temp[temp.length-1] = user;
			this.pChat = temp;
		}
	}
	
	//delete from private chat
	public void removeFromPrivateChat(String theirName)
	{
		int index = 0;
		for(int i =0; i<this.pChat.length;i++)
		{
			if(this.pChat[i].getName().equals(theirName))
			{
				index = i;
				break;
			}
		}
		
		if(this.pChat.length>1)
		{
			User[] n = new User[this.pChat.length-1];
			System.arraycopy(this.pChat, 0, n, 0, index);
			System.arraycopy(this.pChat, index+1, n, index, this.pChat.length - index-1);
			
			this.pChat = n;
		}
		else
		{
			this.pChat[0] = null;
		}
	}
	
	//add file to a user's repo
	public void addToRepository(File file)
	{
		if(this.repository[0] == null)
		{
			this.repository[0] = file;
		}
		else
		{
			File[] temp = new File[this.repository.length+1];
			for(int i = 0; i<this.repository.length; i++)
			{
				temp[i] = this.repository[i];
			}
			temp[temp.length-1] = file;
			this.repository = temp;
		}
	}
	
	//delete file from user's repository
	public void removeFromRepository(String fileName)
	{
		int index = 0;
		for(int i =0; i<this.repository.length;i++)
		{
			if(this.repository[i].getName().equals(fileName))
			{
				index = i;
				break;
			}
		}
		
		if(this.repository.length>1)
		{
			File[] n = new File[this.repository.length-1];
			System.arraycopy(this.repository, 0, n, 0, index);
			System.arraycopy(this.repository, index+1, n, index, this.repository.length - index-1);
			
			this.repository = n;
		}
		else
		{
			this.repository[0] = null;
		}
	}
	
	//add to chat log
	public void addToChatLog(String message)
	{
		if(this.chatLog[0] == null)
		{
			this.chatLog[0] = message;
		}
		else
		{
			String[] temp = new String[this.chatLog.length+1];
			for(int i = 0; i<this.chatLog.length; i++)
			{
				temp[i] = this.chatLog[i];
			}
			temp[temp.length-1] = message;
			this.chatLog = temp;
		}
	}
	
	//grab chat log and return as a string for sending
	public String getChatLog()
	{
		String msg = "Tlog:";
		
		//iterate through array and concatenate messages
		for(int i = 0; i < this.chatLog.length; i++)
		{
			msg += this.chatLog[i]+":";
		}
		System.out.println("Chat log: " + msg);
		return msg+"\n";
	}
}
