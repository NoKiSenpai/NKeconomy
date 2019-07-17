package be.noki_senpai.NKeconomy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import be.noki_senpai.NKeconomy.data.Accounts;
import be.noki_senpai.NKeconomy.listeners.EcoCompleter;
import be.noki_senpai.NKeconomy.listeners.PlayerConnectionListener;
import be.noki_senpai.NKeconomy.utils.Economy_NKeconomy;
import be.noki_senpai.NKeconomy.utils.SQLConnect;
import net.milkbowl.vault.economy.Economy;

public class NKeconomy extends JavaPlugin implements PluginMessageListener
{
	public final static String PName = "[NKeconomy]";
	public static String prefix;
	public static Map<String, String> table = new HashMap<>();
	
	// Options
	public static double startAmount = 100;
	public static String currency = "Lumi";
	
	// Accounts datas
	public static Map<String, Accounts> accounts = new TreeMap<String, Accounts>(String.CASE_INSENSITIVE_ORDER);
	public static List<String> playerListServer = new ArrayList<String>();
	
	private static NKeconomy instance;
	private static Connection bdd = null;
	private static Economy economy = null;
	private ConsoleCommandSender console = getServer().getConsoleSender();
	
	
	
		
	String tmp1 = "";
	String tmp2 = "";
	
