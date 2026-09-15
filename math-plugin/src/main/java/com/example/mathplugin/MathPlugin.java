package com.example.mathplugin;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class MathPlugin extends PluginBase implements Listener {
    private int currentAnswer = Integer.MIN_VALUE;
    private boolean taskActive;
    private Config leaderboardConfig;
    private final Map<String, Integer> leaderboard = new HashMap<>();

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        leaderboardConfig = new Config(getDataFolder() + "/leaderboard.yml", Config.YAML);
        for (String key : leaderboardConfig.getKeys(false)) {
            leaderboard.put(key, leaderboardConfig.getInt(key));
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(TextFormat.GREEN + "Математичний плагін успішно активовано!");
    }

    @Override
    public void onDisable() {
        saveLeaderboardData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("math")) {
            return false;
        }
        if (args.length == 0) {
            sender.sendMessage(TextFormat.YELLOW + "=== Математичний плагін ===");
            sender.sendMessage(TextFormat.GOLD + "/math start " + TextFormat.WHITE + "- нове завдання");
            sender.sendMessage(TextFormat.GOLD + "/math top " + TextFormat.WHITE + "- таблиця лідерів");
            sender.sendMessage(TextFormat.GOLD + "/math answer <число> " + TextFormat.WHITE + "- відповідь");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start", "генерувати" -> {
                generateNewTask();
                sender.sendMessage(TextFormat.GREEN + "Нове завдання згенеровано!");
            }
            case "top", "topboard", "лідерборд" -> showLeaderboard(sender);
            case "answer", "відповідь" -> {
                if (args.length < 2) {
                    sender.sendMessage(TextFormat.RED + "Використання: /math answer <число>");
                    return true;
                }
                try {
                    checkAnswer(sender, Integer.parseInt(args[1]));
                } catch (NumberFormatException exception) {
                    sender.sendMessage(TextFormat.RED + "Вкажіть ціле число!");
                }
            }
            default -> sender.sendMessage(TextFormat.RED + "Невідома команда. Напишіть /math.");
        }
        return true;
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        if (!taskActive) return;
        try {
            checkAnswer(event.getPlayer(), Integer.parseInt(event.getMessage().trim()));
        } catch (NumberFormatException ignored) {
        }
    }

    private void generateNewTask() {
        Random random = new Random();
        int first = random.nextInt(50) + 1;
        int second = random.nextInt(50) + 1;
        int operation = random.nextInt(3);
        String operator;
        if (operation == 0) {
            operator = "+";
            currentAnswer = first + second;
        } else if (operation == 1) {
            operator = "-";
            currentAnswer = first - second;
        } else {
            first = random.nextInt(12) + 1;
            second = random.nextInt(12) + 1;
            operator = "*";
            currentAnswer = first * second;
        }
        taskActive = true;
        getServer().broadcastMessage(TextFormat.LIGHT_PURPLE + "[Математика] " + TextFormat.YELLOW
                + "Розв'яжіть: " + TextFormat.AQUA + first + " " + operator + " " + second + " = ?");
    }

    private synchronized void checkAnswer(CommandSender sender, int answer) {
        if (!taskActive) {
            sender.sendMessage(TextFormat.RED + "Наразі немає активного завдання!");
            return;
        }
        if (answer != currentAnswer) {
            sender.sendMessage(TextFormat.RED + "Неправильна відповідь, спробуйте ще раз!");
            return;
        }
        taskActive = false;
        String winner = sender.getName();
        leaderboard.put(winner, leaderboard.getOrDefault(winner, 0) + 1);
        saveLeaderboardData();
        getServer().broadcastMessage(TextFormat.GREEN + "[Математика] " + TextFormat.GOLD + winner
                + TextFormat.GREEN + " правильно відповів(ла)! Відповідь: " + TextFormat.WHITE + currentAnswer);
    }

    private void showLeaderboard(CommandSender sender) {
        if (leaderboard.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "Таблиця лідерів поки порожня.");
            return;
        }
        sender.sendMessage(TextFormat.GOLD + "=== ТОП-10 математиків ===");
        List<Map.Entry<String, Integer>> entries = leaderboard.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(10).collect(Collectors.toList());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, Integer> entry = entries.get(index);
            sender.sendMessage(TextFormat.GREEN + String.valueOf(index + 1) + ". " + TextFormat.WHITE + entry.getKey()
                    + " - " + TextFormat.YELLOW + entry.getValue() + " завдань");
        }
    }

    private void saveLeaderboardData() {
        if (leaderboardConfig == null) return;
        for (Map.Entry<String, Integer> entry : leaderboard.entrySet()) {
            leaderboardConfig.set(entry.getKey(), entry.getValue());
        }
        leaderboardConfig.save();
    }
}