#Zombie Survival Plugin - 僵尸生存插件
A highly customizable Minecraft zombie survival minigame with weapon customization, economy, and random events
高度可定制的 Minecraft 僵尸生存小游戏，包含武器定制、经济系统和随机事件

🧩 Core Features / 核心功能
1. Game State System / 游戏状态系统
Waiting Phase - Players prepare, set spawn/shop locations
等待阶段 - 玩家准备，设置出生点/商店位置

Active Phase - Zombie waves spawn, players survive
战斗阶段 - 僵尸波次生成，玩家生存

End Phase - All players dead or manual end
结束阶段 - 所有玩家死亡或手动结束

Auto Start - Game starts automatically with 1+ players
自动开始 - 1名以上玩家在线自动开始游戏

Wave System - Zombies strengthen every 60s (Count = 5 + Wave × Players)
波次系统 - 每60秒增强一波僵尸（数量=5+波次×玩家数）

2. Zombie System / 僵尸系统
11 Unique Zombie Types / 11种独特僵尸类型:

NORMAL(普通), FAST(快速), TANK(重装), BABY(小僵尸), 
HUSK(尸壳), DROWNED(溺尸), POISON(剧毒), EXPLODER(自爆),
ARMORED(装甲), GIANT(巨型), BOSS(僵尸王)
Special Abilities / 特殊能力:

Exploders: 80% chance of AoE explosion
自爆僵尸：80%几率范围爆炸

Baby Zombies: Steal player food
小僵尸：偷取玩家食物

Armored Zombies: Damage player armor
装甲僵尸：破坏玩家护甲

BOSS: 200 HP + Diamond Armor
BOSS：200生命值+钻石护甲

3. Weapon System / 武器系统
20+ Weapon Types / 20+种武器类型:

PISTOL(手枪), SHOTGUN(霰弹枪), SNIPER(狙击枪), 
MINIGUN(加特林), FLAMETHROWER(火焰喷射器), 
ICE_SNIPER(冰冻狙击枪), ELECTRIC_SHOTGUN(电击霰弹枪)...
Weapon Attributes / 武器属性:

Damage, Clip Size, Reload Time, Ammo Type
伤害值、弹容量、装弹时间、弹药类型

Special Effects (Freeze/Ignite/Electrocute)
特殊效果（冰冻/点燃/电击）

Ammo Management / 弹药管理:
Real-time ammo XP bar display
实时显示弹药经验条

4. Attachment System / 配件系统
9 Attachments / 9种可安装配件:

SCOPE_2X(2倍镜), EXTENDED_MAG(扩容弹夹), 
LASER_SIGHT(激光指示器), ARMOR_PIERCER(穿甲弹)...
Effect Stacking / 效果叠加:

Extended Mag: +50% clip size
扩容弹夹：+50%弹容量

2x Scope: Sneak+Left Click to zoom
2倍镜：潜行+左键放大视野

Suppressor reduces gunshot sound
消音器降低枪声

Slot Limits / 槽位限制:
Same-type attachments can't stack (e.g. two scopes)
同类配件不可叠加（如2个瞄准镜）

5. Economy & Shop / 经济与商店
Shop Items / 商品示例:

Weapons: Diamond Sword $150
武器：钻石剑 $150

Infinite Ammo (60s): $400
无限弹药(60秒)：$400

Iron Armor Set: $450
铁护甲套装：$450

Golden Apples (3): $50
金苹果(3个)：$50

6. Random Events / 随机事件系统
Double Coins: 2× kill rewards
双倍硬币：击杀奖励×2

Zombie Rage: Zombies gain speed/damage
僵尸狂暴：僵尸加速+增伤

Boss Wave: Spawns 200HP Zombie King
BOSS波次：生成200HP僵尸王

Health Regen: All players regenerate health
生命恢复：全体玩家回复生命

Cooldown: 10 minutes
事件冷却：10分钟

7. Achievement System / 成就系统
Achievement Examples / 成就示例:

Zombie Slayer: Kill 50 zombies +$100
僵尸杀手：击杀50僵尸 +$100

Bomb Disposal: Kill Exploder Zombie +$150
拆弹专家：击杀自爆僵尸 +$150

Dragon Slayer: Defeat Zombie King +$300
屠龙勇士：击败僵尸王 +$300

Reward Effects / 特效奖励:
Sound + Title display on unlock
解锁时播放音效+全屏标题

⚙️ Technical Highlights / 技术亮点
Persistent Storage / 持久化存储:

YAML player data (coins/kills)
YAML存储玩家数据（硬币/击杀数）

Weapon UUID binding prevents duplication
武器UUID绑定防止复制

Real-time Particle System / 实时粒子系统:

Bullet trajectory rendering
弹道轨迹渲染

Dynamic zombie health bars
僵尸血条动态显示

Physics Simulation / 物理模拟:

Weapon recoil affects crosshair
武器后坐力影响准星

Explosion shockwave physics
爆炸冲击波物理计算

Performance Optimization / 性能优化:

Asynchronous data saving
异步数据保存

Entity cleanup scheduler
实体清理定时任务

Concurrent collections prevent thread blocking
并发集合防线程阻塞

📜 Command List / 指令列表
Command / 命令 | Permission / 权限 | Description / 描述
/setspawn | zombie.admin | Set player spawn point / 设置玩家出生点
/setshop | zombie.admin | Set shop location / 设置商店位置
/shop | (All players) | Open shop / 打开商店
/start | zombie.admin | Force start game / 强制开始游戏
/achievements | (All players) | View achievements / 查看成就
/attachments | (All players) | Attachment manager / 配件管理器
/zslocations | zombie.admin | View location info / 查看位置信息
🔧 Installation & Configuration / 安装与配置
Place plugin in plugins/ folder
将插件放入 plugins/ 文件夹

Restart server to generate config files
重启服务器生成配置文件

Customization:
自定义：

Modify tab.yml for TAB header/footer
修改 tab.yml 调整Tab列表头/尾

Edit scoreboard.yml for custom scoreboard
编辑 scoreboard.yml 自定义计分板

🎯 Design Philosophy / 设计理念
"Survival is not the end, but the beginning of evolution"
"生存不是终点，而是进化的开始"
Encourages players through:
通过以下方式鼓励玩家：

Team collaboration against zombie hordes
团队协作对抗僵尸潮

Risk decisions in economy system
经济系统风险决策

Create unique combat styles with attachments
配件组合创造独特战斗风格

Long-term goals via achievements
成就系统提供长期目标

Compatible Versions: Spigot 1.17+
适用版本: Spigot 1.17+
License: GNU GPL v3
开源协议: GNU GPL v3