	// Fired when plugin is first enabled
	@Override
    public void onEnable() 
	{
		instance = this;
		
		// Say to Vault "Hey ! I'm an economy plugin !"
		Bukkit.getServicesManager().register(Economy.class, new Economy_NKeconomy(this), getServer().getPluginManager().getPlugin("Vault"), ServicePriority.Normal);
		
		if (!setupEconomy()) 
		{
            console.sendMessage(ChatColor.DARK_RED + PName + " Disabled due to no Vault dependency found ! (Error#A.2.000)");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
				
		this.saveDefaultConfig();
		
		if(this.getConfig().getBoolean("use-mysql"))
		{
			prefix = this.getConfig().getString("table-prefix");
			startAmount = this.getConfig().getDouble("start-amount");
			currency = this.getConfig().getString("currency").replace("&", "�");
			
			// Save table name
			table.put("accounts", prefix + "accounts");

			//Setting database informations		
			SQLConnect.setInfo(this.getConfig().getString("host"), this.getConfig().getInt("port"), this.getConfig().getString("dbName"), this.getConfig().getString("user"), this.getConfig().getString("password"));
			try
			{
				bdd = SQLConnect.getHikariDS().getConnection();
			} 
			catch (SQLException e1)
			{
				bdd = null;
				console.sendMessage(ChatColor.DARK_RED + PName + " Error while attempting database connexion. Verify your access informations in config.yml");
				getServer().getPluginManager().disablePlugin(this);
				e1.printStackTrace();
			}
			
			try 
			{
				//Creating database structure if not exist
				Storage.createTable(this.getConfig().getString("dbName"), prefix, table);
			} 
			catch (SQLException e) 
			{
				console.sendMessage(ChatColor.DARK_RED + PName + " Error while creating database structure. (Error#A.2.002)");
				getServer().getPluginManager().disablePlugin(this);
			}
			
			//On command
			getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
			this.getCommand("eco").setTabCompleter(new EcoCompleter());
			getCommand("eco").setExecutor(new EcoCmd());
			
			//Data exchange between servers
			this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		    this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
			
		    //Get all connected players
			Bukkit.getOnlinePlayers().forEach(player -> accounts.putIfAbsent(player.getDisplayName(), new Accounts(player.getUniqueId())));
			
			new BukkitRunnable() 
			{
			    @Override
			    public void run() 
			    {
			        saveAll();
			    }
			}.runTaskTimerAsynchronously(this, 0, 300*20);
			
		}
		else
		{
			console.sendMessage(ChatColor.DARK_RED + PName + " Disabled because this plugin only use MySQL database. Please set to true the 'use-mysql' field in config.yml");
			getServer().getPluginManager().disablePlugin(this);
		}
		
    }
	
	
    // Fired when plugin is disabled
    @Override
    public void onDisable() 
    {
    	if(bdd != null)
    	{
    		saveAll();
        	accounts.clear();
        	try
    		{
    			bdd.close();
    		} 
        	catch (SQLException e)
    		{
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	kickAllPlayers();
    	console.sendMessage(ChatColor.GREEN + PName + " has been disable.");
    }
    

    
	//######################################
	// Getters & Setters
	//######################################
    
    // Getter 'instance'
	public static NKeconomy getInstance()
	{
		return instance;
	}
	
	// Getter 'bdd'
	public Connection getConnection()
	{
		return bdd;
	}
	
	// Getter 'bdd'
	public Economy getEconomy()
	{
		return economy;
	}
		
	// Getter & setter 'console'
	public ConsoleCommandSender getConsole()
	{
		return console;
	}
	public void setConsole(ConsoleCommandSender console)
	{
		this.console = console;
	}
	
	
	
	//######################################
	// Setup Vault economy
	//######################################
	
	private boolean setupEconomy() 
	{
        if (getServer().getPluginManager().getPlugin("Vault") == null) 
        {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) 
        {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
	
	
	
	//######################################
	// Disable this plugin
	//######################################
	
	public void disablePlugin()
	{
		getServer().getPluginManager().disablePlugin(this);
	}
	
	
	
	//######################################
	// Save all players amounts
	//######################################
	
	public void saveAll()
	{
		if(accounts.size() > 0)
		{
			PreparedStatement ps = null;
			String req = null;
			tmp1 = "";
			tmp2 = "(";
			
			accounts.forEach((key, value) -> 
			{
				tmp1 += "WHEN " + value.getId() + " THEN " + value.getAmount() + " ";
				tmp2 += value.getId() + ",";
			});
			
			tmp2 += "-1)";
	
	        try
			{
	        	req = "UPDATE " + table.get("accounts") + " SET amount = CASE id " + tmp1 + "ELSE amount END WHERE id IN" + tmp2; 
				ps = bdd.prepareStatement(req);
				ps.executeUpdate();
				ps.close();
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	
	
	//######################################
	// Economy functions
	//######################################
	
	// Get amount of a player
	public static double getBalance(String playerName)
	{
		if(accounts.containsKey(playerName))
    	{
    		return accounts.get(playerName).getAmount();
    	}
		else
		{
			Connection bdd = null;
			PreparedStatement ps = null;
			ResultSet resultat = null;
			String req = null;
			
	        try
			{
	        	bdd = NKeconomy.getInstance().getConnection();
	        	
	        	req = "SELECT amount FROM " + table.get("accounts") + " WHERE name = ?";	
				ps = bdd.prepareStatement(req);
				ps.setString(1, playerName);
				
		        resultat = ps.executeQuery();
		        if(resultat.next())
		        {
		        	double amount = resultat.getDouble("amount");
		        	ps.close();
		        	resultat.close();
		        	return amount;
		        }
		        ps.close();
	        	resultat.close();
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}   
		}
		return 0;
	}
	
	// Verify that player has an account
	public static boolean hasAccount(String playerName)
	{
		if(NKeconomy.accounts.containsKey(playerName))
    	{
    		return true;
    	}
		else
		{
			Connection bdd = null;
			PreparedStatement ps = null;
			ResultSet resultat = null;
			String req = null;
			
	        try
			{
	        	bdd = NKeconomy.getInstance().getConnection();
	        	
	        	req = "SELECT COUNT(id) FROM " + NKeconomy.table.get("accounts") + " WHERE name = ?";
				ps = bdd.prepareStatement(req);
				ps.setString(1, playerName);
				
		        resultat = ps.executeQuery();
		        resultat.next();
		        
		        if(resultat.getInt(1)==1)
		        {
		        	ps.close();
		        	resultat.close();
		        	return true;
		        }
		        ps.close();
		        resultat.close();
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}   
		}
		return false;
	}

	// Add amount to a player
	public static void payAmount(String playerName, Double amount, String sender)
	{
		amount = round(amount);
		if(accounts.containsKey(playerName))
    	{
			accounts.get(playerName).addAmount(amount);
			if(Bukkit.getPlayer(playerName)!=null)
			{
				Bukkit.getPlayer(playerName).sendMessage(ChatColor.AQUA + sender + ChatColor.GREEN + " vous avez donn� " + format(amount) + " " + currency);
			}
    	}
		else if(playerListServer.contains(playerName))
		{
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("NKeconomy");
			out.writeUTF("add|" + playerName + "|" + amount + "|" + sender);
			
			Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
			
			player.sendPluginMessage(NKeconomy.getInstance(), "BungeeCord", out.toByteArray());
		}
		else
		{
			Connection bdd = null;
			PreparedStatement ps = null;
			String req = null;

	        try
			{
	        	bdd = getInstance().getConnection();
	        	
	        	req = "UPDATE " + table.get("accounts") + " SET amount = amount + ? WHERE name = ?";
				ps = bdd.prepareStatement(req);
				ps.setDouble(1, amount);
				ps.setString(2, playerName);
				
				ps.executeUpdate();
				ps.close();
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	// Add amount to a player
	public static void giveAmount(String playerName, Double amount, boolean crossServer)
	{
		amount = round(amount);
		if(accounts.containsKey(playerName))
    	{
			accounts.get(playerName).addAmount(amount);
			if(crossServer && Bukkit.getPlayer(playerName) != null)
			{
				Bukkit.getPlayer(playerName).sendMessage(ChatColor.GREEN + " Vous avez re�u " + format(amount) + " " + currency);
			}
    	}
		else if(playerListServer.contains(playerName))
		{
			if(accounts.size() != 0)
			{
				ByteArrayDataOutput out = ByteStreams.newDataOutput();
				out.writeUTF("NKeconomy");
				out.writeUTF("add|" + playerName + "|" + amount + "|null");
				
				Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
				
				player.sendPluginMessage(NKeconomy.getInstance(), "BungeeCord", out.toByteArray());
			}
			else
			{
				NKeconomy.getInstance().console.sendMessage(ChatColor.DARK_RED + PName + " " + playerName + " est connect�(e) sur un autre serveur. Utilisez la console de ce serveur pour lui donner des " + currency);
			}
		}
		else
		{
			Connection bdd = null;
			PreparedStatement ps = null;
			String req = null;

	        try
			{
	        	bdd = getInstance().getConnection();
	        	
	        	req = "UPDATE " + table.get("accounts") + " SET amount = amount + ? WHERE name = ?";
				ps = bdd.prepareStatement(req);
				ps.setDouble(1, amount);
				ps.setString(2, playerName);
				
				ps.executeUpdate();
				ps.close();
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	// Remove amount to a player
	public static boolean takeAmount(String playerName, Double amount, boolean crossServer)
	{
		amount = round(amount);
		if(NKeconomy.getBalance(playerName) >= amount)
    	{
			if(accounts.containsKey(playerName))
	    	{
				accounts.get(playerName).removeAmount(amount);
				if(crossServer && Bukkit.getPlayer(playerName)!=null)
				{
					Bukkit.getPlayer(playerName).sendMessage(ChatColor.GREEN + " Vous avez perdu " + format(amount) + " " + currency);
				}
				return true;
	    	}
			else if(!crossServer && playerListServer.contains(playerName))
			{
				if(accounts.size() != 0)
				{
					ByteArrayDataOutput out = ByteStreams.newDataOutput();
					out.writeUTF("NKeconomy");
					out.writeUTF("remove|" + playerName + "|" + amount);
					
					Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
					
					player.sendPluginMessage(NKeconomy.getInstance(), "BungeeCord", out.toByteArray());
					return true;
				}
				else
				{
					NKeconomy.getInstance().console.sendMessage(ChatColor.DARK_RED + PName + " " + playerName + " est connect�(e) sur un autre serveur. Utilisez la console de ce serveur pour lui retirer des " + currency);
					return false;
				}
				
			}
			else
			{
				Connection bdd = null;
				PreparedStatement ps = null;
				String req = null;
	
		        try
				{
	        		bdd = getInstance().getConnection();
		        	
		        	req = "UPDATE " + table.get("accounts") + " SET amount = amount - ? WHERE name = ?";
					ps = bdd.prepareStatement(req);
					ps.setDouble(1, amount);
					ps.setString(2, playerName);
					
					ps.executeUpdate();
					ps.close();
					return true;
				} 
		        catch (SQLException e)
				{
					e.printStackTrace();
				}
			}
    	}
		return false;
	}

	// Set amount of a player
	public static void setAmount(String playerName, Double amount, boolean crossServer)
	{
		amount = round(amount);
		if(accounts.containsKey(playerName))
    	{
			accounts.get(playerName).setAmount(amount);
			if(crossServer && Bukkit.getPlayer(playerName)!=null)
			{
				Bukkit.getPlayer(playerName).sendMessage(ChatColor.GREEN + " Vous avez maintenant " + format(amount) + " " + currency);
			}
    	}
		else if(playerListServer.contains(playerName))
		{
			if(accounts.size() != 0)
			{
				ByteArrayDataOutput out = ByteStreams.newDataOutput();
				out.writeUTF("NKeconomy");
				out.writeUTF("set|" + playerName + "|" + amount);
				
				Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
				
				player.sendPluginMessage(NKeconomy.getInstance(), "BungeeCord", out.toByteArray());
			}
			else
			{
				NKeconomy.getInstance().console.sendMessage(ChatColor.DARK_RED + PName + " " + playerName + " est connect�(e) sur un autre serveur. Utilisez la console de ce serveur pour lui d�finir son nombre de " + currency);
			}
		}
		else
		{
			Connection bdd = null;
			PreparedStatement ps = null;
			String req = null;

	        try
			{
	        	bdd = getInstance().getConnection();
	        	
	        	req = "UPDATE " + table.get("accounts") + " SET amount = ? WHERE name = ?";
				ps = bdd.prepareStatement(req);
				ps.setDouble(1, amount);
				ps.setString(2, playerName);
				
				ps.executeUpdate();
				ps.close();
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	// get top amount of players
	public static LinkedHashMap<String, Double> topAmount(int page)
	{
		if(page >= 1)
		{
			LinkedHashMap<String, Double> topAmount = new LinkedHashMap<String, Double>();
			
			Connection bdd = null;
			PreparedStatement ps = null;
			ResultSet resultat = null;
			String req = null;
	
			
	        try
			{
	        	bdd = getInstance().getConnection();
	        	
	        	req = "SELECT name, amount FROM " + table.get("accounts") + " ORDER BY amount DESC LIMIT ?, 10";
				ps = bdd.prepareStatement(req);
				ps.setInt(1, (page-1)*10);
				resultat = ps.executeQuery();
				
				while (resultat.next()) 
				{
					topAmount.put(resultat.getString("name"), resultat.getDouble("amount"));
				}
				
				ps.close();
				resultat.close();
				return topAmount;
			} 
	        catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
		return null;
	}
	
	
	
	//######################################
	// Utils functions
	//######################################
	
	// Format amount
	public static String format(double amount) 
    {
		String pattern = "";
		if(amount % 1 == 0)
    	{
			pattern = "###,###,###,##0";
    	}
		else
		{
			pattern = "###,###,###,##0.00";
		}
    	
    	DecimalFormatSymbols formatSymbols = new DecimalFormatSymbols(Locale.FRENCH);
        formatSymbols.setDecimalSeparator(',');
        formatSymbols.setGroupingSeparator(' ');
    	
        DecimalFormat decimalFormat = new DecimalFormat(pattern, formatSymbols);
    	
    	String format = decimalFormat.format(amount);
  
        return format;
	}
	
	// Round amount
	public static double round(double value) 
	{
	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(5, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}
	
	
	
	//######################################
	// Data exchange between servers
	//######################################

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message)
	{
		if (!channel.equals("BungeeCord")) 
		{
			return;
		}
		
		ByteArrayDataInput in = ByteStreams.newDataInput(message);
		String subchannel = in.readUTF();
		
		if (subchannel.equals("NKeconomy")) 
		{
			String[] args = in.readUTF().split("|");
			if(args.length >= 4)
			{
				switch(args[0])
				{
					case "give": NKeconomy.giveAmount(args[1], Double.parseDouble(args[2]), true);
						break;
					case "pay": NKeconomy.payAmount(args[1], Double.parseDouble(args[2]), args[3]);
						break;
					case "remove": NKeconomy.takeAmount(args[1], Double.parseDouble(args[2]), true);
						break;
					case "set": NKeconomy.setAmount(args[1], Double.parseDouble(args[2]), true);
						break;
					case "playerJoin": playerListServer.add(args[1]);
						break;
					case "playerLeave": playerListServer.remove(args[1]);
						break;
					default:
				}
			}
		}
	}
	
	
	
	//######################################
	// External feature
	//######################################
	
	public static void kickAllPlayers()
	{
		Bukkit.getOnlinePlayers().forEach((player) -> 
		{
			player.kickPlayer("Le serveur red�marre.");
		});
	}
}
