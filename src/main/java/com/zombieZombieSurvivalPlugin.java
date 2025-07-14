/*
 * ZombieSurvivalPlugin - 僵尸生存插件
 * Copyright (C) 2025 Roy
 *
 * 本程序是自由软件；你可以依据自由软件基金会发布的
 * GNU 通用公共许可证第三版（GPL v3）或更高版本来修改和重新发布它。
 *
 * 本程序分发时希望有用，但没有任何担保；
 * 甚至没有适销性或特定用途的隐含担保。
 * 详情请参见 GNU 通用公共许可证。
 *
 * 你应该已经收到一份 GNU 通用公共许可证的副本。
 * 如果没有，请查看 <https://www.gnu.org/licenses/>.
 */
package com.zombie;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class ZombieSurvivalPlugin extends JavaPlugin implements Listener {

    // 僵尸可能掉落的物品
    private static final Material[] ZOMBIE_DROPS = {
            Material.ROTTEN_FLESH,
            Material.IRON_INGOT,
            Material.CARROT,
            Material.POTATO,
            Material.GOLD_NUGGET,
            Material.BONE,
            Material.ARROW
    };

    // 最少玩家数才能自动开始游戏
    private int minPlayersToStart = 2; // 你可以根据需要修改默认值

    // 用于记录上次僵尸名称更新的时间
    private long lastZombieNameUpdate = 0;

    // 玩家成就数据
    private final Map<String, Set<Achievement>> playerAchievements = new HashMap<>();

    private void updateTab(Player player) {
        String header = tablistConfig.getString("header", "");
        String footer = tablistConfig.getString("footer", "");

        // 波数变量
        header = header.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{wave}", String.valueOf(wave)); // 关键修改

        footer = footer.replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{wave}", String.valueOf(wave)); // 关键修改

        player.setPlayerListHeaderFooter(header, footer);
    }

    // PersistentDataType for UUID
    public static class UUIDDataType implements PersistentDataType<byte[], UUID> {
        public static final UUIDDataType INSTANCE = new UUIDDataType();

        @Override
        public Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        public Class<UUID> getComplexType() {
            return UUID.class;
        }

        @Override
        public byte[] toPrimitive(UUID uuid, PersistentDataAdapterContext context) {
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return bb.array();
        }

        @Override
        public UUID fromPrimitive(byte[] bytes, PersistentDataAdapterContext context) {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            long firstLong = bb.getLong();
            long secondLong = bb.getLong();
            return new UUID(firstLong, secondLong);
        }
    }

    private GameState gameState = GameState.WAITING;
    private Location spawnPoint;
    private Location shopLocation;
    private int resetCountdown = -1;
    private int wave = 1;

    private Map<String, PlayerData> playerDataMap = new HashMap<>();
    private Map<Entity, ZombieType> zombieTypes = new ConcurrentHashMap<>();
    private Map<String, GunData> playerGuns = new ConcurrentHashMap<>();
    private Set<String> deadPlayers = new HashSet<>();
    private Map<String, Long> reloadCooldowns = new ConcurrentHashMap<>();
    // 玩家配件数据
    private Map<String, Set<GunAttachment>> playerAttachments = new ConcurrentHashMap<>();

    // 随机事件冷却时间（单位：秒）
    private int eventCooldown = 600;

    // 当前随机事件
    private RandomEvent currentEvent = RandomEvent.NONE;
    // 当前事件持续时间（秒）
    private int eventDuration = 0;

    private ScoreboardManager scoreboardManager;
    private Objective objective;

    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    private Villager shopVillager;
    private FileConfiguration scoreboardConfig;
    private FileConfiguration tablistConfig;
    private final Map<Material, GunType> materialToGunType = new HashMap<>();

    {
        // 初始化 materialToGunType 映射
        for (GunType type : GunType.values()) {
            materialToGunType.put(type.material, type);
        }
    }

    @Override
    public void onEnable() {
        // 确保数据文件夹存在
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 手动初始化配置
        if (!new File(getDataFolder(), "config.yml").exists()) {
            try {
                getConfig().options().copyDefaults(true);
                saveConfig();
            } catch (Exception e) {
                getLogger().severe("创建配置文件失败: " + e.getMessage());
            }
        } else {
            reloadConfig();
        }

        // 加载位置
        loadLocationsFromConfig();

        // 初始化计分板
        scoreboardManager = Bukkit.getScoreboardManager();

        // 加载玩家数据
        loadPlayerData();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);

        // 自动保存任务
        new BukkitRunnable() {
            @Override
            public void run() {
                savePlayerData();
            }
        }.runTaskTimer(this, 20 * 60 * 5, 20 * 60 * 5); // 每5分钟保存一次

        // 游戏状态更新任务
        new BukkitRunnable() {
            @Override
            public void run() {
                updateGameState();
                updateZombieNames();
                updateAmmoBars();
                updateResetCountdown();
                updateRandomEvents();
            }
        }.runTaskTimer(this, 0, 20); // 每秒更新一次

        // 清除掉落物和箭的任务
        new BukkitRunnable() {
            @Override
            public void run() {
                clearDropsAndArrows();
            }
        }.runTaskTimer(this, 20 * 10, 20 * 10); // 每10秒清除一次

        // 创建商店村民
        createShopVillager();

        // 保持白天和晴天
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    world.setTime(1000); // 1000为早晨
                    world.setStorm(false);
                    world.setThundering(false);
                    world.setWeatherDuration(999999);
                }
            }
        }.runTaskTimer(this, 0, 200); // 每10秒设置一次

        // 加载自定义计分板配置
        File scoreboardFile = new File(getDataFolder(), "scoreboard.yml");
        if (!scoreboardFile.exists()) {
            saveResource("scoreboard.yml", false);
        }
        scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardFile);

        // 加载自定义Tab配置
        File tablistFile = new File(getDataFolder(), "tab.yml");
        if (!tablistFile.exists()) {
            saveResource("tab.yml", false);
        }
        tablistConfig = YamlConfiguration.loadConfiguration(tablistFile);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 检查无限弹药是否过期
                    if (player.hasMetadata("InfiniteAmmo")) {
                        long expireTime = player.getMetadata("InfiniteAmmo").get(0).asLong();
                        if (System.currentTimeMillis() >= expireTime) {
                            player.removeMetadata("InfiniteAmmo", ZombieSurvivalPlugin.this);

                            // 更新显示
                            ItemStack gun = player.getInventory().getItemInMainHand();
                            if (gun != null) {
                                updateGunLore(player, gun);
                            }
                            updateScoreboard(player);
                            updateAmmoBar(player);

                            // 播放音效提醒
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20, 20); // 每秒检查一次
    }

    // 更新随机事件
    private void updateRandomEvents() {
        if (gameState != GameState.ACTIVE)
            return;

        // 事件冷却
        if (eventCooldown > 0) {
            eventCooldown--;
            return;
        }

        // 事件进行中
        if (currentEvent != RandomEvent.NONE) {
            eventDuration--;

            // 事件结束
            if (eventDuration <= 0) {
                Bukkit.broadcastMessage("§d事件结束: " + currentEvent.displayName);
                currentEvent = RandomEvent.NONE;
                eventCooldown = 600; // 10分钟冷却
            }
            return;
        }

        // 随机触发事件 (10% 几率)
        if (new Random().nextInt(100) < 10) {
            RandomEvent[] events = RandomEvent.values();
            currentEvent = events[new Random().nextInt(events.length)];
            eventDuration = 60; // 60秒

            Bukkit.broadcastMessage("§6§l随机事件触发: " + currentEvent.displayName);
            Bukkit.broadcastMessage("§7" + currentEvent.description);

            // 应用事件效果
            applyRandomEvent(currentEvent);
        }
    }

    // 应用随机事件效果
    private void applyRandomEvent(RandomEvent event) {
        switch (event) {
            case DOUBLE_COINS:
                Bukkit.broadcastMessage("§e所有击杀奖励翻倍!");
                break;
            case ZOMBIE_RAGE:
                for (Entity entity : zombieTypes.keySet()) {
                    if (entity instanceof Zombie) {
                        Zombie zombie = (Zombie) entity;
                        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 1));
                        zombie.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 60, 1));
                    }
                }
                break;
            case HEALTH_REGEN:
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!deadPlayers.contains(player.getUniqueId())) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60, 1));
                    }
                }
                break;
            case BOSS_WAVE:
                Bukkit.broadcastMessage("§4§lBOSS波次来袭! 准备战斗!");
                spawnBossZombie();
                break;
            case NONE:
            default:
                break;
        }
    }

    // 生成BOSS僵尸
    private void spawnBossZombie() {
        if (spawnPoint == null)
            return;

        Location bossLoc = spawnPoint.clone().add(10, 0, 0);
        bossLoc.setY(spawnPoint.getWorld().getHighestBlockYAt(bossLoc) + 1);

        Zombie boss = (Zombie) bossLoc.getWorld().spawnEntity(bossLoc, EntityType.ZOMBIE);
        boss.setCustomName("§4§l僵尸王");
        boss.setCustomNameVisible(true);

        // 设置BOSS属性
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(200);
        boss.setHealth(200);
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(0.25);
        Objects.requireNonNull(boss.getAttribute(Attribute.GENERIC_ARMOR)).setBaseValue(10);

        // 给僵尸添加随机帽子
        Material helmet = Material.LEATHER_HELMET; // 简化头盔
        ItemStack helmetItem = new ItemStack(helmet);
        ItemMeta helmetMeta = helmetItem.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setUnbreakable(true); // 修复：设置头盔不可破坏
            helmetItem.setItemMeta(helmetMeta);
        }
        boss.getEquipment().setHelmet(helmetItem);
        boss.getEquipment().setHelmetDropChance(0.0f); // 防止掉落

        // 添加特殊效果
        boss.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1));
        boss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));

        // 记录僵尸类型
        zombieTypes.put(boss, ZombieType.BOSS);
    }

    // 清除所有掉落物和箭
    private void clearDropsAndArrows() {
        if (gameState != GameState.ACTIVE)
            return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item || entity instanceof Arrow) {
                    entity.remove();
                }
            }
        }
    }

    // 更新所有玩家的弹药经验条
    private void updateAmmoBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                updateAmmoBar(player);
            }
        }
    }

    // 更新单个玩家的弹药经验条
    private void updateAmmoBar(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null) {
            player.setLevel(0);
            player.setExp(0);
            return;
        }
        GunType gunType = materialToGunType.get(mainHand.getType());
        if (gunType != null) {
            NamespacedKey key = new NamespacedKey(this, "gun_id");
            ItemMeta meta = mainHand.getItemMeta();
            if (meta == null)
                return;
            byte[] gunIdBytes = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
            if (gunIdBytes != null) {
                UUID gunId = bytesToUuid(gunIdBytes);
                GunData gunData = playerGuns.get(gunId.toString());
                if (gunData != null) {
                    player.setLevel(gunData.ammo);
                    float exp = (float) gunData.ammo / gunData.getClipSize(player);
                    player.setExp(Math.max(0f, Math.min(1f, exp)));
                    return;
                }
            }
        }
        player.setLevel(0);
        player.setExp(0);
    }

    @Override
    public void onDisable() {
        savePlayerData();
        saveLocationsToConfig();

        // 移除商店村民
        if (shopVillager != null && !shopVillager.isDead()) {
            shopVillager.remove();
        }

    }

    private void createShopVillager() {
        if (shopLocation != null && shopLocation.getWorld() != null) {
            // 移除旧村民
            if (shopVillager != null && !shopVillager.isDead()) {
                shopVillager.remove();
            }

            // 创建新村民
            shopVillager = (Villager) shopLocation.getWorld().spawnEntity(shopLocation, EntityType.VILLAGER);
            shopVillager.setCustomName("§6武器商人");
            shopVillager.setCustomNameVisible(true);
            shopVillager.setProfession(Villager.Profession.WEAPONSMITH);
            shopVillager.setAI(false);
            shopVillager.setInvulnerable(true);
            shopVillager.setSilent(true);
            shopVillager.setMetadata("ShopNPC", new FixedMetadataValue(this, true));
            shopVillager.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 100, true, false));
        }
    }

    private void loadLocationsFromConfig() {
        spawnPoint = loadLocationFromConfig("spawn");
        shopLocation = loadLocationFromConfig("shop");

        // 日志位置信息
        if (spawnPoint != null) {
            getLogger().info("加载出生点: " + formatLocation(spawnPoint));
        } else {
            getLogger().warning("出生点未设置!");
        }

        if (shopLocation != null) {
            getLogger().info("加载商店位置: " + formatLocation(shopLocation));
        } else {
            getLogger().warning("商店位置未设置!");
        }
    }

    private void saveLocationsToConfig() {
        saveLocationToConfig("spawn", spawnPoint);
        saveLocationToConfig("shop", shopLocation);
        saveConfig();
    }

    private Location loadLocationFromConfig(String key) {
        ConfigurationSection section = getConfig().getConfigurationSection(key);
        if (section == null)
            return null;

        String worldName = section.getString("world");
        if (worldName == null)
            return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return null;

        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0),
                (float) section.getDouble("pitch", 0));
    }

    private void saveLocationToConfig(String key, Location loc) {
        if (loc == null || loc.getWorld() == null)
            return;

        ConfigurationSection section = getConfig().createSection(key);
        section.set("world", loc.getWorld().getName());
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", loc.getYaw());
        section.set("pitch", loc.getPitch());
    }

    private String formatLocation(Location loc) {
        if (loc == null)
            return "未设置";
        return loc.getWorld().getName() +
                " (" + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ() + ")";
    }

    private boolean isValidLocation(Location loc) {
        return loc != null && loc.getWorld() != null;
    }

    private Location getSafeSpawnPoint() {
        if (isValidLocation(spawnPoint)) {
            return spawnPoint;
        }
        // 使用世界默认出生点作为备选
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以执行此命令");
            return true;
        }

        Player player = (Player) sender;

        // 权限判断：无权限玩家只能用 /shop 和 /achievements
        boolean hasAdminPerm = player.hasPermission("zombie.admin");
        String cmdName = cmd.getName().toLowerCase();

        if (!hasAdminPerm
                && !(cmdName.equals("shop") || cmdName.equals("achievements") || cmdName.equals("attachments"))) {
            player.sendMessage("§c你没有权限使用该命令！");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("attachments")) {
            openAttachmentManager(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setspawn")) {
            if (player.getLocation().getWorld() == null) {
                player.sendMessage("§c无效位置! 请确保你在有效世界中");
                return true;
            }

            spawnPoint = player.getLocation();
            player.sendMessage("§a出生点已设置!");
            saveLocationToConfig("spawn", spawnPoint);
            saveConfig();
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setshop")) {
            if (player.getLocation().getWorld() == null) {
                player.sendMessage("§c无效位置! 请确保你在有效世界中");
                return true;
            }

            shopLocation = player.getLocation();
            player.sendMessage("§a商店位置已设置!");
            saveLocationToConfig("shop", shopLocation);
            saveConfig();

            // 创建商店村民
            createShopVillager();
            return true;
        }

        // 添加/shop命令
        if (cmd.getName().equalsIgnoreCase("shop")) {
            openShop(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("start")) {
            if (gameState == GameState.WAITING) {
                startGame();
                player.sendMessage("§a游戏开始!");
            } else {
                player.sendMessage("§c游戏已经开始了!");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("end")) {
            if (gameState == GameState.ACTIVE || gameState == GameState.WAITING) {
                endGame();
                player.sendMessage("§a游戏已强制结束!");
            } else {
                player.sendMessage("§c游戏已经结束了!");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("zslocations")) {
            player.sendMessage("§6===== 位置信息 =====");
            player.sendMessage("§a出生点: " + formatLocation(spawnPoint));
            player.sendMessage("§a商店位置: " + formatLocation(shopLocation));
            return true;
        }

        // 成就查看命令
        if (cmd.getName().equalsIgnoreCase("achievements")) {
            showAchievements(player);
            return true;
        }

        return false;

    }

    // 显示玩家成就
    private void showAchievements(Player player) {
        Set<Achievement> achievements = playerAchievements.getOrDefault(player.getName(), new HashSet<>());

        player.sendMessage("§6===== 你的成就 =====");
        if (achievements.isEmpty()) {
            player.sendMessage("§7你还没有获得任何成就");
        } else {
            for (Achievement achievement : Achievement.values()) {
                if (achievements.contains(achievement)) {
                    player.sendMessage("§a✔ " + achievement.displayName + " - §7" + achievement.description);
                } else {
                    player.sendMessage("§7✘ " + achievement.displayName);
                }
            }
        }
    }

    // 授予玩家成就
    private void grantAchievement(Player player, Achievement achievement) {
        String playerName = player.getName();
        Set<Achievement> achievements = playerAchievements.computeIfAbsent(playerName, k -> new HashSet<>());

        if (!achievements.contains(achievement)) {
            achievements.add(achievement);
            player.sendTitle("§6成就解锁!", achievement.displayName, 10, 60, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // 成就奖励
            PlayerData data = playerDataMap.get(playerName);
            if (data != null) {
                data.addCoins(achievement.coinReward);
                player.sendMessage("§a获得成就奖励: §6" + achievement.coinReward + "硬币");
            }
        }
    }

    private void startGame() {
        clearAllZombies();
        // 检查出生点是否设置
        if (!isValidLocation(spawnPoint)) {
            getLogger().severe("出生点未设置! 请使用 /setspawn 命令设置出生点");
            Bukkit.broadcastMessage("§c错误: 出生点未设置! 管理员请使用 /setspawn 设置出生点");
            return;
        }

        gameState = GameState.ACTIVE;
        wave = 1;
        deadPlayers.clear();
        zombieTypes.clear();
        playerGuns.clear();
        reloadCooldowns.clear();
        resetCountdown = -1;
        playerAttachments.clear();
        currentEvent = RandomEvent.NONE;
        eventCooldown = 600;

        // 清除所有掉落物和箭
        clearDropsAndArrows();

        // 重置所有玩家状态
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayer(player);

            // 确保玩家有数据（保留硬币）
            if (!playerDataMap.containsKey(player.getName())) {
                playerDataMap.put(player.getName(), new PlayerData());
            }

            // 切回冒险模式
            player.setGameMode(GameMode.ADVENTURE);

            // 安全传送玩家
            try {
                player.teleport(spawnPoint);
                player.sendMessage("§a你已被传送到出生点!");
            } catch (IllegalArgumentException e) {
                getLogger().severe("传送玩家 " + player.getName() + " 到出生点时出错: " + e.getMessage());
                player.sendMessage("§c传送失败! 出生点位置无效");
                // 使用安全出生点
                player.teleport(getSafeSpawnPoint());
            }

            giveStartingItems(player);
        }

        // 开始僵尸生成
        startZombieSpawner();
        // 修改开局提示文字
        Bukkit.broadcastMessage("§c§l僵尸生存游戏开始! §4§l末日僵尸来临，孤立无援，你/你们能撑多久？");
    }

    private void endGame() {
        gameState = GameState.END;
        Bukkit.broadcastMessage("§c§l游戏已强制结束!");

        // 重置所有玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayer(player);
            player.teleport(getSafeSpawnPoint());
            player.setGameMode(GameMode.ADVENTURE);
        }

        // 清除所有僵尸
        clearAllZombies();

        // 清除所有掉落物
        clearDropsAndArrows();

        // 重置游戏状态
        new BukkitRunnable() {
            @Override
            public void run() {
                gameState = GameState.WAITING;
                Bukkit.broadcastMessage("§a游戏已重置，等待玩家加入...");
            }
        }.runTaskLater(this, 20 * 5); // 5秒后重置
    }

    // 清除所有僵尸
    private void clearAllZombies() {
        for (Entity entity : zombieTypes.keySet()) {
            if (entity instanceof Zombie) {
                entity.remove();
            }
        }
        zombieTypes.clear();

        // 确保清除所有世界的僵尸
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie) {
                    entity.remove();
                }
            }
        }
    }

    private void resetPlayer(Player player) {
        player.getInventory().clear();
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setExp(0);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // 重置最大生命值
        Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20);

        // 重置配件
        playerAttachments.remove(player.getName());

        // 修复：移除死亡状态
        deadPlayers.remove(player.getName());

        // 新增：刷新计分板
        updateScoreboard(player);
    }

    private void giveStartingItems(Player player) {
        player.getInventory().addItem(createGun(GunType.PISTOL));
        player.getInventory().addItem(new ItemStack(Material.BREAD, 5));
        // 添加起始弹药
        player.getInventory().addItem(createAmmoItem(GunType.PISTOL, 32));
        player.getInventory().addItem(new ItemStack(Material.LEATHER_HELMET));
        player.getInventory().addItem(new ItemStack(Material.LEATHER_CHESTPLATE));
        player.getInventory().addItem(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().addItem(new ItemStack(Material.LEATHER_BOOTS));
    }

    private ItemStack createAmmoItem(GunType gunType, int amount) {
        ItemStack ammo = new ItemStack(gunType.ammoType, amount);
        ItemMeta meta = ammo.getItemMeta();
        meta.setDisplayName(gunType.ammoName);
        List<String> lore = new ArrayList<>();
        lore.add("§7用于: " + gunType.displayName);
        lore.add("§7类型: " + gunType.ammoTypeName);
        meta.setLore(lore);
        ammo.setItemMeta(meta);
        return ammo;
    }

    private ItemStack createGun(GunType gunType) {
        ItemStack gun = new ItemStack(gunType.material);
        ItemMeta meta = gun.getItemMeta();
        meta.setDisplayName(gunType.displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7伤害: " + gunType.damage);
        lore.add("§7弹药: " + gunType.clipSize + "/" + gunType.clipSize); // 改为弹药显示
        lore.add("§7装弹时间: " + gunType.reloadTime + "秒");
        lore.add("§7弹药类型: " + gunType.ammoTypeName);
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);

        UUID gunId = UUID.randomUUID();
        playerGuns.put(gunId.toString(), new GunData(gunType, gunType.clipSize, gunId));
        NamespacedKey key = new NamespacedKey(this, "gun_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, uuidToBytes(gunId));
        gun.setItemMeta(meta);
        return gun;
    }

    // 创建配件物品
    private ItemStack createAttachmentItem(GunAttachment attachment) {
        ItemStack item = new ItemStack(attachment.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(attachment.displayName);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + attachment.description);
        lore.add("§6价格: " + attachment.price + "硬币");
        lore.add("§e使用命令/attachment管理配件");
        meta.setLore(lore);
        // 添加唯一标识
        NamespacedKey key = new NamespacedKey(this, "attachment_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, attachment.name());
        // 防止与其他物品堆叠
        meta.setCustomModelData(1000 + attachment.ordinal());
        item.setItemMeta(meta);
        return item;
    }

    // ========================
    // 多页面商店系统实现
    // ========================

    private void openShop(Player player) {
        openShopPage(player, ShopPage.MAIN_MENU);
    }

    private void openShopPage(Player player, ShopPage page) {
        Inventory shop;

        switch (page) {
            case WEAPONS:
                shop = createWeaponsPage(player);
                break;
            case AMMO:
                shop = createAmmoPage(player);
                break;
            case BUFFS:
                shop = createBuffsPage(player);
                break;
            case ARMOR:
                shop = createArmorPage(player);
                break;
            case FOOD:
                shop = createFoodPage(player);
                break;
            case ATTACHMENTS:
                shop = createAttachmentsPage(player);
                break;
            case MAIN_MENU:
            default:
                shop = createMainMenu(player);
                break;
        }

        player.openInventory(shop);
    }

    // 创建主菜单页面
    private Inventory createMainMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, "§8商店主菜单");

        // 背景填充
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bgMeta = background.getItemMeta();
        bgMeta.setDisplayName(" ");
        background.setItemMeta(bgMeta);

        for (int i = 0; i < 27; i++) {
            menu.setItem(i, background);
        }

        // 武器分类按钮
        ItemStack weapons = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta weaponsMeta = weapons.getItemMeta();
        weaponsMeta.setDisplayName("§6§l武器商店");
        weaponsMeta.setLore(Arrays.asList("§7购买各种强大的武器", "§a点击进入"));
        weapons.setItemMeta(weaponsMeta);
        menu.setItem(10, weapons);

        // 弹药分类按钮
        ItemStack ammo = new ItemStack(Material.ARROW);
        ItemMeta ammoMeta = ammo.getItemMeta();
        ammoMeta.setDisplayName("§6§l弹药商店");
        ammoMeta.setLore(Arrays.asList("§7购买各种武器的弹药", "§a点击进入"));
        ammo.setItemMeta(ammoMeta);
        menu.setItem(12, ammo);

        // 增益效果按钮
        ItemStack buffs = new ItemStack(Material.POTION);
        ItemMeta buffsMeta = buffs.getItemMeta();
        buffsMeta.setDisplayName("§d§l增益效果");
        buffsMeta.setLore(Arrays.asList("§7购买各种增益效果", "§a点击进入"));
        buffs.setItemMeta(buffsMeta);
        menu.setItem(14, buffs);

        // 护甲分类按钮
        ItemStack armor = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta armorMeta = armor.getItemMeta();
        armorMeta.setDisplayName("§b§l护甲商店");
        armorMeta.setLore(Arrays.asList("§7购买各种防护装备", "§a点击进入"));
        armor.setItemMeta(armorMeta);
        menu.setItem(16, armor);

        // 食物分类按钮
        ItemStack food = new ItemStack(Material.COOKED_BEEF);
        ItemMeta foodMeta = food.getItemMeta();
        foodMeta.setDisplayName("§e§l食物补给");
        foodMeta.setLore(Arrays.asList("§7购买各种食物补给", "§a点击进入"));
        food.setItemMeta(foodMeta);
        menu.setItem(22, food);

        // 配件分类按钮
        ItemStack attachments = new ItemStack(Material.ANVIL);
        ItemMeta attachmentsMeta = attachments.getItemMeta();
        attachmentsMeta.setDisplayName("§6§l武器配件");
        attachmentsMeta.setLore(Arrays.asList("§7购买各种武器配件", "§a点击进入"));
        attachments.setItemMeta(attachmentsMeta);
        menu.setItem(20, attachments);

        // 玩家信息显示
        PlayerData data = playerDataMap.get(player.getName());
        if (data != null) {
            ItemStack info = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta infoMeta = (SkullMeta) info.getItemMeta();
            infoMeta.setOwningPlayer(player);
            infoMeta.setDisplayName("§a" + player.getName());
            infoMeta.setLore(Arrays.asList(
                    "§7硬币: §6" + data.getCoins(),
                    "§7当前生命值: §c" + (int) player.getHealth() + "❤",
                    "§7当前波数: §e" + wave));
            info.setItemMeta(infoMeta);
            menu.setItem(4, info);
        }

        return menu;
    }

    // 创建武器页面
    private Inventory createWeaponsPage(Player player) {
        Inventory weapons = Bukkit.createInventory(null, 54, "§8武器商店");

        // 添加返回按钮
        weapons.setItem(49, createBackButton());

        // 添加武器
        GunType[] guns = GunType.values();
        for (int i = 0; i < Math.min(45, guns.length); i++) {
            weapons.setItem(i, createShopGun(guns[i]));
        }

        // 添加页面标题
        ItemStack title = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§6§l武器商店");
        titleMeta.setLore(Arrays.asList("§7选择你想要的武器"));
        title.setItemMeta(titleMeta);
        weapons.setItem(45, title);

        return weapons;
    }

    // 创建弹药页面
    private Inventory createAmmoPage(Player player) {
        Inventory ammo = Bukkit.createInventory(null, 54, "§8弹药商店");

        // 添加返回按钮
        ammo.setItem(49, createBackButton());

        // 添加弹药
        GunType[] guns = GunType.values();
        for (int i = 0; i < Math.min(45, guns.length); i++) {
            ammo.setItem(i, createShopAmmo(guns[i]));
        }

        // 添加页面标题
        ItemStack title = new ItemStack(Material.ARROW);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§6§l弹药商店");
        titleMeta.setLore(Arrays.asList("§7购买武器所需的弹药"));
        title.setItemMeta(titleMeta);
        ammo.setItem(45, title);

        return ammo;
    }

    // 创建增益效果页面
    private Inventory createBuffsPage(Player player) {
        Inventory buffs = Bukkit.createInventory(null, 54, "§8增益效果商店");

        // 添加返回按钮
        buffs.setItem(49, createBackButton());

        // 添加增益效果
        buffs.setItem(10, createShopItem(Material.GOLDEN_APPLE, "§a生命提升", "§7增加最大生命值", 200));
        buffs.setItem(12, createShopItem(Material.IRON_CHESTPLATE, "§a伤害减免", "§7减少受到的伤害", 250));
        buffs.setItem(14, createShopItem(Material.POTION, "§a速度提升", "§7增加移动速度", 150));
        buffs.setItem(16, createShopItem(Material.FIREWORK_ROCKET, "§a跳跃提升", "§7增加跳跃高度", 120));
        buffs.setItem(28, createShopItem(Material.GOLDEN_CARROT, "§6夜视效果", "§7获得夜视能力", 100));
        buffs.setItem(30, createShopItem(Material.TOTEM_OF_UNDYING, "§c保命图腾", "§7死亡时自动复活", 1000));
        buffs.setItem(32, createShopItem(Material.BLAZE_POWDER, "§6火焰抗性", "§7免疫火焰伤害", 180));
        buffs.setItem(34, createShopItem(Material.FIRE_CHARGE, "§e无限弹药", "§7获得60秒无限弹药", 400));
        // 添加页面标题
        ItemStack title = new ItemStack(Material.POTION);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§d§l增益效果");
        titleMeta.setLore(Arrays.asList("§7购买各种增益效果"));
        title.setItemMeta(titleMeta);
        buffs.setItem(45, title);

        return buffs;
    }

    // 创建护甲页面
    private Inventory createArmorPage(Player player) {
        Inventory armor = Bukkit.createInventory(null, 54, "§8护甲商店");

        // 添加返回按钮
        armor.setItem(49, createBackButton());

        // 添加铁护甲
        armor.setItem(10, createShopItem(Material.IRON_HELMET, "§a铁头盔", "§7提供头部保护", 100));
        armor.setItem(11, createShopItem(Material.IRON_CHESTPLATE, "§a铁胸甲", "§7提供身体保护", 150));
        armor.setItem(12, createShopItem(Material.IRON_LEGGINGS, "§a铁护腿", "§7提供腿部保护", 120));
        armor.setItem(13, createShopItem(Material.IRON_BOOTS, "§a铁靴子", "§7提供脚部保护", 80));

        // 添加钻石护甲
        armor.setItem(19, createShopItem(Material.DIAMOND_HELMET, "§b钻石头盔", "§7提供高级头部保护", 500));
        armor.setItem(20, createShopItem(Material.DIAMOND_CHESTPLATE, "§b钻石胸甲", "§7提供高级身体保护", 800));
        armor.setItem(21, createShopItem(Material.DIAMOND_LEGGINGS, "§b钻石护腿", "§7提供高级腿部保护", 700));
        armor.setItem(22, createShopItem(Material.DIAMOND_BOOTS, "§b钻石靴子", "§7提供高级脚部保护", 600));

        // 添加下界合金护甲
        armor.setItem(28, createShopItem(Material.NETHERITE_HELMET, "§4下界合金头盔", "§7提供顶级头部保护", 1000));
        armor.setItem(29, createShopItem(Material.NETHERITE_CHESTPLATE, "§4下界合金胸甲", "§7提供顶级身体保护", 1500));
        armor.setItem(30, createShopItem(Material.NETHERITE_LEGGINGS, "§4下界合金护腿", "§7提供顶级腿部保护", 1300));
        armor.setItem(31, createShopItem(Material.NETHERITE_BOOTS, "§4下界合金靴子", "§7提供顶级脚部保护", 1200));

        // 添加特殊护甲
        armor.setItem(37, createShopItem(Material.TURTLE_HELMET, "§a海龟头盔", "§7提供水下呼吸", 300));
        armor.setItem(38, createShopItem(Material.SHIELD, "§7盾牌", "§7提供格挡能力", 250));

        // 添加页面标题
        ItemStack title = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§b§l护甲商店");
        titleMeta.setLore(Arrays.asList("§7购买各种防护装备"));
        title.setItemMeta(titleMeta);
        armor.setItem(45, title);

        return armor;
    }

    // 创建食物页面
    private Inventory createFoodPage(Player player) {
        Inventory food = Bukkit.createInventory(null, 54, "§8食物补给商店");

        // 添加返回按钮
        food.setItem(49, createBackButton());

        // 添加食物
        food.setItem(10, createShopItem(Material.GOLDEN_APPLE, "§6金苹果", "§7提供额外生命值", 50));
        food.setItem(11, createShopItem(Material.COOKED_BEEF, "§6熟牛排", "§7恢复饥饿值", 10));
        food.setItem(12, createShopItem(Material.BAKED_POTATO, "§6烤土豆", "§7恢复中等饥饿值", 5));
        food.setItem(13, createShopItem(Material.COOKED_CHICKEN, "§6烤鸡肉", "§7恢复少量饥饿值", 4));
        food.setItem(14, createShopItem(Material.PUMPKIN_PIE, "§6南瓜派", "§7恢复大量饥饿值", 8));
        food.setItem(15, createShopItem(Material.COOKED_COD, "§6熟鳕鱼", "§7恢复中等饥饿值", 6));
        food.setItem(16, createShopItem(Material.COOKED_SALMON, "§6熟鲑鱼", "§7恢复较多饥饿值", 9));

        food.setItem(19, createShopItem(Material.MUSHROOM_STEW, "§6蘑菇煲", "§7恢复全部饥饿值", 15));
        food.setItem(20, createShopItem(Material.RABBIT_STEW, "§6兔肉煲", "§7恢复大量饥饿值", 20));
        food.setItem(21, createShopItem(Material.BEETROOT_SOUP, "§6甜菜汤", "§7恢复中等饥饿值", 12));
        food.setItem(22, createShopItem(Material.SUSPICIOUS_STEW, "§6迷之炖菜", "§7随机增益效果", 30));
        food.setItem(23, createShopItem(Material.CAKE, "§d蛋糕", "§7可分多次食用", 25));

        // 添加特殊食物
        food.setItem(28, createShopItem(Material.ENCHANTED_GOLDEN_APPLE, "§6附魔金苹果", "§7提供强大增益效果", 500));
        food.setItem(29, createShopItem(Material.HONEY_BOTTLE, "§6蜂蜜瓶", "§7清除中毒效果", 40));
        food.setItem(30, createShopItem(Material.MILK_BUCKET, "§f牛奶桶", "§7清除所有效果", 50));

        // 添加页面标题
        ItemStack title = new ItemStack(Material.COOKED_BEEF);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§e§l食物补给");
        titleMeta.setLore(Arrays.asList("§7购买各种食物补给"));
        title.setItemMeta(titleMeta);
        food.setItem(45, title);

        return food;
    }

    // 创建配件页面
    private Inventory createAttachmentsPage(Player player) {
        Inventory attachments = Bukkit.createInventory(null, 54, "§8武器配件商店");

        // 添加返回按钮
        attachments.setItem(49, createBackButton());

        // 添加配件
        int slot = 0;
        for (GunAttachment attachment : GunAttachment.values()) {
            attachments.setItem(slot, createAttachmentItem(attachment));
            slot++;
            if (slot >= 45)
                break;
        }

        // 添加页面标题
        ItemStack title = new ItemStack(Material.ANVIL);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName("§6§l武器配件");
        titleMeta.setLore(Arrays.asList("§7购买各种武器配件"));
        title.setItemMeta(titleMeta);
        attachments.setItem(45, title);

        return attachments;
    }

    // 创建返回按钮
    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c返回主菜单");
        back.setItemMeta(backMeta);
        return back;
    }

    private ItemStack createShopGun(GunType gunType) {
        ItemStack gun = createGun(gunType);
        ItemMeta meta = gun.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add("§6价格: " + gunType.price + "硬币");
        meta.setLore(lore);
        gun.setItemMeta(meta);
        return gun;
    }

    private ItemStack createShopAmmo(GunType gunType) {
        ItemStack ammo = createAmmoItem(gunType, 1);
        ItemMeta meta = ammo.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add("§6价格: " + gunType.ammoPrice + "硬币/32发");
        meta.setLore(lore);
        ammo.setItemMeta(meta);
        return ammo;
    }

    private ItemStack createShopItem(Material material, String name, String description, int price) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(description);
        lore.add("");
        lore.add("§6价格: " + price + "硬币");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // 添加僵尸攻击事件处理
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 只处理僵尸攻击玩家的情况
        if (event.getDamager() instanceof Zombie && event.getEntity() instanceof Player) {
            Zombie zombie = (Zombie) event.getDamager();
            Player player = (Player) event.getEntity();

            // 获取僵尸类型
            ZombieType type = zombieTypes.get(zombie);
            if (type == null)
                return;

            // 根据僵尸类型应用特殊技能
            applyZombieSkill(zombie, player, type);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) {
            // 松开潜行时移除2倍镜效果
            player.removePotionEffect(PotionEffectType.SLOW);
        }
    }

    // 应用僵尸特殊技能的方法
    private void applyZombieSkill(Zombie zombie, Player player, ZombieType type) {
        Random random = new Random();
        double chance = random.nextDouble();

        switch (type) {
            case NORMAL:
                // 普通僵尸：10%几率造成额外伤害
                if (chance < 0.10) {
                    player.damage(2);
                    player.sendMessage("§c僵尸对你造成了额外伤害!");
                }
                break;

            case FAST:
                // 快速僵尸：20%几率使玩家缓慢
                if (chance < 0.20) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 1));
                    player.sendMessage("§6快速僵尸使你变得缓慢!");
                }
                break;

            case TANK:
                // 重装僵尸：30%几率击退玩家
                if (chance < 0.30) {
                    Vector knockback = player.getLocation().toVector()
                            .subtract(zombie.getLocation().toVector())
                            .normalize()
                            .multiply(1.5);
                    player.setVelocity(knockback);
                    player.sendMessage("§4重装僵尸将你击退!");
                }
                break;

            case BABY:
                // 小僵尸：15%几率偷取食物
                if (chance < 0.15) {
                    ItemStack food = findFood(player);
                    if (food != null) {
                        int amount = Math.min(1, food.getAmount());
                        food.setAmount(food.getAmount() - amount);
                        player.sendMessage("§e小僵尸偷吃了你的食物!");
                    }
                }
                break;

            case HUSK:
                // 尸壳：使玩家获得10秒饥饿效果
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
                if (chance < 0.25) {
                    player.sendMessage("§6尸壳使你感到饥饿!");
                }
                break;

            case DROWNED:
                // 溺尸：20%几率使玩家窒息
                if (chance < 0.20) {
                    player.damage(4); // 直接造成4点伤害，模拟窒息
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    player.sendMessage("§9溺尸让你窒息了！");
                }
                break;

            case POISON:
                // 剧毒僵尸：使玩家中毒5秒
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                if (chance < 0.25) {
                    player.sendMessage("§2剧毒僵尸使你中毒!");
                }
                break;

            case EXPLODER:
                // 自爆僵尸：80%几率自爆
                if (chance < 0.80) {
                    explodeZombie(zombie, player);
                    player.sendMessage("§c自爆僵尸爆炸了!");
                }
                break;

            case ARMORED:
                // 装甲僵尸：10%几率破坏玩家护甲
                if (chance < 0.10) {
                    damagePlayerArmor(player);
                    player.sendMessage("§7装甲僵尸破坏了你的护甲!");
                }
                break;

            case GIANT:
                // 巨型僵尸：20%几率击飞玩家
                if (chance < 0.20) {
                    Vector knockback = player.getLocation().toVector()
                            .subtract(zombie.getLocation().toVector())
                            .normalize()
                            .multiply(2.0)
                            .setY(0.8);
                    player.setVelocity(knockback);
                    player.sendMessage("§4巨型僵尸将你击飞!");
                }
                break;

            case BOSS:
                // 僵尸王：30%几率造成虚弱效果
                if (chance < 0.30) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
                    player.sendMessage("§4僵尸王使你变得虚弱!");
                }
                break;

            default:
                // 默认无特殊技能
                break;
        }
    }

    // 自爆僵尸爆炸效果（优化版）
    private void explodeZombie(Zombie zombie, Player target) {
        Location loc = zombie.getLocation();

        // 自爆预警效果
        zombie.getWorld().spawnParticle(Particle.LAVA, loc, 20, 0.5, 0.5, 0.5);
        zombie.getWorld().playSound(loc, Sound.BLOCK_LAVA_POP, 1.0f, 1.0f);

        // 延迟爆炸
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid())
                    return;

                createExplosion(zombie.getLocation(), 4.0, 15.0, zombie);
                zombie.remove();
                zombieTypes.remove(zombie);
            }
        }.runTaskLater(this, 20); // 1秒后爆炸
    }

    // 通用爆炸方法
    private void createExplosion(Location loc, double radius, double maxDamage, Entity source) {
        World world = loc.getWorld();
        if (world == null)
            return;

        // 播放爆炸效果
        world.spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // 获取附近实体
        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity) || entity == source)
                continue;

            double distanceSquared = entity.getLocation().distanceSquared(loc);
            if (distanceSquared > radiusSquared)
                continue;

            // 计算伤害（线性衰减）
            double distance = Math.sqrt(distanceSquared);
            double damage = maxDamage * (1 - distance / radius);
            ((LivingEntity) entity).damage(damage);

            // 添加击退效果
            Vector direction = entity.getLocation().toVector().subtract(loc.toVector()).normalize();
            entity.setVelocity(direction.multiply(0.5));
        }
    }

    // 寻找玩家背包中的食物
    private ItemStack findFood(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().isEdible()) {
                return item;
            }
        }
        return null;
    }

    // 破坏玩家护甲
    private void damagePlayerArmor(Player player) {
        PlayerInventory inv = player.getInventory();

        // 随机选择一件护甲破坏
        List<ItemStack> armorPieces = new ArrayList<>();
        if (inv.getHelmet() != null)
            armorPieces.add(inv.getHelmet());
        if (inv.getChestplate() != null)
            armorPieces.add(inv.getChestplate());
        if (inv.getLeggings() != null)
            armorPieces.add(inv.getLeggings());
        if (inv.getBoots() != null)
            armorPieces.add(inv.getBoots());

        if (!armorPieces.isEmpty()) {
            ItemStack armorToDamage = armorPieces.get(new Random().nextInt(armorPieces.size()));
            damageArmorPiece(armorToDamage);
        }
    }

    // 破坏单个护甲片
    private void damageArmorPiece(ItemStack armor) {
        // 如果护甲有耐久度
        if (armor.getItemMeta() instanceof Damageable) {
            Damageable meta = (Damageable) armor.getItemMeta();
            int maxDurability = armor.getType().getMaxDurability();
            int currentDamage = meta.getDamage();

            // 随机增加10-30%的耐久损失
            int damageToAdd = (int) (maxDurability * (0.1 + Math.random() * 0.2));
            meta.setDamage(Math.min(maxDurability - 1, currentDamage + damageToAdd));
            armor.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null)
            return;

        // 新增：抑制望远镜功能
        if (item.getType() == Material.SPYGLASS &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // 检查是否是配件
            NamespacedKey key = new NamespacedKey(this, "attachment_id");
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                    player.sendMessage("§b安装在枪上然后潜行+左键来使用2倍镜");
                    return;
                }
            }
        }

        // 检查无限弹药状态
        boolean infiniteAmmo = isInfiniteAmmoActive(player);

        // 获取枪支类型
        GunType gunType = materialToGunType.get(item.getType());
        if (gunType != null) {
            NamespacedKey key = new NamespacedKey(this, "gun_id");
            ItemMeta meta = item.getItemMeta();
            if (meta == null)
                return;

            // 获取枪支ID
            byte[] gunIdBytes = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
            if (gunIdBytes == null)
                return;

            UUID gunId = bytesToUuid(gunIdBytes);
            GunData gunData = playerGuns.get(gunId.toString());
            if (gunData == null)
                return;

            // 2倍镜处理（保持不变）
            if (player.isSneaking() &&
                    (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
                Set<GunAttachment> attachments = playerAttachments.get(player.getName());
                if (attachments != null && attachments.contains(GunAttachment.SCOPE_2X)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, -1, 4, false, false));
                    player.sendMessage("§b2倍镜已开启，松开潜行键恢复正常视野");
                    event.setCancelled(true);
                    return;
                }
            }

            // 只处理右键射击
            if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                return;
            }

            // 无限弹药时直接射击
            if (infiniteAmmo) {
                shootGun(player, gunType, gunData, item);
                event.setCancelled(true);
                return;
            }
            // 2倍镜：潜行+左键触发
            if (player.isSneaking()
                    && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
                Set<GunAttachment> attachments = playerAttachments.get(player.getName());
                if (attachments != null && attachments.contains(GunAttachment.SCOPE_2X)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, -1, 4, false, false)); // 视野拉近
                    player.sendMessage("§b2倍镜已开启，松开潜行键恢复正常视野");
                    event.setCancelled(true);
                    return;
                }
            }
            // 只允许右键射击
            if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                return;
            }
            if (gunId == null) {
                // 如果枪支ID不存在，创建新的枪支数据
                gunId = UUID.randomUUID();
                gunData = new GunData(gunType, gunType.clipSize, gunId);
                playerGuns.put(gunId.toString(), gunData);

                // 保存枪支ID到物品
                meta.getPersistentDataContainer().set(key, UUIDDataType.INSTANCE, gunId);
                item.setItemMeta(meta);
            } else {
                // 获取
                gunData = playerGuns.get(gunId.toString());
                // 保存
                playerGuns.put(gunId.toString(), gunData);
            }

            // 检查是否在换弹冷却中
            if (reloadCooldowns.containsKey(player.getName())) {
                long cooldown = reloadCooldowns.get(player.getName());
                if (System.currentTimeMillis() < cooldown) {
                    player.sendMessage("§c正在装弹中，请稍候...");
                    return;
                } else {
                    reloadCooldowns.remove(player.getName());
                }
            }

            // 检查弹药
            if (gunData.ammo <= 0) {
                // 开始换弹
                reloadGun(player, gunType, gunData, gunId);
                event.setCancelled(true);
                return;
            }
            if (gunData.ammo < 0)
                gunData.ammo = 0; // 防止负数

            shootGun(player, gunType, gunData, item);
            event.setCancelled(true);

            Set<GunAttachment> attachments = playerAttachments.get(player.getName());

        }
    }

    // 更新枪支的Lore显示配件信息
    private void updateGunLore(Player player, ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null)
            lore = new ArrayList<>();

        // 移除旧的配件信息
        lore.removeIf(line -> line.startsWith("§7配件:") || line.startsWith("§8-"));

        // 添加新配件信息
        Set<GunAttachment> attachments = playerAttachments.get(player.getName());
        if (attachments != null && !attachments.isEmpty()) {
            lore.add("§7配件:");
            for (GunAttachment attachment : attachments) {
                lore.add("§8- " + attachment.displayName);
            }
        }

        // 更新弹药显示
        NamespacedKey key = new NamespacedKey(this, "gun_id");
        byte[] gunIdBytes = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (gunIdBytes != null) {
            UUID gunId = bytesToUuid(gunIdBytes);
            GunData gunData = playerGuns.get(gunId.toString());
            if (gunData != null) {
                // 检查无限弹药状态
                boolean infiniteAmmo = player.hasMetadata("InfiniteAmmo") &&
                        player.getMetadata("InfiniteAmmo").get(0).asLong() > System.currentTimeMillis();

                // 找到弹药显示行
                int ammoLineIndex = -1;
                for (int i = 0; i < lore.size(); i++) {
                    if (lore.get(i).startsWith("§7弹药:")) {
                        ammoLineIndex = i;
                        break;
                    }
                }

                // 更新或添加弹药显示
                String ammoText;
                if (infiniteAmmo) {
                    ammoText = "§7弹药: §a∞ §7(无限)";
                } else {
                    ammoText = "§7弹药: " + gunData.ammo + "/" + gunData.getClipSize(player);
                }

                if (ammoLineIndex != -1) {
                    lore.set(ammoLineIndex, ammoText);
                } else {
                    lore.add(1, ammoText); // 在第二行添加
                }
            }
        }

        meta.setLore(lore);
        gun.setItemMeta(meta);
    }

    // 右键村民打开商店
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().equals(shopVillager)) {
            openShopPage(event.getPlayer(), ShopPage.MAIN_MENU);
            event.setCancelled(true);
        }
    }

    // 玩家加入游戏时自动开始
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 确保玩家有数据（保留硬币）
        if (!playerDataMap.containsKey(player.getName())) {
            playerDataMap.put(player.getName(), new PlayerData());
        }

        // 重置玩家状态
        resetPlayer(player);
        player.teleport(getSafeSpawnPoint());

        // 修复：移除死亡状态
        deadPlayers.remove(player.getName());

        // 修改：只要有1个及以上玩家就自动开始
        if (gameState == GameState.WAITING && Bukkit.getOnlinePlayers().size() >= 1) {
            startGame();
        }
        // 如果游戏正在进行中，给予起始物品
        else if (gameState == GameState.ACTIVE) {
            giveStartingItems(player);
            player.sendMessage("§a你已加入正在进行的僵尸生存游戏! 硬币: " + playerDataMap.get(player.getName()).getCoins());
        }
        updateTab(event.getPlayer());
    }

    // 玩家退出游戏处理
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // 从死亡列表中移除
        deadPlayers.remove(playerName);

        // 保存玩家数据
        savePlayerData();

        // 检查游戏是否应该结束
        updateGameState();
    }

    // 子弹计数辅助方法
    private int countAmmo(Player player, GunType gunType) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == gunType.ammoType) {
                // 检查是否是正确的弹药类型
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                        item.getItemMeta().getDisplayName().equals(gunType.ammoName)) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    // 移除子弹辅助方法
    private void removeAmmo(Player player, GunType gunType, int amount) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (amount <= 0)
                break;
            if (item != null && item.getType() == gunType.ammoType) {
                // 检查是否是正确的弹药类型
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                        item.getItemMeta().getDisplayName().equals(gunType.ammoName)) {
                    int take = Math.min(amount, item.getAmount());
                    item.setAmount(item.getAmount() - take);
                    amount -= take;
                }
            }
        }
    }

    private void reloadGun(Player player, GunType gunType, GunData gunData, UUID gunId) {
        // 检查是否有足够的弹药
        int ammoAvailable = countAmmo(player, gunType);
        if (ammoAvailable < 1) {
            player.sendMessage("§c没有" + gunType.ammoTypeName + "了!");
            return;
        }

        // 防止多次装弹
        if (reloadCooldowns.containsKey(player.getName())) {
            player.sendMessage("§c已经在装弹中!");
            return;
        }

        player.sendMessage("§e正在装弹...");
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1.0f, 1.0f);

        // 计算装弹时间（应用快速装弹配件）
        int reloadTime = gunType.reloadTime;
        Set<GunAttachment> attachments = playerAttachments.get(player.getName());
        if (attachments != null && attachments.contains(GunAttachment.FAST_RELOAD)) {
            reloadTime = Math.max(1, reloadTime / 2); // 最少1秒
        }

        // 设置换弹冷却
        reloadCooldowns.put(player.getName(), System.currentTimeMillis() + (reloadTime * 1000));

        // 计算弹夹容量（应用扩容弹夹配件）
        int clipSize = gunData.getClipSize(player);

        // 换弹完成后补充弹药
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    int bulletsToTake = Math.min(clipSize, ammoAvailable);
                    removeAmmo(player, gunType, bulletsToTake);
                    gunData.ammo = bulletsToTake;
                    playerGuns.put(gunId.toString(), gunData);
                    player.sendMessage("§a装弹完成! 弹药: " + gunData.ammo + "/" + clipSize);
                    player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 1.0f);

                    // 更新枪支显示
                    ItemStack gun = player.getInventory().getItemInMainHand();
                    if (gun != null && gun.getType() == gunType.material) {
                        ItemMeta meta = gun.getItemMeta();
                        List<String> lore = meta.getLore();
                        if (lore != null && lore.size() > 1) {
                            lore.set(1, "§7弹药: " + gunData.ammo + "/" + clipSize);
                            meta.setLore(lore);
                            gun.setItemMeta(meta);
                        }
                    }
                    reloadCooldowns.remove(player.getName()); // 移除冷却
                }
            }
        }.runTaskLater(this, reloadTime * 20);
    }

    private void shootGun(Player player, GunType gunType, GunData gunData, ItemStack gunItem) {
        // 检查无限弹药状态
        boolean infiniteAmmo = isInfiniteAmmoActive(player);

        // 无限弹药时跳过弹药检查
        if (!infiniteAmmo && gunData.ammo <= 0) {
            player.sendMessage("§c弹药耗尽! 按R键装弹");
            return;
        }

        // 应用配件效果
        Set<GunAttachment> attachments = playerAttachments.get(player.getName());
        double damageMultiplier = 1.0;
        double spread = 0.02;
        int reloadTime = gunType.reloadTime;

        if (attachments != null) {
            for (GunAttachment attachment : attachments) {
                damageMultiplier += attachment.damageBoost;
                if (attachment == GunAttachment.RECOIL_PAD)
                    spread -= 0.012;
                if (attachment == GunAttachment.GRIP)
                    spread -= 0.008;
                if (attachment == GunAttachment.SCOPE_2X)
                    spread -= 0.012;
                if (attachment == GunAttachment.FAST_RELOAD)
                    reloadTime = Math.max(1, reloadTime / 2);
            }
            if (spread < 0.003)
                spread = 0.003;
        }

        double finalDamage = gunType.damage * damageMultiplier;

        // 计算射线方向
        Vector dir = player.getEyeLocation().getDirection();
        Random rand = new Random();
        dir.add(new Vector(
                (rand.nextDouble() - 0.5) * spread,
                (rand.nextDouble() - 0.5) * spread,
                (rand.nextDouble() - 0.5) * spread)).normalize();

        // 射线检测
        double range = 50.0;
        Location start = player.getEyeLocation();
        Location end = start.clone().add(dir.multiply(range));
        RayTraceResult result = player.getWorld().rayTraceEntities(start, dir, range,
                entity -> entity instanceof Zombie && !entity.equals(player));

        // 命中处理
        if (result != null && result.getHitEntity() instanceof Zombie) {
            Zombie zombie = (Zombie) result.getHitEntity();
            ZombieType zType = zombieTypes.get(zombie);
            boolean hasLaser = attachments != null && attachments.contains(GunAttachment.LASER_SIGHT);
            boolean hasArmorPiercer = attachments != null && attachments.contains(GunAttachment.ARMOR_PIERCER);

            double damage = finalDamage;
            if (hasLaser && Math.random() < 0.10) {
                damage *= 2;
                player.sendMessage("§b激光指示器暴击！造成双倍伤害！");
            }
            if (hasArmorPiercer && zType == ZombieType.ARMORED) {
                damage *= 1.5;
                player.sendMessage("§8穿甲弹对装甲僵尸造成额外伤害！");
            }
            zombie.damage(damage, player);

            // 特殊枪械效果
            switch (gunType) {
                case ELECTRIC_SHOTGUN:
                    zombie.getWorld().strikeLightningEffect(zombie.getLocation());
                    for (Entity nearby : zombie.getNearbyEntities(3, 3, 3)) {
                        if (nearby instanceof Zombie)
                            ((Zombie) nearby).damage(5);
                    }
                    break;
                case ICE_SNIPER:
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 4));
                    zombie.getWorld().spawnParticle(Particle.SNOW_SHOVEL, zombie.getLocation(), 20, 0.5, 0.5, 0.5, 0);
                    break;
                case FLAME_PISTOL:
                    zombie.setFireTicks(100);
                    break;
            }

        }

        // 后坐力执行
        applyRecoil(player, gunType, attachments);

        // 弹药消耗
        if (!infiniteAmmo) {
            gunData.ammo--;
            if (gunData.ammo < 0)
                gunData.ammo = 0;
        }

        // 确保枪口位置正确
        Location muzzleLocation = getMuzzleLocation(player);

        // 播放射击音效
        float volume = 1.0f;

        // 更新物品栏显示弹药
        if (gunItem != null && gunItem.getType() == gunType.material) {
            updateGunLore(player, gunItem);
        }

        // 更新弹药经验条
        updateAmmoBar(player);

        // 更新计分板
        updateScoreboard(player);

        // 确定弹道终点
        Location endPoint;
        if (result != null && result.getHitEntity() != null) {
            endPoint = result.getHitEntity().getLocation();
        } else {
            endPoint = start.clone().add(dir.multiply(range));
        }

        // 显示弹道
        showBulletTrail(start, endPoint);
    }

    // 显示弹道粒子效果
    private void showBulletTrail(Location from, Location to) {
        World world = from.getWorld();
        if (world == null)
            return;

        // 计算实际显示距离（限制最大50格）
        double distance = Math.min(from.distance(to), 50.0);
        Vector direction = to.toVector().subtract(from.toVector()).normalize();

        // 粒子起始点前移1格（更符合枪口位置）
        Location start = from.clone().add(direction.clone().multiply(1));
        Location actualEnd = start.clone().add(direction.clone().multiply(distance));

        // 使用白色粒子
        Particle.DustOptions dust = new Particle.DustOptions(Color.WHITE, 0.7f); // 白色粒子

        // 减少粒子密度（每米5个粒子）
        int particles = (int) (distance * 5);
        double step = distance / particles;

        // 生成弹道粒子
        for (int i = 0; i <= particles; i++) {
            double progress = i * step;
            Location point = start.clone().add(direction.clone().multiply(progress));

            // 白色粒子弹道
            world.spawnParticle(
                    Particle.REDSTONE,
                    point,
                    1, // 每次生成1个粒子
                    0, 0, 0, 0, // 无随机偏移
                    dust);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();

        if (projectile instanceof Snowball && shooter instanceof Player && projectile.hasMetadata("GunBullet")) {
            Player player = (Player) shooter;
            double damage = projectile.getMetadata("GunDamage").get(0).asDouble();
            String gunTypeName = projectile.getMetadata("GunType").get(0).asString();

            GunType gunType = GunType.valueOf(gunTypeName);
            if (gunType == null)
                return;

            Set<GunAttachment> attachments = playerAttachments.getOrDefault(player.getName(), new HashSet<>());
            boolean hasLaser = attachments.contains(GunAttachment.LASER_SIGHT);
            boolean hasArmorPiercer = attachments.contains(GunAttachment.ARMOR_PIERCER);

            if (event.getHitEntity() instanceof Zombie) {
                Zombie zombie = (Zombie) event.getHitEntity();
                ZombieType zType = zombieTypes.get(zombie);

                // 激光指示器暴击：10%几率造成2倍伤害
                if (hasLaser && Math.random() < 0.10) {

                    damage *= 2;
                    player.sendMessage("§b激光指示器暴击！造成双倍伤害！");
                }

                // 穿甲弹对装甲僵尸增伤
                if (hasArmorPiercer && zType == ZombieType.ARMORED) {
                    damage *= 1.5;
                    player.sendMessage("§8穿甲弹对装甲僵尸造成额外伤害！");
                }

                zombie.damage(damage, player);
                // 特殊效果
                switch (gunType) {
                    case ELECTRIC_SHOTGUN:
                        // 电击枪：召唤闪电
                        zombie.getWorld().strikeLightningEffect(zombie.getLocation());
                        for (Entity nearby : zombie.getNearbyEntities(3, 3, 3)) {
                            if (nearby instanceof Zombie) {
                                ((Zombie) nearby).damage(5);
                            }
                        }
                        break;
                    case ICE_SNIPER:
                        // 冰冻狙击枪：冻结僵尸3秒
                        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 4));
                        zombie.getWorld().spawnParticle(Particle.SNOW_SHOVEL, zombie.getLocation(), 20, 0.5, 0.5, 0.5,
                                0);
                        break;
                    case FLAME_PISTOL:
                        // 火焰手枪：点燃僵尸
                        zombie.setFireTicks(100);
                        break;
                }

                projectile.remove();
            }
        }
    }

    // 改进的生物生成抑制
    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();

        // 允许插件生成的僵尸和村民
        if (zombieTypes.containsKey(entity) || entity.equals(shopVillager)) {
            return;
        }

        // 只允许生成僵尸
        if (event.getEntity() instanceof Zombie) {
            Zombie zombie = (Zombie) event.getEntity();
            // 随机设置僵尸类型（普通或快速）
            ZombieType type = new Random().nextBoolean() ? ZombieType.NORMAL : ZombieType.FAST;
            zombieTypes.put(zombie, type);
        } else {
            // 抑制所有非僵尸生物生成
            event.setCancelled(true);
        }
    }

    private void updateZombieNames() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastZombieNameUpdate < 500)
            return;
        lastZombieNameUpdate = currentTime;

        for (Entity entity : zombieTypes.keySet()) {
            if (entity instanceof Zombie) {
                Zombie zombie = (Zombie) entity;
                ZombieType type = zombieTypes.get(zombie);

                if (type != null && !zombie.isDead()) {
                    double maxHealth = Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH))
                            .getValue();
                    double healthPercent = (zombie.getHealth() / maxHealth) * 100.0;
                    String healthBar = createHealthBar(healthPercent);
                    zombie.setCustomName(
                            "§c" + type.name + " §7[" + healthBar + "§7] §4" + (int) zombie.getHealth() + "❤");
                    zombie.setCustomNameVisible(true);
                }
            }
        }
    }

    private String createHealthBar(double percent) {
        int filled = (int) (percent / 10);
        int empty = 10 - filled;

        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) {
            bar.append("|");
        }

        bar.append("§c");
        for (int i = 0; i < empty; i++) {
            bar.append("|");
        }

        return bar.toString();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof Zombie && zombieTypes.containsKey(entity)) {
            ZombieType type = zombieTypes.get(entity);
            Player killer = entity.getKiller();
            // 清除僵尸掉落物和经验
            event.getDrops().clear();
            event.setDroppedExp(0);

            if (killer != null) {
                PlayerData data = playerDataMap.get(killer.getName());
                if (data != null) {
                    // 给予玩家硬币奖励
                    int coins = type.coinReward;

                    // 双倍硬币事件
                    if (currentEvent == RandomEvent.DOUBLE_COINS) {
                        coins *= 2;
                    }

                    data.addCoins(coins);
                    killer.sendMessage("§a+ " + coins + " 硬币!");

                    // 成就统计
                    String playerName = killer.getName();
                    int killCount = data.getZombieKills() + 1;
                    data.setZombieKills(killCount);

                    // 授予成就
                    if (killCount >= 50)
                        grantAchievement(killer, Achievement.ZOMBIE_SLAYER);
                    if (killCount >= 100)
                        grantAchievement(killer, Achievement.ZOMBIE_EXTERMINATOR);

                    // 特殊僵尸成就
                    if (type == ZombieType.EXPLODER) {
                        grantAchievement(killer, Achievement.BOMB_DISPOSAL);
                    } else if (type == ZombieType.BOSS) {
                        grantAchievement(killer, Achievement.BOSS_SLAYER);
                    }

                    // 更新计分板
                    updateScoreboard(killer);
                }
            }

            // 僵尸掉落物
            if (new Random().nextInt(100) < 30) // 30%几率掉落
            {
                Material drop = ZOMBIE_DROPS[new Random().nextInt(ZOMBIE_DROPS.length)];
                entity.getWorld().dropItemNaturally(entity.getLocation(), new ItemStack(drop));
            }

            // 移除僵尸记录
            zombieTypes.remove(entity);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = playerDataMap.get(player.getName());

        if (data != null) {
            deadPlayers.add(player.getName());
            event.setDeathMessage("§c" + player.getName() + " 被僵尸杀死了!");
        }

        // 死亡后切换为旁观者
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage("§e你已死亡，进入观战模式，请等待下一局！");
                }
            }
        }.runTaskLater(this, 1L);
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 只做基础重置，玩家手动点重生后才执行
        player.getInventory().clear();
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setExp(0);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.getInventory().addItem(new ItemStack(Material.BREAD, 5));
        player.teleport(getSafeSpawnPoint());

        // 不再设置血量

        if (gameState == GameState.ACTIVE) {
            player.sendMessage("§e你已死亡，请观战或等待下一局！");
        } else {
            player.sendMessage("§a新一局即将开始，请准备！");
        }
        updateScoreboard(player); // 新增
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 新增：只处理顶部库存（商店库存）的点击事件
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // 忽略玩家背包的点击
        }
        if (event.getView().getTitle().contains("§8")) {
            event.setCancelled(true);
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        String title = event.getView().getTitle();

        // 确保点击了物品
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        PlayerData data = playerDataMap.get(player.getName());
        if (data == null) {
            return;
        }

        // =====================
        // 配件管理界面处理
        // =====================
        if (title.startsWith("§8配件管理")) {
            event.setCancelled(true);

            // 只处理顶部库存
            if (!event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return;
            }

            // 处理返回按钮
            if (clicked.getType() == Material.OAK_DOOR &&
                    clicked.getItemMeta().getDisplayName().equals("§c返回商店")) {
                openShopPage(player, ShopPage.ATTACHMENTS);
                return;
            }

            // 处理配件安装/卸载
            for (GunAttachment att : GunAttachment.values()) {
                if (clicked.getType() == att.material &&
                        clicked.getItemMeta().getDisplayName().contains(att.displayName)) {

                    Set<GunAttachment> installed = playerAttachments.getOrDefault(player.getName(), new HashSet<>());

                    // 已安装，点击卸载
                    if (installed.contains(att)) {
                        installed.remove(att);
                        playerAttachments.put(player.getName(), installed);

                        // 将配件返回到玩家背包
                        player.getInventory().addItem(createAttachmentItem(att));
                        player.sendMessage("§a已卸载配件: " + att.displayName);

                        // 更新显示
                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        if (mainHand != null) {
                            updateGunLore(player, mainHand);
                            updateAmmoBar(player);
                        }

                        // 刷新配件管理界面
                        openAttachmentManager(player);
                    }
                    // 未安装，尝试安装
                    else {
                        // 检查背包中是否有该配件
                        if (hasAttachmentInInventory(player, att)) {
                            // 检查同类槽位是否已有配件
                            boolean slotConflict = false;
                            for (GunAttachment ins : installed) {
                                if (ins.slot == att.slot) {
                                    slotConflict = true;
                                    player.sendMessage("§c已安装同类配件: " + ins.displayName);
                                    player.sendMessage("§c请先卸载同类配件！");
                                    break;
                                }
                            }

                            if (slotConflict) {
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            } else {
                                // 从背包中移除配件
                                if (removeAttachmentFromInventory(player, att)) {
                                    installed.add(att);
                                    playerAttachments.put(player.getName(), installed);
                                    player.sendMessage("§a已安装配件: " + att.displayName);
                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f,
                                            1.0f);

                                    // 更新显示
                                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                                    if (mainHand != null) {
                                        updateGunLore(player, mainHand);
                                        updateAmmoBar(player);
                                    }

                                    // 刷新配件管理界面
                                    openAttachmentManager(player);
                                }
                            }
                        } else {
                            player.sendMessage("§c背包中没有该配件！");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                    }
                    return;
                }
            }
        }

        // =====================
        // 商店页面处理
        // =====================

        // 处理返回按钮（所有商店页面）
        if (clicked.getType() == Material.BARRIER && clicked.getItemMeta().getDisplayName().equals("§c返回主菜单")) {
            openShopPage(player, ShopPage.MAIN_MENU);
            return;
        }

        // 处理主菜单导航
        if (title.equals("§8商店主菜单")) {
            event.setCancelled(true);
            switch (clicked.getType()) {
                case DIAMOND_SWORD:
                    openShopPage(player, ShopPage.WEAPONS);
                    break;
                case ARROW:
                    openShopPage(player, ShopPage.AMMO);
                    break;
                case POTION:
                    openShopPage(player, ShopPage.BUFFS);
                    break;
                case DIAMOND_CHESTPLATE:
                    openShopPage(player, ShopPage.ARMOR);
                    break;
                case COOKED_BEEF:
                    openShopPage(player, ShopPage.FOOD);
                    break;
                case ANVIL:
                    openShopPage(player, ShopPage.ATTACHMENTS);
                    break;
            }
            return;
        }

        // 处理武器购买
        for (GunType gunType : GunType.values()) {
            if (clicked.getType() == gunType.material) {
                event.setCancelled(true);
                if (data.getCoins() >= gunType.price) {
                    data.addCoins(-gunType.price);
                    player.getInventory().addItem(createGun(gunType));
                    player.sendMessage("§a已购买 " + gunType.displayName + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } else {
                    player.sendMessage("§c硬币不足!");
                }
                return;
            }
        }

        // 处理弹药购买
        for (GunType gunType : GunType.values()) {
            if (clicked.getType() == gunType.ammoType &&
                    clicked.getItemMeta().getDisplayName().equals(gunType.ammoName)) {
                event.setCancelled(true);
                if (data.getCoins() >= gunType.ammoPrice) {
                    data.addCoins(-gunType.ammoPrice);
                    player.getInventory().addItem(createAmmoItem(gunType, 32));
                    player.sendMessage("§a已购买 32发" + gunType.ammoTypeName + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } else {
                    player.sendMessage("§c硬币不足!");
                }
                return;
            }
        }

        // 处理配件购买（仅在配件商店页面）
        if (title.equals("§8武器配件商店")) {
            event.setCancelled(true);
            for (GunAttachment attachment : GunAttachment.values()) {
                if (clicked.getType() == attachment.material &&
                        clicked.getItemMeta().getDisplayName().equals(attachment.displayName)) {
                    if (data.getCoins() >= attachment.price) {
                        data.addCoins(-attachment.price);
                        player.getInventory().addItem(createAttachmentItem(attachment));
                        player.sendMessage("§a已购买 " + attachment.displayName + "!");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    } else {
                        player.sendMessage("§c硬币不足!");
                    }
                    return;
                }
            }
        }

        // 处理增益效果购买
        if (title.equals("§8增益效果商店")) {
            event.setCancelled(true);
            if (clicked.getType() == Material.GOLDEN_APPLE && clicked.getItemMeta().getDisplayName().equals("§a生命提升")) {
                if (data.getCoins() >= 200) {
                    data.addCoins(-200);
                    Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(30);
                    player.setHealth(30);
                    player.sendMessage("§a最大生命值提升至30点!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.IRON_CHESTPLATE
                    && clicked.getItemMeta().getDisplayName().equals("§a伤害减免")) {
                if (data.getCoins() >= 250) {
                    data.addCoins(-250);
                    player.addPotionEffect(
                            new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20 * 60 * 5, 0, false, false));
                    player.sendMessage("§a获得5分钟伤害减免效果!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.POTION
                    && clicked.getItemMeta().getDisplayName().equals("§a速度提升")) {
                if (data.getCoins() >= 150) {
                    data.addCoins(-150);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 3, 1, false, false));
                    player.sendMessage("§a获得3分钟速度提升效果!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.FIREWORK_ROCKET
                    && clicked.getItemMeta().getDisplayName().equals("§a跳跃提升")) {
                if (data.getCoins() >= 120) {
                    data.addCoins(-120);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 20 * 60 * 3, 1, false, false));
                    player.sendMessage("§a获得3分钟跳跃提升效果!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.GOLDEN_CARROT
                    && clicked.getItemMeta().getDisplayName().equals("§6夜视效果")) {
                if (data.getCoins() >= 100) {
                    data.addCoins(-100);
                    player.addPotionEffect(
                            new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 60 * 10, 0, false, false));
                    player.sendMessage("§a获得10分钟夜视效果!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.TOTEM_OF_UNDYING
                    && clicked.getItemMeta().getDisplayName().equals("§c保命图腾")) {
                if (data.getCoins() >= 1000) {
                    data.addCoins(-1000);
                    player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING));
                    player.sendMessage("§a已购买保命图腾!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.BLAZE_POWDER
                    && clicked.getItemMeta().getDisplayName().equals("§6火焰抗性")) {
                if (data.getCoins() >= 180) {
                    data.addCoins(-180);
                    player.addPotionEffect(
                            new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 5, 0, false, false));
                    player.sendMessage("§a获得5分钟火焰抗性效果!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.FIRE_CHARGE
                    && clicked.getItemMeta().getDisplayName().equals("§e无限弹药")) {
                if (data.getCoins() >= 400) {
                    data.addCoins(-400);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60, 0, false, false)); // 仅作标记
                    player.setMetadata("InfiniteAmmo",
                            new FixedMetadataValue(this, System.currentTimeMillis() + 60000));
                    player.sendMessage("§e你获得了60秒无限弹药！");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            }
            return;
        }

        // 处理护甲购买 - 铁套
        if (title.equals("§8护甲商店")) {
            event.setCancelled(true);
            if (clicked.getType() == Material.IRON_HELMET
                    && clicked.getItemMeta().getDisplayName().equals("§a铁头盔")) {
                if (data.getCoins() >= 100) {
                    data.addCoins(-100);
                    player.getInventory().addItem(new ItemStack(Material.IRON_HELMET));
                    player.sendMessage("§a已购买铁头盔!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.IRON_CHESTPLATE
                    && clicked.getItemMeta().getDisplayName().equals("§a铁胸甲")) {
                if (data.getCoins() >= 150) {
                    data.addCoins(-150);
                    player.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE));
                    player.sendMessage("§a已购买铁胸甲!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.IRON_LEGGINGS
                    && clicked.getItemMeta().getDisplayName().equals("§a铁护腿")) {
                if (data.getCoins() >= 120) {
                    data.addCoins(-120);
                    player.getInventory().addItem(new ItemStack(Material.IRON_LEGGINGS));
                    player.sendMessage("§a已购买铁护腿!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.IRON_BOOTS
                    && clicked.getItemMeta().getDisplayName().equals("§a铁靴子")) {
                if (data.getCoins() >= 80) {
                    data.addCoins(-80);
                    player.getInventory().addItem(new ItemStack(Material.IRON_BOOTS));
                    player.sendMessage("§a已购买铁靴子!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            }
            return;
        }

        // 处理食物购买
        if (title.equals("§8食物补给商店")) {
            event.setCancelled(true);
            if (clicked.getType() == Material.GOLDEN_APPLE
                    && clicked.getItemMeta().getDisplayName().equals("§6金苹果")) {
                if (data.getCoins() >= 50) {
                    data.addCoins(-50);
                    player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 3));
                    player.sendMessage("§a已购买3个金苹果!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            } else if (clicked.getType() == Material.COOKED_BEEF
                    && clicked.getItemMeta().getDisplayName().equals("§6熟牛排")) {
                if (data.getCoins() >= 10) {
                    data.addCoins(-10);
                    player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 8));
                    player.sendMessage("§a已购买8个熟牛排!");
                } else {
                    player.sendMessage("§c硬币不足!");
                }
            }
            // ... 其他食物购买处理 ...
            return;
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // 防止商店村民受到伤害
        if (event.getEntity().equals(shopVillager)) {
            event.setCancelled(true);
        }

        // 防止玩家之间的PVP
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;
            if (damageEvent.getEntity() instanceof Player &&
                    damageEvent.getDamager() instanceof Player) {
                event.setCancelled(true);
            }
        }

        // 防止爆炸刷怪
        if (event.getCause() == DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        // 防止僵尸以商店村民为目标
        if (event.getTarget() != null && event.getTarget().equals(shopVillager)) {
            event.setCancelled(true);
        }
    }

    private void startZombieSpawner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.ACTIVE) {
                    cancel();
                    return;
                }

                // 根据波数调整僵尸数量和类型
                int zombiesToSpawn = 5 + wave;
                int playerCount = Bukkit.getOnlinePlayers().size();

                if (playerCount > 0) {
                    zombiesToSpawn *= playerCount;
                }

                // 生成僵尸
                for (int i = 0; i < zombiesToSpawn; i++) {
                    spawnZombie();
                }

                // 增加波数
                wave++;

                // 广播波数信息
                Bukkit.broadcastMessage("§c第 §4" + wave + " §c波僵尸来袭!");
                // 更新所有玩家的Tab列表
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateTab(p);
                }
            }
        }.runTaskTimer(this, 20 * 30, 20 * 60);
    }

    private void spawnZombie() {
        if (spawnPoint == null)
            return;

        // 随机选择僵尸类型
        ZombieType[] types = ZombieType.values();
        ZombieType type = types[new Random().nextInt(types.length)];

        // 在出生点附近生成僵尸（圆形分布）
        double angle = Math.random() * Math.PI * 2;
        double distance = 10 + Math.random() * 10;
        Location spawnLoc = spawnPoint.clone().add(
                Math.cos(angle) * distance,
                0,
                Math.sin(angle) * distance);

        // 确保生成在地面上
        spawnLoc.setY(spawnPoint.getWorld().getHighestBlockYAt(spawnLoc) + 1);

        Zombie zombie = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);

        // 设置僵尸属性
        Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(type.health);
        zombie.setHealth(type.health);
        Objects.requireNonNull(zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)).setBaseValue(type.speed);

        // 设置特殊能力
        if (type == ZombieType.FAST) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
        } else if (type == ZombieType.TANK) {
            zombie.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        } else if (type == ZombieType.POISON) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.POISON, Integer.MAX_VALUE, 0, false, false));
        } else if (type == ZombieType.EXPLODER) {
            // 自爆僵尸发光效果
            zombie.setGlowing(true);
        }

        // 给僵尸添加随机帽子（修复：设置头盔不可破坏）
        Material helmet = Material.LEATHER_HELMET;
        ItemStack helmetItem = new ItemStack(helmet);
        ItemMeta helmetMeta = helmetItem.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setUnbreakable(true); // 设置不可破坏
            helmetItem.setItemMeta(helmetMeta);
        }
        zombie.getEquipment().setHelmet(helmetItem);
        zombie.getEquipment().setHelmetDropChance(0.0f); // 防止掉落

        // 记录僵尸类型
        zombieTypes.put(zombie, type);

        // 设置僵尸目标
        setZombieTarget(zombie);
    }

    private void setZombieTarget(Zombie zombie) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!zombie.isValid() || gameState != GameState.ACTIVE) {
                    cancel();
                    return;
                }

                // 寻找最近的玩家
                Player nearest = null;
                double nearestDist = Double.MAX_VALUE;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SURVIVAL &&
                            !deadPlayers.contains(player.getName()) &&
                            !player.equals(shopVillager)) {
                        double dist = player.getLocation().distanceSquared(zombie.getLocation());
                        if (dist < nearestDist) {
                            nearest = player;
                            nearestDist = dist;
                        }
                    }
                }

                if (nearest != null) {
                    zombie.setTarget(nearest);
                }
            }
        }.runTaskTimer(this, 0, 20); // 每秒更新一次目标
    }

    private void updateScoreboard(Player player) {
        PlayerData data = playerDataMap.get(player.getName());
        if (data == null)
            return;

        Scoreboard board = scoreboardManager.getNewScoreboard();
        String title = scoreboardConfig.getString("title", "§6■ §l僵尸生存 §6■");
        Objective obj = board.registerNewObjective("zombieSurvival", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = scoreboardConfig.getStringList("lines");
        int score = lines.size();
        for (String line : lines) {
            // 替换变量
            String replaced = line
                    .replace("{health}", String.valueOf((int) player.getHealth()))
                    .replace("{coins}", String.valueOf(data.getCoins()))
                    .replace("{wave}", String.valueOf(wave))
                    .replace("{kills}", String.valueOf(data.getZombieKills()))
                    .replace("{event}", currentEvent != RandomEvent.NONE ? currentEvent.displayName : "无")
                    .replace("{reset}", resetCountdown > 0 ? String.valueOf(resetCountdown) : "-")
                    .replace("{ammo}", getAmmoDisplay(player))
                    .replace("{infinite}", isInfiniteAmmoActive(player) ? "§a是" : "§c否");

            // 隐藏无用数据：不显示 "-" 或 "无"
            if (replaced.contains("-") || replaced.contains("无")) {
                continue;
            }

            obj.getScore(replaced).setScore(score--);
        }

        player.setScoreboard(board);
    }

    // 获取弹药显示
    private String getAmmoDisplay(Player player) {
        // 优先检查无限弹药状态
        if (isInfiniteAmmoActive(player)) {
            return "∞";
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null) {
            return "-";
        }

        GunType gunType = materialToGunType.get(mainHand.getType());
        if (gunType == null) {
            return "-";
        }

        NamespacedKey key = new NamespacedKey(this, "gun_id");
        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null) {
            return "-";
        }

        byte[] gunIdBytes = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (gunIdBytes != null) {
            UUID gunId = bytesToUuid(gunIdBytes);
            GunData gunData = playerGuns.get(gunId.toString());
            if (gunData != null) {
                return gunData.ammo + "/" + gunData.getClipSize(player);
            }
        }
        return "-";
    }

    private void loadPlayerData() {
        getLogger().info("加载玩家数据...");
        playerDataFile = new File(getDataFolder(), "playerdata.yml");

        try {
            if (!playerDataFile.exists()) {
                playerDataFile.createNewFile();
                playerDataConfig = new YamlConfiguration();
                playerDataConfig.save(playerDataFile);
                getLogger().info("创建新的 playerdata.yml");
            } else {
                playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
            }

            // 加载玩家数据（用玩家名作为key）
            for (String key : playerDataConfig.getKeys(false)) {
                int coins = playerDataConfig.getInt(key + ".coins", 0);
                int kills = playerDataConfig.getInt(key + ".kills", 0);
                PlayerData playerData = new PlayerData(coins);
                playerData.setZombieKills(kills);
                playerDataMap.put(key, playerData);
                getLogger().info("加载玩家 " + key + " 数据: " + coins + " 硬币, " + kills + " 击杀");
            }
        } catch (IOException e) {
            getLogger().severe("加载玩家数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void savePlayerData() {
        if (playerDataConfig == null || playerDataFile == null)
            return;

        try {
            // 确保文件夹存在
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // 清除旧数据
            for (String key : playerDataConfig.getKeys(false)) {
                playerDataConfig.set(key, null);
            }

            // 保存新数据（用玩家名作为key）
            for (Map.Entry<String, PlayerData> entry : playerDataMap.entrySet()) {
                String playerName = entry.getKey();
                PlayerData data = entry.getValue();
                playerDataConfig.set(playerName + ".coins", data.getCoins());
                playerDataConfig.set(playerName + ".kills", data.getZombieKills());
            }

            // 保存到文件
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            getLogger().severe("保存玩家数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    enum GameState {
        WAITING, ACTIVE, END
    }

    static class PlayerData {
        private int coins;
        private int zombieKills;

        public PlayerData() {
            this(0);
        }

        public PlayerData(int coins) {
            this.coins = coins;
            this.zombieKills = 0;
        }

        public int getCoins() {
            return coins;
        }

        public void addCoins(int amount) {
            coins += amount;
        }

        public int getZombieKills() {
            return zombieKills;
        }

        public void setZombieKills(int kills) {
            this.zombieKills = kills;
        }
    }

    class GunData {
        GunType gunType;
        int ammo;
        UUID gunId;
        int clipSize; // 添加这个字段

        public GunData(GunType gunType, int ammo, UUID gunId) {
            this.gunType = gunType;
            this.ammo = ammo;
            this.gunId = gunId;
            this.clipSize = gunType.clipSize; // 初始化为基础容量
        }

        public int getClipSize(Player player) {
            int baseSize = this.clipSize;
            Set<GunAttachment> attachments = playerAttachments.get(player.getName());

            if (attachments != null) {
                for (GunAttachment attachment : attachments) {
                    if (attachment == GunAttachment.EXTENDED_MAG) {
                        baseSize = (int) (baseSize * 1.5);
                    }
                }
            }
            return baseSize;
        }
    }

    enum GunType {
        PISTOL(Material.WOODEN_HOE, "§f手枪", 5, 10, 2.0, 2, 50,
                Material.IRON_NUGGET, "§f手枪弹", "手枪弹药", 10),
        SHOTGUN(Material.STONE_HOE, "§7霰弹枪", 8, 6, 1.5, 3, 100,
                Material.GOLD_NUGGET, "§6霰弹", "霰弹枪弹药", 20),
        RIFLE(Material.IRON_HOE, "§f突击步枪", 7, 30, 3.0, 1, 150,
                Material.IRON_INGOT, "§f步枪弹", "步枪弹药", 15),
        SNIPER(Material.GOLDEN_HOE, "§6狙击枪", 25, 5, 5.0, 4, 200,
                Material.GOLD_INGOT, "§6狙击弹", "狙击枪弹药", 30),
        SMG(Material.DIAMOND_HOE, "§b冲锋枪", 4, 40, 4.0, 1, 180,
                Material.DIAMOND, "§b冲锋枪弹", "冲锋枪弹药", 25),
        LMG(Material.NETHERITE_HOE, "§c轻机枪", 6, 60, 4.5, 5, 250,
                Material.NETHERITE_INGOT, "§c机枪弹", "机枪弹药", 40),
        REVOLVER(Material.WOODEN_AXE, "§e左轮手枪", 12, 6, 2.5, 3, 120,
                Material.COPPER_INGOT, "§e左轮弹", "左轮手枪弹药", 15),
        GRENADE_LAUNCHER(Material.STONE_AXE, "§2榴弹发射器", 20, 3, 1.0, 6, 300,
                Material.FIRE_CHARGE, "§2榴弹", "榴弹", 50),
        FLAMETHROWER(Material.IRON_AXE, "§6火焰喷射器", 3, 100, 1.0, 0, 280,
                Material.BLAZE_POWDER, "§6燃料", "燃料罐", 30),
        RAILGUN(Material.GOLDEN_AXE, "§d电磁炮", 30, 1, 10.0, 8, 400,
                Material.PRISMARINE_CRYSTALS, "§d能量电池", "能量电池", 60),
        CROSSBOW(Material.CROSSBOW, "§a弩", 10, 8, 3.5, 2, 130,
                Material.ARROW, "§a弩箭", "弩箭", 10),
        BOW(Material.BOW, "§9弓", 8, 1, 3.0, 1, 90,
                Material.ARROW, "§9箭矢", "箭矢", 5),
        MINIGUN(Material.DIAMOND_AXE, "§4加特林", 5, 200, 6.0, 10, 500,
                Material.NETHER_STAR, "§4加特林弹链", "加特林弹链", 70),
        LASER_GUN(Material.NETHERITE_AXE, "§5激光枪", 15, 20, 4.0, 0, 350,
                Material.GLOWSTONE_DUST, "§5能量核心", "能量核心", 45),
        ROCKET_LAUNCHER(Material.TRIDENT, "§c火箭筒", 25, 3, 1.5, 7, 450,
                Material.FIREWORK_ROCKET, "§c火箭弹", "火箭弹", 80),
        FLAME_PISTOL(Material.BLAZE_ROD, "§6火焰手枪", 7, 12, 2.2, 2, 180,
                Material.BLAZE_POWDER, "§6火焰弹", "火焰弹药", 25),
        ICE_SNIPER(Material.PACKED_ICE, "§b冰冻狙击枪", 20, 4, 4.5, 5, 350,
                Material.ICE, "§b冰冻弹", "冰冻弹药", 40),
        ELECTRIC_SHOTGUN(Material.LIGHTNING_ROD, "§e电击霰弹枪", 10, 8, 1.8, 3, 300,
                Material.GLOWSTONE_DUST, "§e电击弹", "电击弹药", 35),
        ACID_RIFLE(Material.SLIME_BALL, "§a酸液步枪", 6, 25, 3.2, 2, 220,
                Material.SLIME_BALL, "§a酸液弹", "酸液弹药", 30);

        final Material material;
        final String displayName;
        final double damage;
        final int clipSize;
        final double speed;
        final int reloadTime; // 装弹时间（秒）
        final int price;
        final Material ammoType; // 弹药类型
        final String ammoName; // 弹药显示名称
        final String ammoTypeName; // 弹药类型名称
        final int ammoPrice; // 弹药价格（32发）

        GunType(Material material, String displayName, double damage, int clipSize, double speed, int reloadTime,
                int price,
                Material ammoType, String ammoName, String ammoTypeName, int ammoPrice) {
            this.material = material;
            this.displayName = displayName;
            this.damage = damage;
            this.clipSize = clipSize;
            this.speed = speed;
            this.reloadTime = reloadTime;
            this.price = price;
            this.ammoType = ammoType;
            this.ammoName = ammoName;
            this.ammoTypeName = ammoTypeName;
            this.ammoPrice = ammoPrice;
        }
    }

    enum ZombieType {
        NORMAL("普通僵尸", 20, 0.2, 5),
        FAST("快速僵尸", 15, 0.3, 7),
        TANK("重装僵尸", 50, 0.15, 15),
        BABY("小僵尸", 10, 0.35, 4),
        HUSK("尸壳", 25, 0.18, 6),
        DROWNED("溺尸", 22, 0.19, 6),
        POISON("剧毒僵尸", 18, 0.18, 8),
        EXPLODER("自爆僵尸", 16, 0.22, 12),
        ARMORED("装甲僵尸", 40, 0.16, 10),
        GIANT("巨型僵尸", 100, 0.1, 30),
        BOSS("僵尸王", 200, 0.1, 100);

        final String name;
        final double health;
        final double speed;
        final int coinReward;

        ZombieType(String name, double health, double speed, int coinReward) {
            this.name = name;
            this.health = health;
            this.speed = speed;
            this.coinReward = coinReward;
        }
    }

    // 枪械配件枚举
    enum GunAttachment {
        EXTENDED_MAG(Material.PAPER, "§6扩容弹夹", "增加50%弹夹容量", 120, 0, 0, 2),
        SUPPRESSOR(Material.IRON_TRAPDOOR, "§7消音器", "降低射击声音", 100, 0, 0.05, 3),
        GRIP(Material.STICK, "§a垂直握把", "减少后坐力", 80, 0.1, 0.05, 4),
        DAMAGE_BOOSTER(Material.NETHER_BRICK, "§4伤害强化器", "提高20%武器伤害", 200, 0.2, 0, 5),
        LASER_SIGHT(Material.LIME_DYE, "§b激光指示器", "提升暴击几率", 180, 0.05, 0.05, 6),
        FAST_RELOAD(Material.FEATHER, "§e快速装弹", "减少50%装弹时间", 220, 0, 0, 7),
        RECOIL_PAD(Material.LEATHER, "§6后座缓冲垫", "大幅减少后坐力", 160, 0, 0.15, 8),
        ARMOR_PIERCER(Material.FLINT, "§8穿甲弹", "对装甲僵尸伤害提升", 250, 0.15, 0, 9),
        SCOPE_2X(Material.SPYGLASS, "§b2倍镜", "大幅提升精准度，潜行左键可放大视野", 300, 0, 0.15, 10);

        final Material material;
        final String displayName;
        final String description;
        final int price;
        final double damageBoost;
        final double speedBoost;
        final int slot; // 配件槽位（同类配件不能叠加）

        GunAttachment(Material material, String displayName, String description, int price,
                double damageBoost, double speedBoost, int slot) {
            this.material = material;
            this.displayName = displayName;
            this.description = description;
            this.price = price;
            this.damageBoost = damageBoost;
            this.speedBoost = speedBoost;
            this.slot = slot;
        }
    }

    // 成就枚举
    enum Achievement {
        ZOMBIE_SLAYER("§6僵尸杀手", "击杀50个僵尸", 100),
        ZOMBIE_EXTERMINATOR("§4僵尸灭绝者", "击杀100个僵尸", 250),
        BOMB_DISPOSAL("§c拆弹专家", "击杀自爆僵尸", 150),
        BOSS_SLAYER("§4屠龙勇士", "击败僵尸王", 300);

        final String displayName;
        final String description;
        final int coinReward;

        Achievement(String displayName, String description, int coinReward) {
            this.displayName = displayName;
            this.description = description;
            this.coinReward = coinReward;
        }
    }

    // 随机事件枚举
    enum RandomEvent {
        NONE("无事件", "没有活跃事件"),
        DOUBLE_COINS("双倍硬币", "所有击杀奖励翻倍"),
        ZOMBIE_RAGE("僵尸狂暴", "僵尸获得速度和力量提升"),
        HEALTH_REGEN("生命恢复", "玩家获得生命恢复效果"),
        BOSS_WAVE("BOSS波次", "强大的僵尸王出现");

        final String displayName;
        final String description;

        RandomEvent(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }

    // 商店页面枚举
    private enum ShopPage {
        MAIN_MENU,
        WEAPONS,
        AMMO,
        BUFFS,
        ARMOR,
        FOOD,
        ATTACHMENTS
    }

    private int getPlayerClipSize(Player player, GunType gunType) {
        int clipSize = gunType.clipSize;
        Set<GunAttachment> attachments = playerAttachments.get(player.getUniqueId());
        if (attachments != null) {
            for (GunAttachment attachment : attachments) {
                if (attachment == GunAttachment.EXTENDED_MAG) {
                    clipSize = (int) Math.round(clipSize * 1.5);
                }
            }
        }
        return clipSize;
    }

    private void openAttachmentManager(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        GunType gunType = materialToGunType.get(mainHand.getType());
        if (gunType == null) {
            player.sendMessage("§c请手持一把枪械后再打开配件管理！");
            return;
        }

        // 创建新的分页GUI
        Inventory gui = Bukkit.createInventory(null, 54, "§8配件管理 - " + gunType.displayName);

        // 背景填充
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, bg);
        }

        // 标题区域
        ItemStack titleItem = new ItemStack(Material.ANVIL);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName("§6§l配件管理系统");
        titleItem.setItemMeta(titleMeta);
        gui.setItem(4, titleItem);

        // 当前武器显示
        ItemStack currentGun = new ItemStack(mainHand);
        ItemMeta gunMeta = currentGun.getItemMeta();
        List<String> gunLore = new ArrayList<>();
        gunLore.add("§7弹药: " + getAmmoDisplay(player));
        gunLore.add("§7无限弹药: " + (isInfiniteAmmoActive(player) ? "§a是" : "§c否"));
        gunMeta.setLore(gunLore);
        currentGun.setItemMeta(gunMeta);
        gui.setItem(0, currentGun);

        // 已安装配件区域
        ItemStack installedTitle = new ItemStack(Material.GREEN_WOOL);
        ItemMeta installedMeta = installedTitle.getItemMeta();
        installedMeta.setDisplayName("§a§l已安装配件");
        installedTitle.setItemMeta(installedMeta);
        gui.setItem(9, installedTitle);

        Set<GunAttachment> installed = playerAttachments.getOrDefault(player.getName(), new HashSet<>());
        int slot = 18;
        for (GunAttachment att : installed) {
            ItemStack item = createAttachmentItem(att);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§c点击卸载该配件");
            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot, item);
            slot++;
        }

        // 可安装配件区域
        ItemStack availableTitle = new ItemStack(Material.BLUE_WOOL);
        ItemMeta availableMeta = availableTitle.getItemMeta();
        availableMeta.setDisplayName("§9§l可安装配件");
        availableTitle.setItemMeta(availableMeta);
        gui.setItem(27, availableTitle);

        slot = 36;
        for (GunAttachment att : GunAttachment.values()) {
            if (installed.contains(att))
                continue;

            ItemStack item = createAttachmentItem(att);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            if (hasAttachmentInInventory(player, att)) {
                lore.add("");
                lore.add("§a点击安装该配件");
            } else {
                lore.add("");
                lore.add("§c背包中没有该配件");
                item.setType(Material.RED_STAINED_GLASS_PANE);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot, item);
            slot++;
        }

        // 返回按钮
        ItemStack backButton = new ItemStack(Material.OAK_DOOR);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c返回商店");
        backButton.setItemMeta(backMeta);
        gui.setItem(49, backButton);

        // 帮助说明
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = help.getItemMeta();
        helpMeta.setDisplayName("§6使用说明");
        List<String> helpLore = new ArrayList<>();
        helpLore.add("§7- 绿色区域: 已安装配件");
        helpLore.add("§7- 蓝色区域: 可安装配件");
        helpLore.add("§7- 红色物品: 不可用配件");
        helpLore.add("§7- 点击配件进行安装/卸载");
        helpMeta.setLore(helpLore);
        help.setItemMeta(helpMeta);
        gui.setItem(45, help);

        player.openInventory(gui);
    }

    private boolean removeAttachmentFromInventory(Player player, GunAttachment att) {
        NamespacedKey key = new NamespacedKey(this, "attachment_id");
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == att.material && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                String id = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

                if (id != null && id.equals(att.name())) {
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                        inventory.setItem(i, item);
                    } else {
                        inventory.setItem(i, null);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAttachmentInInventory(Player player, GunAttachment att) {
        NamespacedKey key = new NamespacedKey(this, "attachment_id");

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == att.material && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                String id = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

                if (id != null && id.equals(att.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    // 检查无限弹药状态
    private boolean isInfiniteAmmoActive(Player player) {
        if (player.hasMetadata("InfiniteAmmo")) {
            long expireTime = player.getMetadata("InfiniteAmmo").get(0).asLong();
            if (System.currentTimeMillis() < expireTime) {
                return true;
            } else {
                // 无限弹药已过期，移除元数据
                player.removeMetadata("InfiniteAmmo", this);

                // 更新显示
                ItemStack gun = player.getInventory().getItemInMainHand();
                if (gun != null) {
                    updateGunLore(player, gun);
                }
                updateScoreboard(player);
                updateAmmoBar(player);
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // 更新弹药显示
        updateAmmoBar(player);
        updateScoreboard(player);
    }

    // 新增方法：获取准确的枪口位置
    private Location getMuzzleLocation(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        return eyeLoc.clone().add(direction.multiply(1.2)); // 枪口在眼前1.2格
    }

    // 后坐力效果
    private void applyRecoil(Player player, GunType gunType, Set<GunAttachment> attachments) {
        // 计算基础后坐力（根据枪械类型）
        double recoil = 0.15;

        // 根据枪械类型调整后坐力
        switch (gunType) {
            case PISTOL:
            case FLAME_PISTOL:
                recoil = 0.12; // 手枪后坐力较小
                break;
            case SHOTGUN:
            case ELECTRIC_SHOTGUN:
                recoil = 0.25; // 霰弹枪后坐力大
                break;
            case SNIPER:
            case ICE_SNIPER:
                recoil = 0.30; // 狙击枪后坐力最大
                break;
            case LMG:
            case MINIGUN:
                recoil = 0.10; // 机枪后坐力较小但连续
                break;
        }

        // 应用配件效果
        if (attachments != null) {
            if (attachments.contains(GunAttachment.RECOIL_PAD)) {
                recoil *= 0.4; // 后座缓冲垫减少后坐力
            }
            if (attachments.contains(GunAttachment.GRIP)) {
                recoil *= 0.7; // 垂直握把减少后坐力
            }
        }

        // 创建后坐力向量
        Vector recoilVec = player.getLocation().getDirection().multiply(-recoil);
        recoilVec.setY(recoil * 0.3); // 添加轻微上扬

        // 应用后坐力
        player.setVelocity(player.getVelocity().add(recoilVec));

        // 添加屏幕抖动效果（模拟后坐力影响）
        Location eyeLoc = player.getEyeLocation();
        Vector shake = new Vector(
                (Math.random() - 0.5) * recoil * 0.2,
                (Math.random() - 0.5) * recoil * 0.2,
                (Math.random() - 0.5) * recoil * 0.2);

        // 直接修改玩家视角（关键修复）
        player.setVelocity(player.getVelocity().add(shake));
        player.setRotation(
                eyeLoc.getYaw() + (float) ((Math.random() - 0.5) * recoil * 5),
                eyeLoc.getPitch() + (float) ((Math.random() - 0.5) * recoil * 3));
    }

    private void updateGameState() {
        // 检查是否所有玩家都死亡
        int alivePlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!deadPlayers.contains(player.getName())) {
                alivePlayers++;
            }
        }

        if (alivePlayers == 0 && gameState == GameState.ACTIVE) {
            gameState = GameState.END;
            Bukkit.broadcastMessage("§c所有玩家都已死亡! 游戏结束! 坚持波数: §4" + wave);
            clearAllZombies();

            // 安全重置游戏
            safeReset();
        }

        // 保存玩家数据
        savePlayerData();
    }

    // 修改 resetGame 方法如下
    private void resetGame() {
        // 清除所有僵尸
        clearAllZombies();

        // 清除所有掉落物和箭
        clearDropsAndArrows();

        // 重置玩家状态
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayer(player);
            player.teleport(getSafeSpawnPoint());
        }

        // 重置游戏状态
        gameState = GameState.WAITING;
        wave = 1;
        playerGuns.clear();
        reloadCooldowns.clear();
        playerAttachments.clear();
        currentEvent = RandomEvent.NONE;

        // 广播消息
        Bukkit.broadcastMessage("§a游戏已重置，等待玩家加入...");

        // 新增：如果有玩家在线，自动开始新一局
        if (Bukkit.getOnlinePlayers().size() > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (gameState == GameState.WAITING && Bukkit.getOnlinePlayers().size() > 0) {
                        startGame();
                    }
                }
            }.runTaskLater(this, 40L); // 延迟2秒，避免冲突
        }
    }

    // 新增方法：安全重置游戏
    private void safeReset() {
        // 检查在线玩家数量
        if (Bukkit.getOnlinePlayers().size() > 0) {
            resetGame();
        } else {
            // 没有玩家在线，只重置状态
            gameState = GameState.WAITING;
            wave = 1;
            zombieTypes.clear();
            playerGuns.clear();
            reloadCooldowns.clear();
            playerAttachments.clear();
            currentEvent = RandomEvent.NONE;
            Bukkit.broadcastMessage("§a游戏已重置，等待玩家加入...");
        }
    }

    // 更新重置倒计时
    private void updateResetCountdown() {
        if (resetCountdown > 0) {
            resetCountdown--;

            if (resetCountdown % 5 == 0 || resetCountdown <= 5) {
                String title = "§c游戏重置中...";
                String subtitle = "§e" + resetCountdown + "秒";

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendTitle(title, subtitle, 0, 40, 10);
                }

                if (resetCountdown <= 0) {
                    resetGame();
                }
            }
        }
    }
}
