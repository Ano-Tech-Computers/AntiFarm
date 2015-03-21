package no.atc.floyd.bukkit.antifarm;


//import java.io.*;

//import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
//import org.bukkit.Server;
//import org.bukkit.event.Event.Priority;
//import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
//import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

//import com.nijikokun.bukkit.Permissions.Permissions;

/**
* AntiFarm plugin for Bukkit
*
* @author FloydATC
*/
public class AntiFarm extends JavaPlugin implements Listener {
    //public static Permissions Permissions = null;
    
	public static final Logger logger = Logger.getLogger("Minecraft.AntiFarm");
	
	private static ConcurrentHashMap<UUID,Long> spawned = new ConcurrentHashMap<UUID,Long>();
	private static ConcurrentHashMap<UUID,Location> spawned_at = new ConcurrentHashMap<UUID,Location>();
	private static Long minimum_age_ms = 120 * 1000L;
	private static Long maximum_age_ms = 900 * 1000L;
	private static Random r = new Random();

    public void onDisable() {
        // TODO: Place any custom disable code here

        // NOTE: All registered events are automatically unregistered when a plugin is disabled
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
    	PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!" );
    }

    public void onEnable() {
        // TODO: Place any custom enable code here including the registration of any events

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);

        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
	
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn( CreatureSpawnEvent event ) {
    	Entity entity = event.getEntity();
    	
    	if (event.isCancelled()) {
        	//logger.info("[AntiFarm] Would blacklist but isCancelled: "+entity+" "+entity.getUniqueId());
        	return;
    	}
    	// Blacklist critters from a spawner
    	if (event.getSpawnReason() == SpawnReason.SPAWNER) {
        	if (isBlacklisted(entity) == false) {
            	//logger.info("[AntiFarm] Blacklisting: "+entity+" "+entity.getUniqueId());
        		blacklist(entity);
        	} else {
            	//logger.info("[AntiFarm] Already blacklisted: "+entity+" "+entity.getUniqueId());
        	}
        	pruneBlacklist();
        	//logger.info("[AntiFarm] Blacklist now has "+totalBlacklisted()+" entries");
    	}
    }    

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity ( EntityDamageByEntityEvent event) {
    	Entity attacker = event.getDamager();
    	Entity defender = event.getEntity();
    	if (isBlacklisted(attacker) && defender instanceof Player) { 
    		// Whitelist critter if it successfully attacks a Player
        	//logger.info("[AntiFarm] Hurt player; No longer blacklisted: "+attacker+" "+attacker.getUniqueId());
    		spawned.remove(attacker);
    		return;
    	}
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath( EntityDeathEvent event ) {
    	Entity entity = event.getEntity();
    	//logger.info("[AntiFarm] Processing the death of "+entity+" "+entity.getUniqueId());
    	
    	// Farm animals should only drop 1 xp
    	EntityType type = entity.getType();
    	if (type == EntityType.CHICKEN) { event.setDroppedExp(1); }
    	if (type == EntityType.COW) { event.setDroppedExp(1); }
    	if (type == EntityType.PIG) { event.setDroppedExp(1); }
    	if (type == EntityType.SHEEP) { event.setDroppedExp(1); }
    	
    	// Gun powder should never drop
    	List<ItemStack> drops = event.getDrops();
    	for (Iterator<ItemStack> i = drops.iterator(); i.hasNext();) {
    		ItemStack s = i.next();
    		if (s.getTypeId() == 289) {
    			i.remove();
    		}
    	}

    	if (isBlacklisted(entity)) {
    		long age = blacklistedAge(entity);
    		double distance = distanceWalked(entity);
        	logger.info("[AntiFarm] Blacklisted entity "+entity+" killed after "+distance+" meters/"+((int) age/1000)+" seconds ");
    		if (blacklistedAge(entity) < minimum_age_ms) {
    			// Congratulations, you have 5% chance of getting your reward
    			Integer random = r.nextInt(100);
    			if (random < 95) {
	    	    	logger.info("[AntiFarm] No reward for killing "+entity+" "+entity.getUniqueId());
	    	    	event.setDroppedExp(0);		// No XP reward
	    	    	event.getDrops().clear();	// No loot
    			}
    		} else {
    	    	//logger.info("[AntiFarm] Lived long enough: "+entity+" "+entity.getUniqueId());
    		}
    		whitelist(entity);
        	//logger.info("[AntiFarm] Killed; No longer blacklisted: "+entity+" "+entity.getUniqueId());
    	} else {
        	//logger.info("[AntiFarm] NOT blacklisted: "+entity+" "+entity.getUniqueId());
    	}
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDespawn( ItemDespawnEvent event ) {
    	Entity entity = event.getEntity();
    	if (isBlacklisted(entity)) {
        	//logger.info("[AntiFarm] Despawned; No longer blacklisted: "+entity+" "+entity.getUniqueId());
    		whitelist(entity);
    	}
    }   
    
    private void blacklist(Entity entity) {
    	spawned.putIfAbsent(entity.getUniqueId(), System.currentTimeMillis());
    	spawned_at.putIfAbsent(entity.getUniqueId(), entity.getLocation());
    }

    private void whitelist(Entity entity) {
    	spawned.remove(entity.getUniqueId());
    	spawned_at.remove(entity.getUniqueId());
    }

    private boolean isBlacklisted(Entity entity) {
    	return spawned.containsKey(entity.getUniqueId());
    }
    
    private double distanceWalked(Entity entity) {
    	Location initial = spawned_at.get(entity.getUniqueId());
    	if (initial == null) return -1;
    	Location current = entity.getLocation();
    	return current.distance(initial);
    }
    
    private Long blacklistedAge(Entity entity) {
    	Long when_ms = spawned.get(entity.getUniqueId());
    	if (when_ms == null) {
    		return null;
    	} else {
    		return System.currentTimeMillis() - when_ms;
    	}
    }

    private Integer totalBlacklisted() {
    	return spawned.size();
    }
    
    private void pruneBlacklist() {
    	Long now = System.currentTimeMillis();
    	for (UUID key : spawned.keySet()) {
    		Long blacklisted = spawned.get(key);
    		Long age_ms = now - blacklisted;
        	if (age_ms > maximum_age_ms) {
        		spawned.remove(key);
        		spawned_at.remove(key);
        	}
    	}
    }
}

