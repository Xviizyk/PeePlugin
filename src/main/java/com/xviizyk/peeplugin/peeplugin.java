package com.xviizyk.peeplugin;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class peeplugin extends JavaPlugin implements CommandExecutor {

    private final Map<UUID, PeeTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUsed = new ConcurrentHashMap<>();
    private Particle.DustOptions peeDust = new Particle.DustOptions(Color.YELLOW, 1.0f);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getCommand("pee") != null) getCommand("pee").setExecutor(this);
        if (getCommand("stoppee") != null) getCommand("stoppee").setExecutor(this);
        int r = getConfig().getInt("settings.color.r", 255);
        int g = getConfig().getInt("settings.color.g", 229);
        int b = getConfig().getInt("settings.color.b", 0);
        float size = (float) getConfig().getDouble("settings.size", 1.0);
        try {
            peeDust = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
        } catch (Throwable t) {
            peeDust = new Particle.DustOptions(Color.YELLOW, size);
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("stoppee")) {
            PeeTask task = activeTasks.remove(player.getUniqueId());
            if (task != null) {
                task.cancel();
                player.sendMessage("§aPee stopped.");
            } else {
                player.sendMessage("§eNo active pee to stop.");
            }
            return true;
        }
        int duration = getConfig().getInt("settings.duration_seconds", 60);
        double speed = getConfig().getDouble("settings.speed", 0.5);
        boolean playSound = getConfig().getBoolean("settings.sound", true);
        int cooldownSec = getConfig().getInt("settings.cooldown_seconds", 5);
        double movementCancelThreshold = getConfig().getDouble("settings.move_cancel_distance", 0.6);
        long now = System.currentTimeMillis();
        Long last = lastUsed.get(player.getUniqueId());
        if (last != null && now - last < cooldownSec * 1000L) {
            long remain = (cooldownSec * 1000L - (now - last) + 999) / 1000;
            player.sendMessage("§cPlease wait " + remain + "s before peeing again.");
            return true;
        }
        if (activeTasks.containsKey(player.getUniqueId())) {
            player.sendMessage("§eYou are already peeing. Use /stoppee to cancel.");
            return true;
        }
        String msg = getConfig().getString("messages.start", "§eYou start peeing...");
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg.replace("&", "§"));
        PeeTask task = new PeeTask(player, duration, speed, playSound, movementCancelThreshold);
        activeTasks.put(player.getUniqueId(), task);
        lastUsed.put(player.getUniqueId(), now);
        task.runTaskTimer(this, 0L, 1L);
        return true;
    }

    private class PeeTask extends BukkitRunnable {
        private final Player player;
        private final int durationTicks;
        private final double baseSpeed;
        private final boolean playSound;
        private final double moveCancelDistSq;

        private int ticks = 0;
        private final Location startLocation;

        public PeeTask(Player player, int durationSeconds, double speed, boolean playSound, double moveCancelDistance) {
            this.player = player;
            this.durationTicks = Math.max(1, durationSeconds) * 20;
            this.baseSpeed = speed;
            this.playSound = playSound;
            this.moveCancelDistSq = moveCancelDistance * moveCancelDistance;
            this.startLocation = player.getLocation().clone();
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.getWorld() == null) {
                cleanup();
                return;
            }
            if (player.getLocation().distanceSquared(startLocation) > moveCancelDistSq) {
                player.sendMessage("§ePee cancelled because you moved.");
                cleanup();
                return;
            }
            if (ticks++ >= durationTicks) {
                cleanup();
                return;
            }
            if (playSound && ticks % 5 == 0) {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WATER_AMBIENT, 0.4f, 1.6f);
            }
            Location start = player.getLocation().add(0, 0.8, 0);
            Vector dir = start.getDirection().clone().normalize().multiply(baseSpeed);
            double gravity = getConfig().getDouble("settings.gravity", 0.03);
            double drag = getConfig().getDouble("settings.drag", 0.02);
            int stepsPerTick = Math.min(10, Math.max(4, getConfig().getInt("settings.steps_per_tick", 8)));
            Location probe = start.clone();
            Vector vel = dir.clone();
            for (int s = 0; s < stepsPerTick; s++) {
                probe.add(vel);
                vel.setY(vel.getY() - gravity);
                vel.multiply(1.0 - drag);
                player.getWorld().spawnParticle(Particle.DUST, probe, 1, peeDust);
                if (probe.getBlock().getType().isSolid()) {
                    player.getWorld().spawnParticle(Particle.SPLASH, probe, 4);
                    break;
                }
                if (probe.getY() < 0) break;
            }
        }

        private void cleanup() {
            this.cancel();
            activeTasks.remove(player.getUniqueId());
        }
    }
}
